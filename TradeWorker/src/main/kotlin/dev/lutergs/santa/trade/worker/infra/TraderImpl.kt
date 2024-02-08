package dev.lutergs.santa.trade.worker.infra

import dev.lutergs.santa.trade.worker.domain.Trader
import dev.lutergs.santa.trade.worker.domain.entity.TradeResult
import dev.lutergs.santa.universal.oracle.SellType
import dev.lutergs.upbitclient.api.exchange.order.OrderRequest
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.api.exchange.order.PlaceOrderRequest
import dev.lutergs.upbitclient.dto.MarketCode
import dev.lutergs.upbitclient.dto.OrderSide
import dev.lutergs.upbitclient.dto.OrderType
import dev.lutergs.upbitclient.webclient.BasicClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.util.*

@Component
class TraderImpl (
  private val repository: LogRepository,
  private val client: BasicClient
): Trader {
  private val waitWatchSecond: Duration = Duration.ofSeconds(1)

  // 현재 보유한 코인을 시장가로 판매
  override fun sellMarket(tradeResult: TradeResult, sellType: SellType): Mono<TradeResult> {
    return PlaceOrderRequest(tradeResult.buy.market, OrderType.MARKET, OrderSide.ASK, volume = tradeResult.buy.totalVolume())
      .let { req -> this.client.order.placeOrder(req)
        .flatMap { this.waitOrderUntilComplete(it.uuid) }
        .flatMap { this.repository.updateLogWithSellResult(tradeResult.buy.uuid, it, sellType) }
      }.flatMap { Mono.fromCallable { tradeResult.completeSellOrder(it, sellType) } }
  }

  // 코인을 시장가로 구매 후 TradeResult 변환
  override fun buyMarket(market: MarketCode, money: BigDecimal): Mono<TradeResult> {
    return PlaceOrderRequest(market, OrderType.PRICE, OrderSide.BID, price = money)
      .let { req -> this.client.order.placeOrder(req)
        .flatMap { this.waitOrderUntilComplete(it.uuid) }
        .flatMap { this.repository.newLogWithBuyResult(it) }
      }.flatMap { Mono.fromCallable { TradeResult.createFromBuy(it) } }
  }

  // 지정가 매도 주문 실행
  override fun placeSellLimit(tradeResult: TradeResult, price: BigDecimal): Mono<TradeResult> {
    return PlaceOrderRequest(tradeResult.buy.market, OrderType.LIMIT, OrderSide.ASK, tradeResult.buy.totalVolume(), price.stripTrailingZeros())
      .let { this.client.order.placeOrder(it) }
      .flatMap { this.client.order.getOrder(OrderRequest(it.uuid)) }
      .flatMap { this.repository.updateLogWithSellResult(tradeResult.buy.uuid, it, SellType.PLACED) }
      .flatMap { Mono.fromCallable { tradeResult.sellLimitOrderPlaced(it) } }
  }

  // 지정가 매도 주문이 완료되었을 때 데이터 기록
  override fun finishSellLimit(tradeResult: TradeResult): Mono<TradeResult> {
    return this.repository.updateLogWithSellResult(tradeResult.buy.uuid, tradeResult.sell!!, tradeResult.sellType)
      .thenReturn(tradeResult)
  }

  // 지정가 매도 주문 취소
  override fun cancelSellLimit(tradeResult: TradeResult): Mono<TradeResult> {
    return if (tradeResult.sellType == SellType.PLACED) {
      this.client.order.cancelOrder(OrderRequest(tradeResult.sell!!.uuid))
        .flatMap { this.repository.updateLogWithEmptySellResult(tradeResult.buy.uuid) }
        .then(Mono.fromCallable { tradeResult.cancelSellOrder() })
    } else {
      throw IllegalStateException("매도 주문이 지정가가 아니거나, 완료된 주문입니다.")
    }
  }

  // 지정가 구매 주문
  override fun buyLimit(market: MarketCode, volume: BigDecimal, price: BigDecimal): Mono<TradeResult> {
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