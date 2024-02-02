package dev.lutergs.santa.trade.worker.domain.entity

import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.dto.MarketCode

data class MainTrade(
  val market: MarketCode,
  val money: Int
)

enum class SellType {
  PROFIT, LOSS, TIMEOUT, STOP_LOSS, STOP_PROFIT, NULL
}

data class Phase(
  val phase1: Phase1,
  val phase2: Phase2
) {
  fun totalWaitMinute(): Long {
    return phase1.waitMinute + phase2.waitMinute
  }
}

data class Phase1(
  val waitMinute: Long,
  val profitPercent: Double,
  val lossPercent: Double
) {
  fun getProfitPrice(price: Double): Double {
    return price * (1.0 + (profitPercent * 0.01))
  }

  fun getLossPrice(price: Double): Double {
    return price * (1.0 - (lossPercent * 0.01))
  }
}

data class Phase2(
  val waitMinute: Long,
  val lossPercent: Double
) {
  fun getLossPrice(price: Double): Double {
    return price * (1.0 - (lossPercent * 0.01))
  }
}


data class TradeResult(
  val buy: OrderResponse,
  val sell: OrderResponse? = null,
  val sellResult: SellType = SellType.NULL
) {
  init {
    takeIf { this.buy.isFinished } ?: throw IllegalStateException("매수 주문이 종료되지 않았습니다. 전체 값: $this")
    if (this.sell != null && !this.sell.isFinished) throw IllegalStateException("매도 주문이 종료되지 않았습니다. 전체 값: $this")
  }

  fun sellFinished(response: OrderResponse, sellType: SellType): TradeResult = TradeResult(
    buy = this.buy,
    sell = response,
    sellResult = sellType
  )

  private val earnPrice: Double get() {
    // 매도 주문이 존재하는지 검사
    this.sell ?: throw IllegalStateException("매도 주문이 없습니다. 가격을 계산할 수 없습니다. 전체 값 : $this")
    return this.sell.totalPrice - this.buy.totalPrice - (this.sell.paidFee + this.buy.paidFee)
  }

  fun toMsg(): TradeResultMessage = run {
    this.sell ?: throw IllegalStateException("매도 주문이 없습니다. 메시지를 생성할 수 없습니다. 전체 값 : $this")
    TradeResultMessage(
      TradeResultMsgKey(this.buy.uuid, this.earnPrice),
      TradeResultMsgValue(this.buy, this.sell)
    )
  }
}