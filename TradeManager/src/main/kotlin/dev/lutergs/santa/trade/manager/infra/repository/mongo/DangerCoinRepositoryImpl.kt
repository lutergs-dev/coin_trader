package dev.lutergs.santa.trade.manager.infra.repository.mongo

import dev.lutergs.santa.trade.manager.domain.DangerCoinRepository
import dev.lutergs.santa.trade.manager.domain.entity.DangerCoin
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.time.OffsetDateTime


@Document("danger_coin_list")
class DangerCoinEntity {
  @Id
  var coinName: String? = null

  @Field
  @Indexed(name = "expiration_index", expireAfterSeconds = 3600 * 3)
  var expireIn: OffsetDateTime = OffsetDateTime.now()

  fun toDangerCoin(): DangerCoin = DangerCoin(this.coinName!!, this.expireIn)
}


@Repository
interface DangerCoinEntityRepository: ReactiveMongoRepository<DangerCoinEntity, String>

@Component
class DangerCoinRepositoryImpl(
  private val repository: DangerCoinEntityRepository
): DangerCoinRepository {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun setDangerCoin(coinName: String): Mono<DangerCoin> {
    return DangerCoinEntity()
      .apply { this.coinName = coinName }
      .let { this.repository.save(it)
        .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
        .doOnError { e -> this.logger.error("error occured when save danger coin [$coinName]", e) }
        .flatMap { Mono.fromCallable { it.toDangerCoin() } }
      }
  }

  override fun getDangerCoins(): Flux<DangerCoin> {
    return this.repository.findAll()
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when find danger coins!", it) }
      .flatMap { Mono.fromCallable { it.toDangerCoin() } }
  }
}