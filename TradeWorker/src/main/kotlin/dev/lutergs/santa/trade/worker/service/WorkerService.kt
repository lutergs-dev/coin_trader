package dev.lutergs.santa.trade.worker.service

import dev.lutergs.santa.trade.worker.domain.entity.MainTrade
import dev.lutergs.santa.trade.worker.domain.UpbitClient
import dev.lutergs.santa.trade.worker.domain.LogRepository
import dev.lutergs.santa.trade.worker.domain.MessageSender
import dev.lutergs.santa.trade.worker.domain.entity.*
import dev.lutergs.santa.trade.worker.domain.toStrWithPoint
import dev.lutergs.santa.trade.worker.infra.LoggerCreate
import dev.lutergs.upbitclient.api.exchange.order.OrderRequest
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.api.exchange.order.PlaceOrderRequest
import dev.lutergs.upbitclient.dto.Markets
import dev.lutergs.upbitclient.dto.OrderSide
import dev.lutergs.upbitclient.dto.OrderType
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.util.retry.Retry
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeoutException

@Service
class WorkerService(
  private val phase: Phase,
  private val repository: LogRepository,
  private val alarmSender: MessageSender,
  private val mainTrade: MainTrade,
  private val client: UpbitClient,
  private val applicationContext: ApplicationContext,
  @Value("\${custom.trade.watch.interval}") private val watchIntervalSecond: Int,
) {
  private val originLogger = LoggerCreate.createLogger(this::class)
  private val p1Logger = LoggerCreate.createLogger(this::class, "Phase1")
  private val p2Logger = LoggerCreate.createLogger(this::class, "Phase2")

  @PostConstruct
  fun run() {
    this.buyCoin()
      .flatMap { buyOrder -> this.phase1(buyOrder, p1Logger) }
      .flatMap {
        if (it.sell != null && it.sellType != null && it.sellType == SellType.LOSS) {
          this.alarmSender.sendAlarm(AlarmMessage.fromOrderResponse(it.sell.order))
            .doOnNext { this.p1Logger.info("손실 매도한 코인 (${this.mainTrade.market.quote}) 에 대한 정보를 controller 로 전송했습니다.") }
            .thenReturn(it)
        } else {
          Mono.just(it)
        }
      }.flatMap {
        if (it.sell == null) { this.phase2(it, p2Logger) }
        else { Mono.just(it) }
      }.flatMap { this.alarmSender.sendTradeResult(TradeResult.fromTradeStatus(it)) }
      .subscribe { this.closeApplication() }
  }

  fun buyCoin(): Mono<TradeStatus> {
    return this.client.orderBook.getOrderBook(Markets.fromMarket(this.mainTrade.market))
      .next()
      .flatMap { orderBookResponse ->
        // get the lowest sell price and buy
        val lowestPrice = orderBookResponse.orderbookUnits[0].askPrice
        val calculatedVolume = (this.mainTrade.money.toDouble() / lowestPrice)//(lowestPrice /  this.mainTrade.money.toDouble())
        PlaceOrderRequest(this.mainTrade.market, OrderType.LIMIT, OrderSide.BID, calculatedVolume, lowestPrice)
          .also { this.originLogger.info("코인 구매를 시작합니다. 대상 코인 : ${it.market}, 가격 : $lowestPrice, 구매량 : $calculatedVolume, 총금액: ${this.mainTrade.money.toDouble()}") }
          .let { this.client.placeBuyOrderAndWait(it) }
          .doOnNext { this.originLogger.info("코인 구매가 완료되었습니다. 구매 UUID : ${it.uuid}") }
          .flatMap { Mono.just(TradeStatus(buy = Order(it, OrderStatus.COMPLETE), sell = null, sellType = null)) }
      }
  }

  /**
   * 최초 2시간 반 동안은, 코인을 구매하고 1.5%의 이득점, 3%의 손실점을 설정한 후 해당 지점에 도달했을 때만 판매한다.
   * */
  fun phase1(tradeStatus: TradeStatus, logger: Logger): Mono<TradeStatus> {
    return this.client.orderBook.getOrderBook(Markets.fromMarket(tradeStatus.buy.order.market))
      .next()
      .flatMap {orderbook ->
        val profitPrice = orderbook.orderbookUnits
          .filter { it.askPrice < this.phase.phase1.getProfitPrice(tradeStatus.buy.order.price) }
          .maxOf { it.askPrice }
        val lossPrice = orderbook.orderbookUnits
          .filter { it.bidPrice > this.phase.phase1.getLossPrice(tradeStatus.buy.order.price) }
          .minOf { it.bidPrice }
        PlaceOrderRequest(tradeStatus.buy.order.market, OrderType.LIMIT, OrderSide.ASK, tradeStatus.buy.order.volume, profitPrice)
          .also { logger.info("코인의 이득점을 설정했습니다. 가격 : ${it.price}, 총금액 : ${it.volume!! * it.price!!}. 앞으로 2시간 반동안, 코인의 가격이 $profitPrice (이득점) 혹은 $lossPrice (손실점) 에 도달하면 판매합니다.") }
          .let { request ->
            this.client.order.placeOrder(request)
              .doOnNext { logger.info("코인의 이득 판매 주문을 올렸습니다.") }
              .flatMap { this.repository.newSellOrder(it.toOrderResponse(), tradeStatus.buy.order.uuid).thenReturn(it) }
              .flatMap { profitOrder ->
                // 1초에 한 번씩 가격과 오더 상태를 같이 확인함
                Mono.zip(
                  Mono.defer { this.client.order.getOrder(OrderRequest(profitOrder.uuid)) },
                  Mono.defer { this.client.orderBook.getOrderBook(Markets.fromMarket(profitOrder.market)).next() }
                ).flatMap { p ->
                  val firstSellOrder = p.t1
                  val currentPrice = p.t2.orderbookUnits.first().bidPrice
                  // log
                  this.logCurrentStatus(tradeStatus, currentPrice, profitPrice, lossPrice, logger)

                  if (firstSellOrder.state == "done") {
                    logger.info("코인을 이득을 보고 매매했습니다.")
                    this.repository.finishSellOrder(tradeStatus.buy.order, firstSellOrder, SellType.PROFIT)
                      .thenReturn(tradeStatus.sellFinished(firstSellOrder, SellType.PROFIT))
                  } else if (currentPrice <= lossPrice) {
                    logger.info("코인이 ${this.phase.phase1.lossPercent.toStrWithPoint()}% 이상 손해를 보고 있습니다. 현재 가격으로 매매합니다.")
                    this.cancelSellOrderAndSellByCurrentPrice(firstSellOrder, tradeStatus.buy.order, SellType.LOSS, logger)
                      .flatMap { Mono.fromCallable { tradeStatus.sellFinished(it, SellType.LOSS) } }
                  } else {
                    Mono.empty()
                  }
                }.repeatWhenEmpty(((this.phase.phase1.waitMinute * 60 / this.watchIntervalSecond) + 1).toInt()) {
                  it.delayElements(Duration.ofSeconds(this.watchIntervalSecond.toLong()))
                }.switchIfEmpty {
                  this.client.order.cancelOrder(OrderRequest(profitOrder.uuid))
                    .flatMap { this.repository.cancelSellOrder(profitOrder.uuid, tradeStatus.buy.order.uuid) }
                    .thenReturn(tradeStatus)
                }
              }
          }
      }
  }

  /**
   * 한시간 반 동안, 구매 가격보다 높으면 무조건 매매
   * */
  fun phase2(tradeStatus: TradeStatus, logger: Logger): Mono<TradeStatus> {
    return Mono.defer { this.client.orderBook.getOrderBook(Markets.fromMarket(tradeStatus.buy.order.market)).next() }
      .flatMap { orderbook ->
        val currentPrice = orderbook.orderbookUnits.first().bidPrice

        this.logCurrentStatus(tradeStatus, currentPrice, logger = logger)

        if (currentPrice > tradeStatus.buy.order.price) {
          PlaceOrderRequest(tradeStatus.buy.order.market, OrderType.LIMIT, OrderSide.ASK, tradeStatus.buy.order.volume, currentPrice)
            .let { this.client.placeSellOrderAndWait(it, tradeStatus.buy.order, SellType.STOP_PROFIT) }
            .flatMap { Mono.fromCallable { tradeStatus.sellFinished(it, SellType.STOP_PROFIT) } }
        } else if (currentPrice <= this.phase.phase2.getLossPrice(tradeStatus.buy.order.price)) {
          logger.info("코인이 ${this.phase.phase2.lossPercent.toStrWithPoint()}% 이상 손해를 보고 있습니다. 현재 가격으로 매매합니다.")
          PlaceOrderRequest(tradeStatus.buy.order.market, OrderType.LIMIT, OrderSide.ASK, tradeStatus.buy.order.volume, currentPrice)
            .let { this.client.placeSellOrderAndWait(it, tradeStatus.buy.order, SellType.STOP_LOSS) }
            .flatMap { Mono.fromCallable { tradeStatus.sellFinished(it, SellType.STOP_LOSS) } }
        } else {
          Mono.empty()
        }
      }.repeatWhenEmpty(((this.phase.phase2.waitMinute * 60 / this.watchIntervalSecond) + 1).toInt()) {
        it.delayElements(Duration.ofSeconds(this.watchIntervalSecond.toLong()))
      }.switchIfEmpty {
        logger.info("${(this.phase.totalWaitMinute().toDouble() / 60.0).toStrWithPoint(1)} 시간동안 기다렸지만 매매가 되지 않아, 현재 가격으로 판매합니다.")
        this.client.orderBook.getOrderBook(Markets.fromMarket(tradeStatus.buy.order.market)).next()
          .flatMap { orderbook ->
            PlaceOrderRequest(tradeStatus.buy.order.market, OrderType.LIMIT, OrderSide.ASK, tradeStatus.buy.order.volume, orderbook.orderbookUnits.first().bidPrice)
              .let { this.client.placeSellOrderAndWait(it, tradeStatus.buy.order, SellType.TIMEOUT) }
              .flatMap { Mono.fromCallable { tradeStatus.sellFinished(it, SellType.TIMEOUT) } }
          }
      }
  }



  // TODO: Phase 에 따라 함수 최적화 필요
  private fun cancelSellOrderAndSellByCurrentPrice(firstSellOrder: OrderResponse, buyOrder: OrderResponse, sellType: SellType, logger: Logger): Mono<OrderResponse> {
    return this.client.order.cancelOrder(OrderRequest(firstSellOrder.uuid))
      .flatMap { this.repository.cancelSellOrder(firstSellOrder.uuid, buyOrder.uuid).thenReturn(it) }
      .flatMap { this.client.orderBook.getOrderBook(Markets.fromMarket(it.market)).next() }
      .flatMap { res ->
        logger.info("코인 매도 주문을 취소했습니다. 현재 가격인 ${res.orderbookUnits.first().bidPrice} 에 매도합니다.")
        PlaceOrderRequest(
          market = res.market,
          type = OrderType.LIMIT,
          side = OrderSide.ASK,
          volume = firstSellOrder.volume,
          price = res.orderbookUnits.first().bidPrice
        ).let { this.client.placeSellOrderAndWait(it, buyOrder, sellType) }
      }.doOnNext { logger.info("코인 현재가 매도가 완료되었습니다.") }
  }


  // TODO: Phase 에 따라 함수 최적화 필요
  private fun logCurrentStatus(tradeStatus: TradeStatus, currentPrice: Double, profitPrice: Double? = null, lossPrice: Double? = null, logger: Logger) {
    LocalDateTime.now()
      .takeIf { it.second < this.watchIntervalSecond }
      ?.let {
        Duration.between(tradeStatus.buy.order.createdAt.toLocalDateTime(), it)
          .let { d ->
            val hours = d.toHours()
            val minutes = d.toMinutes() % 60
            val secs = d.seconds % 60

            if (profitPrice != null && lossPrice != null) {
              logger.info("이득가: ${profitPrice.toStrWithPoint()}, 손실가: ${lossPrice.toStrWithPoint()}, 현재가: ${currentPrice.toStrWithPoint()}, 구매가: ${tradeStatus.buy.order.price.toStrWithPoint()} 경과시간: $hours 시간 $minutes 분 $secs 초")
            } else {
              logger.info("현재가: ${currentPrice.toStrWithPoint()}, 구매가: ${tradeStatus.buy.order.price.toStrWithPoint()}, 경과시간: $hours 시간 $minutes 분 $secs 초\"")
            }
          }
      }
  }

  private fun closeApplication() {
    SpringApplication.exit(this.applicationContext)
  }
}