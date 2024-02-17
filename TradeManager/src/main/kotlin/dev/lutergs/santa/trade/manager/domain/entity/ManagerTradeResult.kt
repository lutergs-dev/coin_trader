package dev.lutergs.santa.trade.manager.domain.entity

import dev.lutergs.santa.util.*
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import java.math.BigDecimal


class ManagerTradeResult(
  buy: OrderResponse,
  sell: OrderResponse? = null,
  sellType: SellType = SellType.NULL
): TradeResult(buy, sell, sellType) {

  private fun isProfit(): Boolean = this.profit
    ?.let { it > BigDecimal.ZERO }
    ?: throw IllegalStateException("거래가 완료되지 않았습니다.")


  fun toInfoString(): String {
    return if (sellType.isFinished()) {
      val profitStr = if (this.isProfit()) "이득" else "손해"
      "[${this.buy.createdAt.toHourAndMinuteString()}] " +
        "${this.coin} ${this.buy.avgPrice().toStrWithStripTrailing()} 에 매수, " +
        "${this.sell?.avgPrice()?.toStrWithStripTrailing()} 에 ${this.sellType.toInfoString()} 매도, " +
        "${this.profit!!.toStrWithStripTrailing()} 원 $profitStr"
    } else {
      // 주문완료되지 않은 것에 대한 String은...?
      "[${this.buy.createdAt.toHourAndMinuteString()}] " +
        "${this.coin} ${this.buy.avgPrice().toStrWithStripTrailing()} 에 매수, " +
        "매도 진행 중"
    }
  }
}
