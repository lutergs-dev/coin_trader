package dev.lutergs.santa.trade.worker.domain

import dev.lutergs.upbitclient.api.exchange.order.OrderRequest
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.api.exchange.order.PlaceOrderRequest
import dev.lutergs.upbitclient.dto.OrderSide
import dev.lutergs.upbitclient.webclient.BasicClient
import dev.lutergs.upbitclient.webclient.Client
import reactor.core.publisher.Mono
import java.time.Duration


class UpbitClient(
  private val basicClient: BasicClient,
  private val waitWatchSecond: Duration = Duration.ofSeconds(1),
  private val repository: LogRepository
): Client() {

  override val account = this.basicClient.account
  override val market = this.basicClient.market
  override val ticker = this.basicClient.ticker
  override val order = this.basicClient.order
  override val orderBook = this.basicClient.orderBook
  override val candle = this.basicClient.candle

  fun placeBuyOrderAndWait(req: PlaceOrderRequest): Mono<OrderResponse> {
    return req.side
      .takeIf { it == OrderSide.BID }
      ?.let {this.placeOrderAndWait(req) }
      ?: Mono.error(IllegalArgumentException("매수용 함수에 매도 주문을 넣었습니다."))
  }

  fun placeSellOrderAndWait(req: PlaceOrderRequest, buyOrder: OrderResponse): Mono<OrderResponse> {
    return req.side
      .takeIf { it == OrderSide.ASK }
      ?.let { this.placeOrderAndWait(req, buyOrder) }
      ?: Mono.error(IllegalArgumentException("매도용 함수에 매수 주문을 넣었습니다."))
  }

  private fun placeOrderAndWait(req: PlaceOrderRequest, buyOrder: OrderResponse? = null): Mono<OrderResponse> {
      return this.order.placeOrder(req)
        .flatMap { when (req.side) {
            OrderSide.BID -> this.repository.newBuyOrder(it.toOrderResponse())
            OrderSide.ASK -> this.repository.newSellOrder(it.toOrderResponse(), buyOrder!!.uuid)
        } }
        .flatMap { orderResponse ->
          Mono.defer { this.order.getOrder(OrderRequest(orderResponse.uuid)) }
            .flatMap {
              if (it.isFinished()) Mono.just(it)
              else Mono.empty()
            }.repeatWhenEmpty (Integer.MAX_VALUE) {
              it.delayElements(this.waitWatchSecond)
            }
        }.flatMap { when (req.side) {
          OrderSide.BID -> this.repository.finishBuyOrder(it)
          OrderSide.ASK -> this.repository.finishSellOrder(buyOrder!!, it)
        } }
  }
}

