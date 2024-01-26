package dev.lutergs.santa.trade.infra.impl

import com.fasterxml.jackson.annotation.JsonIgnore
import dev.lutergs.santa.trade.domain.TradeHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.util.retry.Retry
import java.io.Serializable
import java.time.Duration
import java.time.OffsetDateTime


@Table(name = "coin_trade_order_list")
class OrderEntity: Persistable<String>, Serializable {
  @Column(value = "coin") var coin: String? = null

  /* need index */ @Id
  @Column(value = "buy_uuid") var buyId: String? = null  // main buy order id
  @Column(value = "buy_price") var buyPrice: Double? = null
  @Column(value = "buy_fee") var buyFee: Double? = null
  @Column(value = "buy_volume") var buyVolume: Double? = null
  @Column(value = "buy_won") var buyWon: Double? = null
  @Column(value = "buy_place_at") var buyPlaceAt: OffsetDateTime? = null
  @Column(value = "buy_finish_at") var buyFinishAt: OffsetDateTime? = null

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

  override fun getId(): String? {
    return this.buyId
  }

  override fun isNew(): Boolean {
    return this.newInstance
  }

  fun setNewInstance() {
    this.newInstance = true
  }

  fun sellTypeStr(): String? {
    return this.sellType
      ?.let {
        when (it) {
          "PROFIT" -> "익절"
          "LOSS" -> "손절"
          "TIMEOUT" -> "시간초과"
          else -> "오류"
        }
      }
  }
}

@Repository
interface OrderEntityReactiveRepository: ReactiveCrudRepository<OrderEntity, String> {
  fun findAllByBuyFinishAtBetween(startAt: OffsetDateTime, endAt: OffsetDateTime): Flux<OrderEntity>
}

@Repository
class TradeHistoryRepositoryImpl(
  private val repository: OrderEntityReactiveRepository
): TradeHistoryRepository {
  private val logger = LoggerFactory.getLogger(TradeHistoryRepositoryImpl::class.java)
  override fun getTradeHistoryBetweenDatetime(startAt: OffsetDateTime, endAt: OffsetDateTime): Flux<OrderEntity> {
    return this.repository.findAllByBuyFinishAtBetween(startAt, endAt)
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when search for history", it) }
  }
}