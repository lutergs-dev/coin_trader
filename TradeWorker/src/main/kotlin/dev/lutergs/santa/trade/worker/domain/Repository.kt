package dev.lutergs.santa.trade.worker.domain

import dev.lutergs.santa.trade.worker.domain.entity.DangerCoinMessage
import dev.lutergs.santa.trade.worker.domain.entity.SellType
import dev.lutergs.santa.trade.worker.domain.entity.TradeResult
import dev.lutergs.santa.trade.worker.domain.entity.TradeResultMessage
import dev.lutergs.santa.trade.worker.infra.KafkaMessageResponse
import dev.lutergs.upbitclient.dto.MarketCode
import reactor.core.publisher.Mono

interface MessageSender {
  fun sendAlarm(msg: DangerCoinMessage): Mono<KafkaMessageResponse>

  fun sendTradeResult(msg: TradeResultMessage): Mono<KafkaMessageResponse>
}

interface Trader {
  fun sellMarket(tradeResult: TradeResult, sellType: SellType): Mono<TradeResult>
  fun buyMarket(market: MarketCode, money: Double): Mono<TradeResult>
  fun placeSellLimit(tradeResult: TradeResult, price: Double): Mono<TradeResult>
  fun finishSellOrder(tradeResult: TradeResult): Mono<TradeResult>
  // completeSellLimit 도 있어야함
  fun cancelSellLimit(tradeResult: TradeResult): Mono<TradeResult>
  fun buyLimit(market: MarketCode, volume: Double, price: Double): Mono<TradeResult>
}