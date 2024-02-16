package dev.lutergs.santa.trade.worker.domain.entity

import dev.lutergs.santa.util.SellType
import dev.lutergs.santa.util.TradeResult
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.dto.OrderType

class WorkerTradeResult(
  buy: OrderResponse,
  sell: OrderResponse? = null,
  sellType: SellType = SellType.NULL
) : TradeResult(buy, sell, sellType) {

  fun completeSellOrder(sell: OrderResponse, sellType: SellType): WorkerTradeResult {
    if (!sell.isFinished()) throw IllegalStateException("종료되지 않은 매도 주문으로 TradeResult 의 매도주문 기록을 요청했습니다.")
    return WorkerTradeResult(this.buy, sell, sellType)
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