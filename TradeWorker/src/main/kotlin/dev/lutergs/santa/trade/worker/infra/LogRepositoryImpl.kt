package dev.lutergs.santa.trade.worker.infra

import com.fasterxml.jackson.annotation.JsonIgnore
import dev.lutergs.santa.trade.worker.domain.LogRepository
import dev.lutergs.upbeatclient.api.exchange.order.OrderResponse
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.io.Serializable
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
}

@Repository
interface OrderEntityReactiveRepository: ReactiveCrudRepository<OrderEntity, String>

@Component
class LogRepositoryImpl(
  private val repository: OrderEntityReactiveRepository
): LogRepository {

  // new buy order
  override fun newBuyOrder(response: OrderResponse): Mono<OrderResponse> {
    return OrderEntity()
      .apply {
        this.coin = response.market.quote
        this.buyId = response.uuid.toString()
        this.buyPrice = response.price
        this.buyFee = response.reservedFee
        this.buyVolume = response.volume
        this.buyWon = response.price * response.volume + response.reservedFee
        this.buyPlaceAt = response.createdAt
        this.setNewInstance()
      }.let { this.repository.save(it).thenReturn(response) }
  }

  override fun finishBuyOrder(response: OrderResponse): Mono<OrderResponse> {
    return this.repository.findById(response.uuid.toString())
      .flatMap {
        it.buyFinishAt = response.trades.maxOf { d -> d.createdAt }
        this.repository.save(it)
      }.thenReturn(response)
  }

  override fun newSellOrder(response: OrderResponse, buyUuid: UUID): Mono<OrderResponse> {
    return this.repository.findById(buyUuid.toString())
      .flatMap {
        it.sellId = response.uuid.toString()
        it.sellPrice = response.price
        it.sellFee = response.reservedFee
        it.sellVolume = response.volume
        it.sellWon = response.price * response.volume + response.reservedFee
        it.sellPlaceAt = response.createdAt
        this.repository.save(it)
      }.thenReturn(response)
  }

  override fun finishSellOrder(buyResponse: OrderResponse, sellResponse: OrderResponse): Mono<OrderResponse> {
    return this.repository.findById(buyResponse.uuid.toString())
      .flatMap {
        if (it.sellId != sellResponse.uuid.toString()) Mono.error(IllegalStateException("잘못된 주문을 요청했습니다."))
        else {
          it.sellFinishAt = sellResponse.trades.maxOf { d -> d.createdAt }
          it.profit = (sellResponse.price * sellResponse.volume) - (buyResponse.price * buyResponse.volume) - (buyResponse.paidFee + sellResponse.paidFee)
          this.repository.save(it).thenReturn(sellResponse)
        }
      }
  }

  override fun cancelSellOrder(sellUuid: UUID, buyUuid: UUID): Mono<Void> {
    return this.repository.findById(buyUuid.toString())
      .flatMap {
        if (it.sellId != sellUuid.toString()) Mono.error(IllegalStateException("잘못된 주문을 요청했습니다."))
        else {
          it.sellId = null
          it.sellPrice = null
          it.sellFee = null
          it.sellVolume = null
          it.sellWon = null
          it.sellPlaceAt = null
          this.repository.save(it).then(Mono.empty())
        }
      }
  }
}