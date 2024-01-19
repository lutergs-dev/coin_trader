package dev.lutergs.santa.trade.infra

import dev.lutergs.santa.trade.domain.DangerCoinRepository
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@Document("danger_coins")
class DangerCoinEntity {
  @Id var id: String? = null
  @Field @Indexed(name = "expiration_index", expireAfter = "3h")
  var expireIn: String = ""

  var coinName: String = ""
}


@Repository
interface DangerCoinEntityRepository: ReactiveMongoRepository<DangerCoinEntity, String>

@Component
class DangerCoinRepositoryImpl(
  private val repository: DangerCoinEntityRepository
): DangerCoinRepository {
  override fun setDangerCoin(coinName: String): Mono<String> {
    return DangerCoinEntity()
      .apply { this.coinName = coinName }
      .let { this.repository.save(it).flatMap { c -> Mono.just(c.coinName) } }
  }

  override fun getDangerCoins(): Flux<String> {
    return this.repository.findAll()
      .flatMap { Mono.just(it.coinName) }
  }
}