package dev.lutergs.santa.trade.worker.service

import dev.lutergs.santa.trade.worker.domain.entity.MainTrade
import dev.lutergs.santa.trade.worker.domain.UpbitClient
import dev.lutergs.santa.trade.worker.domain.LogRepository
import dev.lutergs.santa.trade.worker.domain.MessageSender
import dev.lutergs.santa.trade.worker.domain.entity.*
import dev.lutergs.santa.trade.worker.domain.toStrWithPoint
import dev.lutergs.santa.trade.worker.infra.LoggerCreate
import dev.lutergs.upbitclient.api.exchange.order.OrderRequest
import dev.lutergs.upbitclient.api.exchange.order.PlaceOrderRequest
import dev.lutergs.upbitclient.dto.Markets
import dev.lutergs.upbitclient.dto.OrderSide
import dev.lutergs.upbitclient.dto.OrderType
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.lang.IllegalStateException
import java.time.Duration
import java.time.LocalDateTime

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
      .flatMap { buyOrder -> this.phase1(buyOrder) }
      .flatMap {
        if (it.sellResult == SellType.LOSS) {
          this.alarmSender.sendAlarm(DangerCoinMessage.fromOrderResponse(it.sell!!))
            .doOnNext { this.p1Logger.info("손실 매도한 코인 (${this.mainTrade.market.quote}) 에 대한 정보를 controller 로 전송했습니다.") }
            .thenReturn(it)
        } else {
          Mono.just(it)
        }
      }.flatMap {
        if (it.sell == null) { this.phase2(it) }
        else { Mono.just(it) }
      }.flatMap { this.alarmSender.sendTradeResult(it.toMsg()) }
      .subscribe { this.closeApplication() }
  }

  fun buyCoin(): Mono<TradeResult> {
    return PlaceOrderRequest(this.mainTrade.market, OrderType.PRICE, OrderSide.BID, price = this.mainTrade.money.toDouble())
      .let { this.client.placeBuyOrderAndWait(it) }
      .flatMap {
        this.originLogger.info("코인 구매가 완료되었습니다. 대상 코인 : ${it.market}, 가격: ${it.avgPrice()}, 구매량: ${it.totalVolume().toStrWithPoint(3)}, 총금액: ${this.mainTrade.money.toDouble()}")
        this.originLogger.info("구매 주문 UUID: ${it.uuid}")
        Mono.fromCallable { TradeResult(buy = it, sell = null) }
      }
  }

  /**
   * 최초 정해진 시각 동안은, 코인을 구매하고 1차 이득점, 손실점을 설정한 후 해당 지점에 도달했을 때만 판매한다.
   * */
  fun phase1(tradeResult: TradeResult, logger: Logger = this.p1Logger): Mono<TradeResult> {
    return this.client.orderBook.getOrderBook(Markets.fromMarket(tradeResult.buy.market))
      .next()
      .flatMap { orderbook ->
        val profitPrice = this.phase.phase1.getProfitPrice(tradeResult.buy.avgPrice()).let { orderbook.nearestStepPrice(it) }
        val lossPrice = this.phase.phase1.getLossPrice(tradeResult.buy.avgPrice())
        PlaceOrderRequest(tradeResult.buy.market, OrderType.LIMIT, OrderSide.ASK, tradeResult.buy.totalVolume(), profitPrice)
          .also { logger.info("코인의 이득점을 설정했습니다. 가격 : ${it.price?.toStrWithPoint()}, 총금액 : ${(it.volume!! * it.price!!).toStrWithPoint()}. 앞으로 2시간 반동안, 코인의 가격이 ${profitPrice.toStrWithPoint()} (이득주문점) 혹은 ${lossPrice.toStrWithPoint()} (손실점) 에 도달하면 판매합니다.") }
          .let { request ->
            this.client.order.placeOrder(request)
              .doOnNext { logger.info("코인의 이득 판매 주문을 올렸습니다.") }
              .flatMap { this.client.order.getOrder(OrderRequest(it.uuid)) }
              .flatMap { this.repository.placeSellOrder(it, tradeResult.buy.uuid).thenReturn(it) }
              .flatMap { profitOrder ->
                // 1초에 한 번씩 가격과 오더 상태를 같이 확인함
                Mono.zip(
                  Mono.defer { this.client.order.getOrder(OrderRequest(profitOrder.uuid)) },
                  Mono.defer { this.client.ticker.getTicker(Markets.fromMarket(profitOrder.market)).next() }
                ).flatMap { p ->
                  val (firstSellOrder, currentPrice) = p.t1 to p.t2.tradePrice

                  this.logCurrentStatus(tradeResult, currentPrice, profitPrice, lossPrice, logger)

                  if (firstSellOrder.isFinished()) {
                    logger.info("코인을 이득을 보고 매매했습니다.")
                    this.repository.finishSellOrder(tradeResult.buy, firstSellOrder, SellType.PROFIT)
                      .thenReturn(tradeResult.sellFinished(firstSellOrder, SellType.PROFIT))
                  } else if (currentPrice <= lossPrice) {
                    logger.info("코인이 ${this.phase.phase1.lossPercent.toStrWithPoint()}% 이상 손해를 보고 있습니다. 현재 가격으로 매도합니다.")
                    this.client.order.cancelOrder(OrderRequest(firstSellOrder.uuid))
                      .flatMap { this.repository.cancelSellOrder(firstSellOrder.uuid, tradeResult.buy.uuid).thenReturn(it) }
                      .doOnNext { logger.info("코인 매도 주문을 취소했습니다. 현재 가격으로 매도합니다.") }
                      .flatMap { PlaceOrderRequest(it.market, OrderType.MARKET, OrderSide.ASK, volume = firstSellOrder.totalVolume())
                        .let {p -> this.client.placeSellOrderAndWait(p, tradeResult.buy, SellType.LOSS) }
                      }.doOnNext { logger.info("코인 현재가 매도가 완료되었습니다.") }
                      .flatMap { Mono.fromCallable { tradeResult.sellFinished(it, SellType.LOSS) } }
                  } else {
                    Mono.empty()
                  }
                }.repeatWhenEmpty((this.phase.phase1.waitMinute * 60 / this.watchIntervalSecond).toInt()) {
                  it.delayElements(Duration.ofSeconds(this.watchIntervalSecond.toLong()))
                }.onErrorResume(IllegalStateException::class.java) {
                  logger.info("코인이 판매점에 도달하지 못했습니다. 현재 매도 주문을 취소하고, phase 2 로 진입합니다.")
                  this.client.order.cancelOrder(OrderRequest(profitOrder.uuid))
                    .flatMap { this.repository.cancelSellOrder(profitOrder.uuid, tradeResult.buy.uuid) }
                    .thenReturn(tradeResult)
                    .doOnNext { logger.info("코인의 매도 주문을 취소했습니다.") }
                }
              }
          }
      }
  }

  /**
   * 한시간 반 동안, 구매 가격보다 높으면 무조건 매매
   * */
  fun phase2(tradeResult: TradeResult, logger: Logger = this.p2Logger): Mono<TradeResult> {
    logger.info("지금부터 코인의 평균 구매단가보다 현재 가격이 높거나, 지정한 손실점 이하로 가격이 떨어지면 매도합니다.")
    return Mono.defer { this.client.ticker.getTicker(Markets.fromMarket(tradeResult.buy.market)).next() }
      .flatMap { ticker ->
        val currentPrice = ticker.tradePrice
        this.logCurrentStatus(tradeResult, currentPrice, logger = logger)

        if (currentPrice > tradeResult.buy.avgPrice()) {
          logger.info("코인이 평균 구매단가보다 높습니다. 현재 가격으로 매도합니다.")
          PlaceOrderRequest(tradeResult.buy.market, OrderType.MARKET, OrderSide.ASK, volume = tradeResult.buy.totalVolume())
            .let { this.client.placeSellOrderAndWait(it, tradeResult.buy, SellType.STOP_PROFIT) }
            .flatMap { Mono.fromCallable { tradeResult.sellFinished(it, SellType.STOP_PROFIT) } }
            .doOnNext { logger.info("이익 매도가 완료되었습니다.") }
        } else if (currentPrice <= this.phase.phase2.getLossPrice(tradeResult.buy.avgPrice())) {
          logger.info("코인이 ${this.phase.phase2.lossPercent.toStrWithPoint()}% 이상 손해를 보고 있습니다. 현재 가격으로 매도합니다.")
          PlaceOrderRequest(tradeResult.buy.market, OrderType.MARKET, OrderSide.ASK, volume = tradeResult.buy.totalVolume())
            .let { this.client.placeSellOrderAndWait(it, tradeResult.buy, SellType.STOP_LOSS) }
            .flatMap { Mono.fromCallable { tradeResult.sellFinished(it, SellType.STOP_LOSS) } }
            .doOnNext { logger.info("손실 매도가 완료되었습니다.") }
        } else {
          Mono.empty()
        }
      }.repeatWhenEmpty((this.phase.phase2.waitMinute * 60 / this.watchIntervalSecond).toInt()) {
        it.delayElements(Duration.ofSeconds(this.watchIntervalSecond.toLong()))
      }.onErrorResume(IllegalStateException::class.java) {
        logger.info("${(this.phase.totalWaitMinute().toDouble() / 60.0).toStrWithPoint(1)} 시간동안 기다렸지만 매매가 되지 않아, 현재 가격으로 매도합니다.")
        this.client.orderBook.getOrderBook(Markets.fromMarket(tradeResult.buy.market)).next()
          .flatMap { orderbook ->
            PlaceOrderRequest(tradeResult.buy.market, OrderType.LIMIT, OrderSide.ASK, tradeResult.buy.totalVolume(), orderbook.orderbookUnits.first().bidPrice)
              .let { this.client.placeSellOrderAndWait(it, tradeResult.buy, SellType.TIMEOUT) }
              .flatMap { Mono.fromCallable { tradeResult.sellFinished(it, SellType.TIMEOUT) } }
              .doOnNext { logger.info("시간초과 ${if(it.sell!!.avgPrice() > it.buy.avgPrice() ) "이익" else "손실"} 매도가 완료되었습니다.") }
          }
      }
  }


  private fun logCurrentStatus(tradeResult: TradeResult, currentPrice: Double, profitPrice: Double? = null, lossPrice: Double? = null, logger: Logger) {
    LocalDateTime.now()
      .takeIf { it.second < this.watchIntervalSecond }
      ?.let {
        Duration.between(tradeResult.buy.createdAt.toLocalDateTime(), it)
          .let { d ->
            val hours = d.toHours()
            val minutes = d.toMinutes() % 60
            val secs = d.seconds % 60

            if (profitPrice != null && lossPrice != null) {
              logger.info("이득가: ${profitPrice.toStrWithPoint()}, 손실가: ${lossPrice.toStrWithPoint()}, 현재가: ${currentPrice.toStrWithPoint()}, 구매가: ${tradeResult.buy.avgPrice().toStrWithPoint()} 경과시간: $hours 시간 $minutes 분 $secs 초")
            } else {
              logger.info("현재가: ${currentPrice.toStrWithPoint()}, 구매가: ${tradeResult.buy.avgPrice().toStrWithPoint()}, 경과시간: $hours 시간 $minutes 분 $secs 초")
            }
          }
      }
  }

  private fun closeApplication() {
    SpringApplication.exit(this.applicationContext)
  }
}