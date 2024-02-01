package dev.lutergs.santa.trade.worker.domain

import dev.lutergs.santa.trade.worker.domain.entity.SellType
import dev.lutergs.upbitclient.api.exchange.order.OrderRequest
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.api.exchange.order.PlaceOrderRequest
import dev.lutergs.upbitclient.dto.OrderSide
import dev.lutergs.upbitclient.webclient.BasicClient
import dev.lutergs.upbitclient.webclient.Client
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID


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
      ?.let { _ ->
        this.order.placeOrder(req)
          .flatMap { this.order.getOrder(OrderRequest(it.uuid)) }
          .flatMap { this.repository.newBuyOrder(it) }
          .flatMap { this.waitOrderUntilComplete(it.uuid) }
          .flatMap { this.repository.finishBuyOrder(it) }
      }
      ?: Mono.error(IllegalArgumentException("매수용 함수에 매도 주문을 넣었습니다."))
  }

  fun placeSellOrderAndWait(req: PlaceOrderRequest, buyOrder: OrderResponse, sellType: SellType): Mono<OrderResponse> {
    return req.side
      .takeIf { it == OrderSide.ASK }
      ?.let { _ ->
        this.order.placeOrder(req)
          .flatMap { this.order.getOrder(OrderRequest(it.uuid)) }
          .flatMap { this.repository.newSellOrder(it, buyOrder.uuid) }
          .flatMap { this.waitOrderUntilComplete(it.uuid) }
          .flatMap { this.repository.finishSellOrder(buyOrder, it, sellType) }
      }
      ?: Mono.error(IllegalArgumentException("매도용 함수에 매수 주문을 넣었습니다."))
  }
  
  private fun waitOrderUntilComplete(uuid: UUID): Mono<OrderResponse> {
    return Mono.defer { this.order.getOrder(OrderRequest(uuid)) }
      .flatMap { 
        if (it.isFinished) {
          Mono.just(it)
        } else {
          Mono.empty()
        }
      }.repeatWhenEmpty (Integer.MAX_VALUE) {
        it.delayElements(this.waitWatchSecond)
      }
  }
}

