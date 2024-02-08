package dev.lutergs.santa.universal.oracle

import org.slf4j.LoggerFactory
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.time.OffsetDateTime


@Deprecated(
  message = "TradeHistoryRepository 를 사용하세요. 이 Repository 는 retry 로직이 적용되지 않은 원본 구현체입니다.",
  replaceWith = ReplaceWith("TradeHistoryRepository")
)
@Repository
interface TradeHistoryReactiveRepository: R2dbcRepository<TradeHistory, String> {
  fun findByBuyFinishAtAfter(buyFinishAt: OffsetDateTime): Flux<TradeHistory>
}

@Component
class TradeHistoryRepository(
  private val repository: TradeHistoryReactiveRepository
) {
  private val logger = LoggerFactory.getLogger(this::class.java)

  fun findById(id: String): Mono<TradeHistory> {
    return this.repository.findById(id)
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when find entity! id = $id", it) }
  }

  fun save(entity: TradeHistory): Mono<TradeHistory> {
    return this.repository.save(entity)
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when save entity! entity = $entity", it) }
  }

  fun findByBuyFinishAtAfter(buyFinishAt: OffsetDateTime): Flux<TradeHistory> {
    return this.repository.findByBuyFinishAtAfter(buyFinishAt)
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError { this.logger.error("error occured when find by finishAt after! finishAt = $buyFinishAt", it) }
  }
}