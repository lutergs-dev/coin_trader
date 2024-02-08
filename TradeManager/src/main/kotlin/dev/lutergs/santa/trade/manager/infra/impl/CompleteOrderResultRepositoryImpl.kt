package dev.lutergs.santa.trade.manager.infra.impl

import dev.lutergs.santa.trade.manager.domain.CompleteOrderResult
import dev.lutergs.santa.trade.manager.domain.OrderResult
import dev.lutergs.santa.trade.manager.domain.CompleteOrderResultRepository
import dev.lutergs.santa.universal.oracle.SellType
import dev.lutergs.santa.universal.oracle.TradeHistory
import dev.lutergs.upbitclient.dto.MarketCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import dev.lutergs.santa.universal.oracle.TradeHistoryRepository


fun TradeHistory.toCompleteOrderResult() = CompleteOrderResult(
  buy = OrderResult(
    id = this.buyId.let { UUID.fromString(it) },
    price = this.buyPrice,
    fee = this.buyFee,
    volume = this.buyVolume,
    won = this.buyWon,
    placeAt = this.buyPlaceAt,
    finishAt = this.buyFinishAt
  ),
  sellType = this.sellType,
  sell = when (this.sellType) {
    SellType.PROFIT, SellType.LOSS, SellType.STOP_PROFIT, SellType.STOP_LOSS, SellType.TIMEOUT_PROFIT, SellType.TIMEOUT_LOSS -> OrderResult(
      id = this.sellId!!.let { UUID.fromString(it) },
      price = this.sellPrice!!,
      fee = this.sellFee!!,
      volume = this.sellVolume!!,
      won = this.sellWon!!,
      placeAt = this.sellPlaceAt!!,
      finishAt = this.sellFinishAt!!
    )
    SellType.PLACED -> OrderResult(
      id = this.sellId!!.let { UUID.fromString(it) },
      price = this.sellPrice ?: BigDecimal.ZERO,
      fee = this.sellFee ?: BigDecimal.ZERO,
      volume = this.sellVolume ?: BigDecimal.ZERO,
      won = this.sellWon ?: BigDecimal.ZERO,
      placeAt = this.sellPlaceAt!!,
      finishAt = OffsetDateTime.MIN
    )
    SellType.NULL -> null
  },
  profit = if (this.sellType.isFinished()) this.profit!! else null ,
  coin = MarketCode("KRW", this.coin)
)


@Repository
class CompleteOrderResultRepositoryImpl(
  private val repository: TradeHistoryRepository
): CompleteOrderResultRepository {
  private val logger = LoggerFactory.getLogger(CompleteOrderResultRepositoryImpl::class.java)

  override fun getCompleteOrderResultAfter(datetime: OffsetDateTime): Flux<CompleteOrderResult> {
    return this.repository.findByBuyFinishAtAfter(datetime)
      .flatMap { Mono.fromCallable { it.toCompleteOrderResult() } }
      .doOnError { this.logger.error("error occured when search for history", it) }
  }
}