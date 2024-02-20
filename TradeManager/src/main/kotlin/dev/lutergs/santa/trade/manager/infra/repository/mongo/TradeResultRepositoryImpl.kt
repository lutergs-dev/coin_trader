package dev.lutergs.santa.trade.manager.infra.repository.mongo

import dev.lutergs.santa.trade.manager.domain.TradeResultRepository
import dev.lutergs.santa.trade.manager.domain.entity.ManagerTradeResult
import dev.lutergs.santa.util.SellType
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.util.retry.Retry
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

@Document("trade_history")
class TradeResultEntity {
  @Id
  var id: UUID? = null    // UUID

  @Field
  lateinit var buy: OrderResponse

  @Field
  var sell: OrderResponse? = null

  @Field
  var sellType: SellType = SellType.NULL

  fun toManagerTradeResult(): ManagerTradeResult = ManagerTradeResult(buy, sell, sellType)

  companion object {
    fun fromManagerTradeResult(managerTradeResult: ManagerTradeResult): TradeResultEntity = TradeResultEntity()
      .apply {
        this.id = managerTradeResult.buy.uuid
        this.buy = managerTradeResult.buy
        this.sell = managerTradeResult.sell
        this.sellType = managerTradeResult.sellType
      }
  }
}

@Repository
interface TradeResultReactiveMongoRepository: ReactiveMongoRepository<TradeResultEntity, UUID> {
  fun findAllByBuyCreatedAtAfter(buyCreatedAt: OffsetDateTime): Flux<TradeResultEntity>
}

@Component
class TradeResultRepositoryImpl(
  private val repository: TradeResultReactiveMongoRepository
): TradeResultRepository {
  private val logger = LoggerFactory.getLogger(TradeResultRepositoryImpl::class.java)

  override fun save(t: ManagerTradeResult): Mono<ManagerTradeResult> {
    return this.repository.findById(t.buy.uuid)
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when find entity! id = ${t.buy.uuid}", it) }
      .flatMap { 
        it.buy = t.buy
        it.sell = t.sell
        it.sellType = t.sellType
        Mono.fromCallable { it }
      }.switchIfEmpty { Mono.fromCallable { TradeResultEntity.fromManagerTradeResult(t) } }
      .flatMap { this.repository.save(it) }
      .flatMap { Mono.fromCallable { it.toManagerTradeResult() } }
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when save entity! entity = $t", it) }
  }

  override fun findByBuyUUID(uuid: UUID): Mono<ManagerTradeResult> {
    return this.repository.findById(uuid)
      .flatMap { Mono.fromCallable { it.toManagerTradeResult() } }
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when find entity! id = $uuid", it) }
  }

  override fun getAllResultAfterDateTime(datetime: OffsetDateTime): Flux<ManagerTradeResult> {
    return this.repository.findAllByBuyCreatedAtAfter(datetime)
      .flatMap { Mono.fromCallable { it.toManagerTradeResult() } }
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when find by finishAt after! finishAt = $datetime", it) }
  }

}