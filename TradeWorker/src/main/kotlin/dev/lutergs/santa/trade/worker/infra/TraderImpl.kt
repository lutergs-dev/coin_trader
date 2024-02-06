package dev.lutergs.santa.trade.worker.infra

import dev.lutergs.santa.trade.worker.domain.Trader
import dev.lutergs.santa.trade.worker.domain.entity.SellType
import dev.lutergs.santa.trade.worker.domain.entity.TradeResult
import dev.lutergs.upbitclient.api.exchange.order.OrderRequest
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.api.exchange.order.PlaceOrderRequest
import dev.lutergs.upbitclient.dto.MarketCode
import dev.lutergs.upbitclient.dto.OrderSide
import dev.lutergs.upbitclient.dto.OrderType
import dev.lutergs.upbitclient.webclient.BasicClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*

@Component
class TraderImpl (
  private val repository: LogRepository,
  private val client: BasicClient
): Trader {
  private val waitWatchSecond: Duration = Duration.ofSeconds(1)

  // 시장가 판매 주문
  override fun sellMarket(tradeResult: TradeResult, sellType: SellType): Mono<TradeResult> {
    return PlaceOrderRequest(tradeResult.buy.market, OrderType.MARKET, OrderSide.ASK, volume = tradeResult.buy.totalVolume())
      .let { req -> this.client.order.placeOrder(req)
        .flatMap { this.waitOrderUntilComplete(it.uuid) }
        .flatMap { this.repository.completeSellOrder(tradeResult.buy, it, sellType) }
      }.flatMap { Mono.fromCallable { tradeResult.completeSellOrder(it, sellType) } }
  }

  // 시장가 구매 주문
  override fun buyMarket(market: MarketCode, money: Double): Mono<TradeResult> {
    return PlaceOrderRequest(market, OrderType.PRICE, OrderSide.BID, price = money)
      .let { req -> this.client.order.placeOrder(req)
        .flatMap { this.waitOrderUntilComplete(it.uuid) }
        .flatMap { this.repository.completeBuyOrder(it) }
      }.flatMap { Mono.fromCallable { TradeResult.createFromBuy(it) } }
  }

  // 지정가 판매 주문
  override fun placeSellLimit(tradeResult: TradeResult, price: Double): Mono<TradeResult> {
    return PlaceOrderRequest(tradeResult.buy.market, OrderType.LIMIT, OrderSide.ASK, tradeResult.buy.totalVolume(), price)
      .let { this.client.order.placeOrder(it) }
      .flatMap { this.client.order.getOrder(OrderRequest(it.uuid)) }
      .flatMap { this.repository.placeSellOrder(it, tradeResult.buy.uuid) }
      .flatMap { Mono.fromCallable { tradeResult.sellLimitOrderPlaced(it) } }
  }

  override fun finishSellOrder(tradeResult: TradeResult): Mono<TradeResult> {
    return this.repository.finishSellOrder(tradeResult.buy, tradeResult.sell!!, tradeResult.sellType)
      .thenReturn(tradeResult)
  }
  
  override fun cancelSellLimit(tradeResult: TradeResult): Mono<TradeResult> {
    return if (tradeResult.sellType == SellType.PLACED) {
      this.client.order.cancelOrder(OrderRequest(tradeResult.sell!!.uuid))
        .flatMap { this.repository.cancelSellOrder(tradeResult.sell.uuid, tradeResult.buy.uuid) }
        .then(Mono.fromCallable { tradeResult.cancelSellOrder() })
    } else {
      throw IllegalStateException("매도 주문이 지정가가 아니거나, 완료된 주문입니다.")
    }
  }

  // 지정가 구매 주문
  override fun buyLimit(market: MarketCode, volume: Double, price: Double): Mono<TradeResult> {
    TODO("Not yet implemented")
  }

  private fun waitOrderUntilComplete(uuid: UUID): Mono<OrderResponse> {
    return Mono.defer { this.client.order.getOrder(OrderRequest(uuid)) }
      .flatMap {
        if (it.isFinished()) {
          Mono.just(it)
        } else {
          Mono.empty()
        }
      }.repeatWhenEmpty (Integer.MAX_VALUE) {
        it.delayElements(this.waitWatchSecond)
      }
  }
}