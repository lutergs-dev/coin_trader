package dev.lutergs.santa.trade.worker.infra

import com.fasterxml.jackson.annotation.JsonIgnore
import dev.lutergs.santa.trade.worker.domain.LogRepository
import dev.lutergs.santa.trade.worker.domain.entity.SellType
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.io.Serializable
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*


@Table(name = "coin_trade_order_list")
class OrderEntity: Persistable<String>, Serializable {
  @Column(value = "coin") var coin: String? = null

  /* need index */@Id @Column(value = "buy_uuid") var buyId: String? = null  // main buy order id
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

  @Transient @JsonIgnore private var newInstance: Boolean = false

  override fun getId(): String? {
    return this.buyId
  }

  override fun isNew(): Boolean {
    return this.newInstance
  }

  fun setNewInstance() {
    this.newInstance = true
  }

  override fun toString(): String {
    return "OrderEntity(coin=$coin, buyId=$buyId, buyPrice=$buyPrice, buyFee=$buyFee, buyVolume=$buyVolume, buyWon=$buyWon, buyPlaceAt=$buyPlaceAt, buyFinishAt=$buyFinishAt, sellId=$sellId, sellPrice=$sellPrice, sellFee=$sellFee, sellVolume=$sellVolume, sellWon=$sellWon, sellPlaceAt=$sellPlaceAt, sellFinishAt=$sellFinishAt, profit=$profit, newInstance=$newInstance)"
  }

}

@Repository
interface OrderEntityReactiveRepository: ReactiveCrudRepository<OrderEntity, String>

@Component
class LogRepositoryImpl(
  private val repository: OrderEntityReactiveRepository
): LogRepository {
  private val logger = LoggerCreate.createLogger(this::class)

  private fun retryFindById(id: String): Mono<OrderEntity> {
    return this.repository.findById(id)
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when find entity! id = $id", it) }
  }

  private fun retrySave(entity: OrderEntity): Mono<OrderEntity> {
    return this.repository.save(entity)
      .retryWhen(Retry.fixedDelay(5,Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when save entity! entity = $entity", it) }
  }

  // new buy order
  override fun newBuyOrder(response: OrderResponse): Mono<OrderResponse> {
    return OrderEntity()
      .apply {
        this.coin = response.market.quote
        this.buyId = response.uuid.toString()
        this.buyPrice = response.price
        this.buyFee = response.reservedFee
        this.buyVolume = response.volume
        this.buyWon = response.price * response.volume - response.reservedFee
        this.buyPlaceAt = response.createdAt
        this.setNewInstance()
      }.let { this.retrySave(it).thenReturn(response) }
  }

  override fun finishBuyOrder(response: OrderResponse): Mono<OrderResponse> {
    return this.retryFindById(response.uuid.toString())
      .flatMap {
        it.buyFinishAt = response.trades.maxOf { d -> d.createdAt }
        this.retrySave(it)
      }.thenReturn(response)
  }

  override fun newSellOrder(response: OrderResponse, buyUuid: UUID): Mono<OrderResponse> {
    return this.retryFindById(buyUuid.toString())
      .flatMap {
        it.sellId = response.uuid.toString()
        it.sellPrice = response.price
        it.sellVolume = response.volume
        it.sellFee = if (response.state == "done") {
          listOf(response.paidFee, response.reservedFee, response.remainingFee).max()
        } else {
          response.reservedFee
        }
        it.sellWon = response.price * response.volume - it.sellFee!!
        it.sellPlaceAt = response.createdAt
        this.retrySave(it)
      }.thenReturn(response)
  }

  override fun finishSellOrder(buyResponse: OrderResponse, sellResponse: OrderResponse, sellType: SellType): Mono<OrderResponse> {
    return this.retryFindById(buyResponse.uuid.toString())
      .flatMap {
        if (it.sellId != sellResponse.uuid.toString()) Mono.error(IllegalStateException("잘못된 주문을 요청했습니다."))
        else {
          it.sellFinishAt = sellResponse.trades.maxOf { d -> d.createdAt }
          it.profit = (sellResponse.price * sellResponse.volume) - (buyResponse.price * buyResponse.volume) - (buyResponse.paidFee + sellResponse.paidFee)
          it.sellType = sellType.name
          this.retrySave(it).thenReturn(sellResponse)
        }
      }
  }

  override fun cancelSellOrder(sellUuid: UUID, buyUuid: UUID): Mono<Void> {
    return this.retryFindById(buyUuid.toString())
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(2)))
      .flatMap {
        if (it.sellId != sellUuid.toString()) Mono.error(IllegalStateException("잘못된 주문을 요청했습니다."))
        else {
          it.sellId = null
          it.sellPrice = null
          it.sellFee = null
          it.sellVolume = null
          it.sellWon = null
          it.sellPlaceAt = null
          this.retrySave(it).then(Mono.empty())
        }
      }
  }
}