package dev.lutergs.santa.trade.domain

import dev.lutergs.upbitclient.dto.MarketCode
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

interface DangerCoinRepository {
  fun setDangerCoin(coinName: String): Mono<String>
  fun getDangerCoins(): Flux<String>
}

interface TradeHistoryRepository {
  fun getTradeHistoryBetweenDatetime(startAt: OffsetDateTime, endAt: OffsetDateTime): Flux<CompleteOrderResult>
  fun getTradeHistoryAfter(datetime: OffsetDateTime): Flux<CompleteOrderResult>
}

interface AlertMessageSender {
  fun sendMessage(msg: Message): Mono<String>
}

interface WorkerController {
  fun initWorker(workerConfig: WorkerConfig, market: MarketCode, price: Int): Boolean
}