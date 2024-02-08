package dev.lutergs.santa.trade.worker.domain

import dev.lutergs.santa.trade.worker.domain.entity.DangerCoinMessage
import dev.lutergs.santa.trade.worker.domain.entity.TradeResult
import dev.lutergs.santa.trade.worker.domain.entity.TradeResultMessage
import dev.lutergs.santa.trade.worker.infra.KafkaMessageResponse
import dev.lutergs.santa.universal.oracle.SellType
import dev.lutergs.upbitclient.dto.MarketCode
import reactor.core.publisher.Mono
import java.math.BigDecimal

interface MessageSender {
  fun sendAlarm(msg: DangerCoinMessage): Mono<KafkaMessageResponse>

  fun sendTradeResult(msg: TradeResultMessage): Mono<KafkaMessageResponse>
}

interface Trader {
  // 현재 보유한 코인을 시장가로 판매
  fun sellMarket(tradeResult: TradeResult, sellType: SellType): Mono<TradeResult>

  // 코인을 시장가로 구매 후 TradeResult 반환
  fun buyMarket(market: MarketCode, money: BigDecimal): Mono<TradeResult>

  // 지정가 매도 주문 실행
  fun placeSellLimit(tradeResult: TradeResult, price: BigDecimal): Mono<TradeResult>

  // 지정가 매도 주문이 완료되었을 때 데이터 기록
  fun finishSellLimit(tradeResult: TradeResult): Mono<TradeResult>

  // 지정가 매도 주문 취소
  fun cancelSellLimit(tradeResult: TradeResult): Mono<TradeResult>

  // 지정가 매수 주문
  fun buyLimit(market: MarketCode, volume: BigDecimal, price: BigDecimal): Mono<TradeResult>
}