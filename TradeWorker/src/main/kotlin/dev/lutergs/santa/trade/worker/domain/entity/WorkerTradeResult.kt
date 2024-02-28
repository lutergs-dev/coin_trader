package dev.lutergs.santa.trade.worker.domain.entity

import dev.lutergs.santa.util.SellType
import dev.lutergs.santa.util.TradeResult
import dev.lutergs.santa.util.toStrWithStripTrailing
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.dto.OrderType
import java.math.BigDecimal

class WorkerTradeResult: TradeResult {
  constructor(buy: OrderResponse)
    : super(buy, null, SellType.NULL)
  private constructor(buy: OrderResponse, sell: OrderResponse, sellPhase: SellPhase)
    : super(buy, sell, sellPhase.toSellType(TradeResult(buy, sell, SellType.PROFIT))) // TODO : SellType.PROFIT 으로 넣는거 말고 더 나이스한 방법 필요
  private constructor(buy: OrderResponse, sell: OrderResponse, sellType: SellType)
    : super(buy, sell, sellType)

  fun completeSellOrder(sell: OrderResponse, sellPhase: SellPhase): WorkerTradeResult {
    if (!sell.isFinished()) throw IllegalStateException("종료되지 않은 매도 주문으로 TradeResult 의 매도주문 기록을 요청했습니다.")
    return WorkerTradeResult(this.buy, sell, sellPhase)
  }

  fun sellLimitOrderPlaced(sell: OrderResponse): WorkerTradeResult {
    if (sell.orderType == OrderType.LIMIT && !sell.isFinished()) {
      return WorkerTradeResult(this.buy, sell, SellType.PLACED)
    } else {
      throw IllegalStateException("시장가 주문이거나, 종료된 지정가 매도 주문 기록을 진행중인 매도 주문 기록 함수에 요청했습니다.")
    }
  }

  fun cancelSellOrder(): WorkerTradeResult {
    if (this.sellType == SellType.PLACED) {
      return WorkerTradeResult(this.buy)
    } else {
      throw IllegalStateException("매도 주문이 주문을 요청한 상태가 아닙니다.")
    }
  }

}

enum class SellPhase {
  PHASE_1, PHASE_2, TIMEOUT;

  fun toSellType(result: TradeResult): SellType = mapping[this to (result.profit?.let { it > BigDecimal.ZERO } ?: false)]
    ?: throw IllegalStateException("잘못된 값이 들어와 SellType 를 판별할 수 없습니다. WorkerSellType : ${this}, profit : ${result.profit?.toStrWithStripTrailing()}")

  companion object {
    private val mapping: Map<Pair<SellPhase, Boolean>, SellType> = mapOf(
      Pair(PHASE_1, true) to SellType.PROFIT,
      Pair(PHASE_1, false) to SellType.LOSS,
      Pair(PHASE_2, true) to SellType.STOP_PROFIT,
      Pair(PHASE_2, false) to SellType.STOP_LOSS,
      Pair(TIMEOUT, true) to SellType.TIMEOUT_PROFIT,
      Pair(TIMEOUT, false) to SellType.TIMEOUT_LOSS
    )
  }
}