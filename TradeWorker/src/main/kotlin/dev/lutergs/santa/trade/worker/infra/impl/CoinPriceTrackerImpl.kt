package dev.lutergs.santa.trade.worker.infra.impl

import dev.lutergs.santa.trade.worker.domain.CoinPriceTracker
import dev.lutergs.santa.trade.worker.domain.entity.WorkerTradeResult
import dev.lutergs.santa.trade.worker.infra.repository.MongoCoinPriceEntity
import dev.lutergs.santa.trade.worker.infra.repository.MongoCoinPriceReactiveRepository
import dev.lutergs.santa.util.toStrWithScale
import dev.lutergs.santa.util.toStrWithStripTrailing
import dev.lutergs.upbitclient.api.quotation.orderbook.OrderStep
import dev.lutergs.upbitclient.dto.MarketCode
import dev.lutergs.upbitclient.dto.Markets
import dev.lutergs.upbitclient.webclient.BasicClient
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

class CoinPriceTrackerImpl(
  private val basicClient: BasicClient,
  private val repository: MongoCoinPriceReactiveRepository
): CoinPriceTracker {

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
    return this.repository.findAllByTradeIdOrderByExpireIn5h(buyUUID, Pageable.ofSize(latestA))
      .collectList()
      .flatMap { list -> Mono.fromCallable { Pair(
        list.takeLast(latestA).let { a -> a.sumOf { it.price } / BigDecimal(a.size) },
        list.takeLast(latestB).let { b -> b.sumOf { it.price } / BigDecimal(b.size) }
      ) } }
  }

}