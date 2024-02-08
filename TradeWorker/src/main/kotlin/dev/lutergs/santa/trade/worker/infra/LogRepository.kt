package dev.lutergs.santa.trade.worker.infra

import dev.lutergs.santa.universal.oracle.SellType
import dev.lutergs.santa.universal.oracle.TradeHistory
import dev.lutergs.santa.universal.oracle.TradeHistoryRepository
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.*


@Component
class LogRepository(
  private val repository: TradeHistoryRepository
) {
  fun newLogWithBuyResult(response: OrderResponse): Mono<OrderResponse> {
    return TradeHistory()
      .apply {
        this.coin = response.market.quote
        this.buyId = response.uuid.toString()
        this.buyPrice = response.avgPrice()
        this.buyFee = response.reservedFee
        this.buyVolume = response.totalVolume()
        this.buyWon = response.avgPrice() * response.totalVolume() - response.reservedFee
        this.buyPlaceAt = response.createdAt
        this.buyFinishAt = response.trades.maxOf { d -> d.createdAt }
        this.setNewInstance()
      }.let { this.repository.save(it).thenReturn(response) }
  }

  fun updateLogWithSellResult(buyUUID: UUID, sellResponse: OrderResponse, sellType: SellType): Mono<OrderResponse> {
    return this.repository.findById(buyUUID.toString())
      .flatMap {
        it.sellId = sellResponse.uuid.toString()
        it.sellPrice = if (sellResponse.isFinished()) sellResponse.avgPrice() else null
        it.sellFee = sellResponse.paidFee
        it.sellVolume = if (sellResponse.isFinished()) sellResponse.totalVolume() else null
        it.sellWon = if (sellResponse.isFinished()) sellResponse.totalPrice() else null
        it.sellPlaceAt = sellResponse.createdAt
        it.sellFinishAt = sellResponse.trades.maxOfOrNull { t -> t.createdAt }
        it.profit = if (sellResponse.isFinished()) it.sellWon!! - it.buyWon else null
        it.sellType = sellType
        this.repository.save(it).thenReturn(sellResponse)
      }
  }

  fun updateLogWithEmptySellResult(buyUUID: UUID): Mono<Void> {
    return this.repository.findById(buyUUID.toString())
      .flatMap {
        it.sellId = null
        it.sellPrice = null
        it.sellFee = null
        it.sellVolume = null
        it.sellWon = null
        it.sellPlaceAt = null
        it.sellFinishAt = null
        it.profit = null
        it.sellType = SellType.NULL
        this.repository.save(it).then()
      }
  }
}