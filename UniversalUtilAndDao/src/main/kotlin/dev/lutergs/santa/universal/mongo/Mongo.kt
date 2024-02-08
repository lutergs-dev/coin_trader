package dev.lutergs.santa.universal.mongo

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
import java.util.*


@Document("danger_coins")
class DangerCoinEntity {
  @Id
  var id: String? = null

  @Field
  @Indexed(name = "expiration_index", expireAfterSeconds = 3600 * 12)
  var expireIn12h: Date = Date()

  @Field
  var coinName: String = ""
}


@Repository
interface DangerCoinEntityRepository: ReactiveMongoRepository<DangerCoinEntity, String>

@Component
class DangerCoinRepository(
  private val repository: DangerCoinEntityRepository
) {
  private val logger = LoggerFactory.getLogger(this::class.java)

  fun setDangerCoin(coinName: String): Mono<String> {
    return DangerCoinEntity()
      .apply { this.coinName = coinName }
      .let { this.repository.save(it)
        .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
        .doOnError { e -> this.logger.error("error occured when save danger coin [$coinName]", e) }
        .flatMap { c -> Mono.just(c.coinName) }
      }
  }

  fun getDangerCoins(): Flux<String> {
    return this.repository.findAll()
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when find danger coins!", it) }
      .flatMap { Mono.just(it.coinName) }
  }
}