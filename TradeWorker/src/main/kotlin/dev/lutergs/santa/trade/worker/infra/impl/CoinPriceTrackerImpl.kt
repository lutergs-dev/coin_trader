package dev.lutergs.santa.trade.worker.infra.impl

import dev.lutergs.santa.trade.worker.domain.CoinPriceTracker
import dev.lutergs.santa.trade.worker.domain.entity.WorkerTradeResult
import dev.lutergs.santa.trade.worker.infra.repository.MongoCoinPriceEntity
import dev.lutergs.santa.trade.worker.infra.repository.MongoCoinPriceReactiveRepository
import dev.lutergs.santa.util.toStrWithStripTrailing
import dev.lutergs.upbitclient.api.quotation.orderbook.OrderStep
import dev.lutergs.upbitclient.dto.Markets
import dev.lutergs.upbitclient.webclient.BasicClient
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.util.*

class CoinPriceTrackerImpl(
  private val basicClient: BasicClient,
  private val repository: MongoCoinPriceReactiveRepository
): CoinPriceTracker {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun getCoinCurrentPrice(workerTradeResult: WorkerTradeResult): Mono<BigDecimal> {
    return this.basicClient.ticker.getTicker(Markets.fromMarket(workerTradeResult.buy.market)).next()
      .flatMap { ticker ->
        MongoCoinPriceEntity.fromTickerResponse(workerTradeResult, ticker)
          .let { this.repository.save(it) }
          .thenReturn(ticker.tradePrice)
      }
  }

  override fun getCoinEMA(workerTradeResult: WorkerTradeResult): Mono<BigDecimal> {
    TODO("Not yet implemented")
  }

  override fun getAvgOfLatestN(buyUUID: UUID, latestN: Int): Mono<BigDecimal> {
    TODO("Not yet implemented")
  }

  override fun getAvgOfLatestAB(buyUUID: UUID, latestA: Int, latestB: Int): Mono<Pair<BigDecimal, BigDecimal>> {
    return this.repository.findAllByTradeIdOrderByExpireIn5hDesc(buyUUID, Pageable.ofSize(latestA))
      .collectList()
      .flatMap { list -> Mono.fromCallable { Pair(
        list.take(latestA).let { a -> a.sumOf { it.price } / BigDecimal(a.size) },
        list.take(latestB).let { b -> b.sumOf { it.price } / BigDecimal(b.size) }
      ) } }
  }

  override fun cleanUp(buyUUID: UUID): Mono<Void> {
    return this.repository.findAllByTradeId(buyUUID)
      .let { this.repository.deleteAll(it) }
  }
}