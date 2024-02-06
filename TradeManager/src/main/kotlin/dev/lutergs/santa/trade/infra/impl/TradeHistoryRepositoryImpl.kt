package dev.lutergs.santa.trade.infra.impl

import com.fasterxml.jackson.annotation.JsonIgnore
import dev.lutergs.santa.trade.domain.CompleteOrderResult
import dev.lutergs.santa.trade.domain.OrderResult
import dev.lutergs.santa.trade.domain.SellType
import dev.lutergs.santa.trade.domain.TradeHistoryRepository
import dev.lutergs.upbitclient.dto.MarketCode
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.io.Serializable
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID


@Table(name = "coin_trade_order_list")
class OrderEntity: Persistable<String>, Serializable {
  @Column(value = "coin") var coin: String = ""

  /* need index */ @Id
  @Column(value = "buy_uuid") var buyId: String = "" // main buy order id
  @Column(value = "buy_price") var buyPrice: Double = 0.0
  @Column(value = "buy_fee") var buyFee: Double = 0.0
  @Column(value = "buy_volume") var buyVolume: Double = 0.0
  @Column(value = "buy_won") var buyWon: Double = 0.0
  @Column(value = "buy_place_at") var buyPlaceAt: OffsetDateTime = OffsetDateTime.now()
  @Column(value = "buy_finish_at") var buyFinishAt: OffsetDateTime = OffsetDateTime.now()

  /* need index */ @Column(value = "sell_uuid") var sellId: String? = null
  @Column(value = "sell_price") var sellPrice: Double? = null
  @Column(value = "sell_fee") var sellFee: Double? = null
  @Column(value = "sell_volume") var sellVolume: Double? = null
  @Column(value = "sell_won") var sellWon: Double? = null
  @Column(value = "sell_place_at") var sellPlaceAt: OffsetDateTime? = null
  @Column(value = "sell_finish_at") var sellFinishAt: OffsetDateTime? = null

  @Column(value = "profit") var profit: Double? = null
  @Column(value = "sell_type") var sellType: String? = null

  @Transient
  @JsonIgnore
  private var newInstance: Boolean = false

  override fun getId(): String {
    return this.buyId
  }

  override fun isNew(): Boolean {
    return this.newInstance
  }

  fun sellType(): SellType {
    return when (this.sellType) {
      "PROFIT" -> SellType.PROFIT
      "LOSS" -> SellType.LOSS
      "STOP_PROFIT" -> SellType.STOP_PROFIT
      "STOP_LOSS" -> SellType.STOP_LOSS
      "TIMEOUT" -> when {
        this.sellWon!! >= this.buyWon -> SellType.TIMEOUT_PROFIT
        else -> SellType.TIMEOUT_LOSS
      }
      "TIMEOUT_LOSS" -> SellType.TIMEOUT_LOSS
      "TIMEOUT_PROFIT" -> SellType.TIMEOUT_PROFIT
      null -> when {
        this.sellId == null -> SellType.NOT_PLACED
        else -> SellType.NOT_COMPLETED
      }
      else -> throw IllegalStateException("잘못된 상태입니다")
    }
  }

  fun toCompleteOrderResult(): CompleteOrderResult {
    return CompleteOrderResult(
      buy = OrderResult(
        id = this.buyId.let { UUID.fromString(it) },
        price = this.buyPrice,
        fee = this.buyFee,
        volume = this.buyVolume,
        won = this.buyWon,
        placeAt = this.buyPlaceAt,
        finishAt = this.buyFinishAt
      ),
      sellType = this.sellType(),
      sell = when (this.sellType()) {
        SellType.PROFIT, SellType.LOSS, SellType.STOP_PROFIT, SellType.STOP_LOSS, SellType.TIMEOUT_PROFIT, SellType.TIMEOUT_LOSS -> OrderResult(
          id = this.sellId!!.let { UUID.fromString(it) },
          price = this.sellPrice!!,
          fee = this.sellFee!!,
          volume = this.sellVolume!!,
          won = this.sellWon!!,
          placeAt = this.sellPlaceAt!!,
          finishAt = this.sellFinishAt!!
        )
        SellType.NOT_PLACED -> null
        SellType.NOT_COMPLETED -> OrderResult(
          id = this.sellId!!.let { UUID.fromString(it) },
          price = this.sellPrice ?: Double.NaN,
          fee = this.sellFee ?: Double.NaN,
          volume = this.sellVolume ?: Double.NaN,
          won = this.sellWon ?: Double.NaN,
          placeAt = this.sellPlaceAt!!,
          finishAt = this.sellFinishAt ?: OffsetDateTime.MIN
        )
      },
      profit = this.sellType().isFinished()
        .takeIf { it }
        ?.let { this.profit!! },
      coin = MarketCode("KRW", this.coin)
    )
  }
}

@Repository
interface OrderEntityReactiveRepository: ReactiveCrudRepository<OrderEntity, String> {
  fun findAllByBuyFinishAtBetween(startAt: OffsetDateTime, endAt: OffsetDateTime): Flux<OrderEntity>

  fun findByBuyFinishAtAfter(buyFinishAt: OffsetDateTime): Flux<OrderEntity>
}

@Repository
class TradeHistoryRepositoryImpl(
  private val repository: OrderEntityReactiveRepository
): TradeHistoryRepository {
  private val logger = LoggerFactory.getLogger(TradeHistoryRepositoryImpl::class.java)
  override fun getTradeHistoryBetweenDatetime(startAt: OffsetDateTime, endAt: OffsetDateTime): Flux<CompleteOrderResult> {
    return this.repository.findAllByBuyFinishAtBetween(startAt, endAt)
      .flatMap { Mono.fromCallable { it.toCompleteOrderResult() } }
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when search for history", it) }
  }

  override fun getTradeHistoryAfter(datetime: OffsetDateTime): Flux<CompleteOrderResult> {
    return this.repository.findByBuyFinishAtAfter(datetime)
      .flatMap { Mono.fromCallable { it.toCompleteOrderResult() } }
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when search for history", it) }
  }
}