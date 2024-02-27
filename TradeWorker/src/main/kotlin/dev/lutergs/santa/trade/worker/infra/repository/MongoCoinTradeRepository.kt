package dev.lutergs.santa.trade.worker.infra.repository

import dev.lutergs.santa.trade.worker.domain.entity.WorkerTradeResult
import dev.lutergs.upbitclient.api.quotation.ticker.TickerResponse
import org.springframework.data.annotation.Id
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*


@Document("coin_trade_price_history")
class MongoCoinPriceEntity {
  @Id
  var id: String? = null

  @Field
  @Indexed
  var tradeId: UUID = UUID.randomUUID()

  @Field
  @Indexed(name = "expiration_index", expireAfterSeconds = 3600 * 24)
  var expireIn: OffsetDateTime = OffsetDateTime.now()

  @Field
  var price: BigDecimal = BigDecimal.ZERO

  companion object {
    fun fromTickerResponse(
      workerTradeResult: WorkerTradeResult,
      tickerResponse: TickerResponse
    ): MongoCoinPriceEntity = MongoCoinPriceEntity().apply {
      this.tradeId = workerTradeResult.buy.uuid
      this.expireIn = OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(tickerResponse.tradeTimestamp),
        ZoneId.of("Asia/Seoul")
      )
      this.price = tickerResponse.tradePrice
    }
  }
}

@Repository
interface MongoCoinPriceReactiveRepository: ReactiveMongoRepository<MongoCoinPriceEntity, UUID> {
  fun findAllByTradeId(tradeId: UUID): Flux<MongoCoinPriceEntity>
  fun findAllByTradeIdOrderByExpireInDesc(tradeId: UUID, pageable: Pageable): Flux<MongoCoinPriceEntity>
}