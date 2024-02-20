package dev.lutergs.santa.trade.worker.domain

import dev.lutergs.santa.trade.worker.domain.entity.DangerCoinMessage
import dev.lutergs.santa.trade.worker.domain.entity.TradeResultMessage
import dev.lutergs.santa.trade.worker.domain.entity.WorkerTradeResult
import dev.lutergs.santa.trade.worker.infra.KafkaMessageResponse
import dev.lutergs.santa.util.SellType
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.dto.MarketCode
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.UUID

interface MessageSender {
  fun sendAlarm(msg: DangerCoinMessage): Mono<KafkaMessageResponse>

  fun sendTradeResult(msg: TradeResultMessage): Mono<KafkaMessageResponse>
}

interface Trader {
  // 현재 보유한 코인을 시장가로 판매
  fun sellMarket(wtr: WorkerTradeResult, sellType: SellType): Mono<WorkerTradeResult>

  // 코인을 시장가로 구매 후 TradeResult 반환
  fun buyMarket(market: MarketCode, money: BigDecimal): Mono<WorkerTradeResult>

  // 지정가 매도 주문 실행
  fun placeSellLimit(wtr: WorkerTradeResult, price: BigDecimal): Mono<WorkerTradeResult>
  fun getSellLimitStatus(wtr: WorkerTradeResult): Mono<OrderResponse>

  // 지정가 매도 주문이 완료되었을 때 데이터 기록
  fun finishSellLimit(wtr: WorkerTradeResult): Mono<WorkerTradeResult>

  // 지정가 매도 주문 취소
  fun cancelSellLimit(wtr: WorkerTradeResult): Mono<WorkerTradeResult>

  // 지정가 매수 주문
  fun buyLimit(market: MarketCode, volume: BigDecimal, price: BigDecimal): Mono<WorkerTradeResult>
}

interface Manager {
  fun executeNewWorker(): Mono<Boolean>
}

interface CoinPriceTracker {
  fun getCoinCurrentPrice(workerTradeResult: WorkerTradeResult): Mono<BigDecimal>
  fun getCoinEMA(workerTradeResult: WorkerTradeResult): Mono<BigDecimal>
  fun getAvgOfLatestN(buyUUID: UUID, latestN: Int): Mono<BigDecimal>
  fun getAvgOfLatestAB(buyUUID: UUID, latestA: Int, latestB: Int): Mono<Pair<BigDecimal, BigDecimal>>
  fun cleanUp(buyUUID: UUID): Mono<Void>
}