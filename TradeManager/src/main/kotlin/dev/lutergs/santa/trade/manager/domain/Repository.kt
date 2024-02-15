package dev.lutergs.santa.trade.manager.domain

import dev.lutergs.santa.trade.manager.domain.entity.*
import dev.lutergs.upbitclient.dto.MarketCode
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

interface TradeResultRepository {
  fun save(t: ManagerTradeResult): Mono<ManagerTradeResult>
  fun findByBuyUUID(uuid: UUID): Mono<ManagerTradeResult>
  fun getAllResultAfterDateTime(datetime: OffsetDateTime): Flux<ManagerTradeResult>
}

interface AlertMessageSender {
  fun sendMessage(msg: Message): Mono<String>
}

interface WorkerController {
  fun initWorker(workerConfig: WorkerConfig, market: MarketCode, price: Long): Boolean
}

interface DangerCoinRepository {
  fun setDangerCoin(coinName: String): Mono<DangerCoin>
  fun getDangerCoins(): Flux<DangerCoin>
}