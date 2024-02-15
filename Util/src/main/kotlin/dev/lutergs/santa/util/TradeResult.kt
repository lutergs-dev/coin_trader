package dev.lutergs.santa.util

import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import java.math.BigDecimal

open class TradeResult(
  // 완전히 매수 완료된 경우에만 생성되어야 함
  val buy: OrderResponse,
  val sell: OrderResponse? = null,
  val sellType: SellType = SellType.NULL
) {
  val coin = this.buy.market.quote
  val profit: BigDecimal? = if (this.sell != null && this.sellType.isFinished()) {
    this.sell.totalPrice() - this.buy.totalPrice() - (this.sell.paidFee + this.buy.paidFee)
  } else {
    null
  }

  init {
    if (this.sell == null && this.sellType != SellType.NULL) {
      throw IllegalStateException("매도 주문이 존재하지 않지만, 매도 타입이 NULL 이 아닙니다. 설정된 매도 타입 : ${this.sellType.name}")
    }
    if (!this.buy.isFinished()) {
      throw IllegalStateException("매수 주문이 종료되지 않았습니다. 전체 값 : ${this.buy}")
    }
  }
}


enum class SellType {
  PROFIT, LOSS, STOP_LOSS, STOP_PROFIT, TIMEOUT_PROFIT, TIMEOUT_LOSS, PLACED, NULL;

  fun isFinished(): Boolean {
    return when (this) {
      PROFIT, LOSS, STOP_PROFIT, STOP_LOSS, TIMEOUT_PROFIT, TIMEOUT_LOSS -> true
      else -> false
    }
  }

  fun toInfoString(): String {
    return when (this) {
      PROFIT -> "1차 익절"
      LOSS -> "1차 손절"
      STOP_PROFIT -> "2차 익절"
      STOP_LOSS -> "2차 손절"
      TIMEOUT_PROFIT -> "시간초과 익절"
      TIMEOUT_LOSS -> "시간초과 손절"
      PLACED -> TODO()
      NULL -> TODO()
    }
  }
}