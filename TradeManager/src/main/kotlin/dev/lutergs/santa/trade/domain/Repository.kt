package dev.lutergs.santa.trade.domain

import dev.lutergs.santa.trade.infra.impl.OrderEntity
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

interface DangerCoinRepository {

  fun setDangerCoin(coinName: String): Mono<String>

  fun getDangerCoins(): Flux<String>
}

interface TradeHistoryRepository {
  fun getTradeHistoryBetweenDatetime(startAt: OffsetDateTime, endAt: OffsetDateTime): Flux<OrderEntity>
  fun getTradeHistoryAfter(datetime: OffsetDateTime): Flux<OrderEntity>
}

interface AlertMessageSender {
  fun sendMessage(msg: Message): Mono<String>
}