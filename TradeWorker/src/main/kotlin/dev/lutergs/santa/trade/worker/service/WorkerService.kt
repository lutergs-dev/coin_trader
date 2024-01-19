package dev.lutergs.santa.trade.worker.service

import dev.lutergs.santa.trade.worker.domain.entity.MainTrade
import dev.lutergs.santa.trade.worker.domain.UpbitClient
import dev.lutergs.santa.trade.worker.domain.LogRepository
import dev.lutergs.santa.trade.worker.domain.MessageSender
import dev.lutergs.santa.trade.worker.domain.entity.*
import dev.lutergs.upbeatclient.api.exchange.order.OrderRequest
import dev.lutergs.upbeatclient.api.exchange.order.OrderResponse
import dev.lutergs.upbeatclient.api.exchange.order.PlaceOrderRequest
import dev.lutergs.upbeatclient.dto.Markets
import dev.lutergs.upbeatclient.dto.OrderSide
import dev.lutergs.upbeatclient.dto.OrderType
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeoutException

@Service
class WorkerService(
  private val repository: LogRepository,
  private val alarmSender: MessageSender,
  private val mainTrade: MainTrade,
  private val client: UpbitClient,
  private val applicationContext: ApplicationContext,
  @Value("\${custom.trade.sell.wait-hour}") waitHour: Long,
  @Value("\${custom.trade.sell.profit-percent}") profitPercent: Double,
  @Value("\${custom.trade.sell.loss-percent}") lossPercent: Double,
  @Value("\${custom.id}") private val serviceId: String
) {
  private val waitDuration = Duration.ofHours(waitHour)
  private val profitTotalPercent = (100.0 + profitPercent) / 100.0
  private val lossTotalPercent = (100.0 - lossPercent) / 100.0
  private val logger = LoggerFactory.getLogger(this::class.java)

  @PostConstruct
  fun run() {
    this.client.orderBook.getOrderBook(Markets.fromMarket(this.mainTrade.market))
      .next()
      .flatMap { orderBookResponse ->
        // get the lowest sell price and buy
        val lowestPrice = orderBookResponse.orderbookUnits[0].askPrice
        val calculatedVolume = (this.mainTrade.money.toDouble() / lowestPrice)//(lowestPrice /  this.mainTrade.money.toDouble())
        PlaceOrderRequest(this.mainTrade.market, OrderType.LIMIT, OrderSide.BID, calculatedVolume, lowestPrice)
          .also { this.logger.info("[${this.serviceId}] 코인 구매를 시작합니다. 대상 코인 : ${it.market}, 가격 : $lowestPrice, 구매량 : $calculatedVolume, 총금액: ${this.mainTrade.money.toDouble()}") }
          .let { this.client.placeBuyOrderAndWait(it) }
          .doOnNext { this.logger.info("[${this.serviceId}] 코인 구매가 완료되었습니다. 구매 UUID : ${it.uuid}") }
          .flatMap { Mono.just(Pair(orderBookResponse, it)) }
      }.flatMap { pair ->
        // place sell order by requested price
        val (orderbook, order) = pair
        val expectedSellPrice = order.price * this.profitTotalPercent
        orderbook.orderbookUnits
          .filter { it.askPrice < expectedSellPrice }
          .maxOf { it.askPrice }
          .let { PlaceOrderRequest(order.market, OrderType.LIMIT, OrderSide.ASK, order.volume, it) }
          .also { this.logger.info("[${this.serviceId}] 코인의 이득점을 설정했습니다. 가격 : ${it.price}, 총금액 : ${it.volume!! * it.price!!}") }
          // wait until finish
          .let { this.placeSellOrderAndWaitForFinish(it, order) }
          .flatMap {
            val earnPrice = (it.price * it.volume ) - (order.price * order.volume ) - (it.paidFee + order.paidFee)
            this.alarmSender.sendTradeResult(TradeResult(it.uuid, TradeResultValue(earnPrice, order, it)))
              .thenReturn("complete")
          }.switchIfEmpty { Mono.just("complete") }
      }.subscribe { this.closeApplication() }
  }

  private fun placeSellOrderAndWaitForFinish(request: PlaceOrderRequest, buyOrder: OrderResponse): Mono<OrderResponse> {
    return this.client.order.placeOrder(request)
      .doOnNext { this.logger.info("[${this.serviceId}] 코인의 이득 판매 주문을 올렸습니다.") }
      .flatMap { this.repository.newSellOrder(it.toOrderResponse(), buyOrder.uuid).thenReturn(it) }
      .flatMap { firstSellPlaceResponse ->
        // 1초에 한 번씩 가격과 오더 상태를 같이 확인함
        Mono.zip(
          Mono.defer { this.client.order.getOrder(OrderRequest(firstSellPlaceResponse.uuid)) },
          Mono.defer { this.client.orderBook.getOrderBook(Markets.fromMarket(firstSellPlaceResponse.market)).next() }
        ).flatMap {
          val firstSellOrder = it.t1
          val currentPrice = it.t2.orderbookUnits.first().bidPrice

          // log
          LocalDateTime.now()
            .also { l ->
              if (l.second == 0) {
                Duration.between(it.t1.createdAt.toLocalDateTime(), l)
                  .also { d ->
                    val hours = d.toHours()
                    val minutes = d.toMinutes() % 60
                    val secs = d.seconds % 60
                    this.logger.info("[${this.serviceId}] 설정 코인 가격 : ${it.t1.price}, 현재 코인 가격 : $currentPrice, 경과시간 : $hours 시간 $minutes 분 $secs 초")
                  }
              }
            }

          if (firstSellOrder.state == "done") {
            this.logger.info("[${this.serviceId}] 코인을 이득을 보고 매매했습니다.")
            Mono.just(it.t1)
          } else if (currentPrice < request.price!! * this.lossTotalPercent) {
            this.logger.info("[${this.serviceId}] 코인이 ${(1L - this.lossTotalPercent) * 100}% 이상 손해를 보고 있습니다. 현재 가격으로 매매합니다.")
            this.cancelSellOrderAndSellByCurrentPrice(firstSellOrder, buyOrder)
              .flatMap { orderResponse ->
                this.alarmSender.sendAlarm(
                  AlarmMessage(
                    AlarmMessageKey(orderResponse.market.quote),
                    AlarmMessageValue(orderResponse.uuid))
                ).thenReturn(orderResponse)
                  .doOnNext { this.logger.info("[${this.serviceId}] 손실 매도한 코인 (${this.mainTrade.market.quote}) 에 대한 정보를 controller 로 전송했습니다.") }
              }
          } else {
            Mono.empty()
          }
        }.repeatWhenEmpty(Integer.MAX_VALUE) {
          it.delayElements(Duration.ofSeconds(1))
        }.flatMap { sellResponse ->
          this.repository.finishSellOrder(buyOrder, sellResponse)
        }.timeout(this.waitDuration)
          .onErrorResume(TimeoutException::class.java) {
            this.logger.info("[${this.serviceId}] ${this.waitDuration.toHours()} 시간동안 기다렸지만 매매가 되지 않아, 현재 가격으로 판매합니다.")
            this.cancelSellOrderAndSellByCurrentPrice(firstSellPlaceResponse.toOrderResponse(), buyOrder)
        }
      }
  }

  private fun cancelSellOrderAndSellByCurrentPrice(firstSellOrder: OrderResponse, buyOrder: OrderResponse): Mono<OrderResponse> {
    return this.client.order.cancelOrder(OrderRequest(firstSellOrder.uuid))
      .flatMap { this.repository.cancelSellOrder(firstSellOrder.uuid, buyOrder.uuid).thenReturn(it) }
      .flatMap { this.client.orderBook.getOrderBook(Markets.fromMarket(it.market)).next() }
      .flatMap { res ->
        this.logger.info("[${this.serviceId}] 코인 매도 주문을 취소했습니다. 현재 가격인 ${res.orderbookUnits.first().bidPrice} 에 매도합니다.")
        PlaceOrderRequest(
          market = res.market,
          type = OrderType.LIMIT,
          side = OrderSide.ASK,
          volume = firstSellOrder.volume,
          price = res.orderbookUnits.first().bidPrice
        ).let { this.client.placeSellOrderAndWait(it, buyOrder) }
      }.doOnNext { this.logger.info("[${this.serviceId}] 코인 현재가 매도가 완료되었습니다.") }
  }

  private fun closeApplication() {
    SpringApplication.exit(this.applicationContext)
  }
}