package dev.lutergs.santa.trade.manager.domain.entity

import dev.lutergs.santa.util.*
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.api.quotation.orderbook.OrderStep
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
      "[${this.buy.createdAt.toHourAndMinuteShortStr()} -> " +
        "${this.sell?.trades?.maxOf { it.createdAt }?.toHourAndMinuteShortStr()}] " +
        "${this.coin} " +
        "${this.buy.avgPrice().let { OrderStep.calculateOrderStepPrice(it) }.toStrWithStripTrailing()} -> " +
        "${this.sell?.avgPrice()?.let { OrderStep.calculateOrderStepPrice(it) }?.toStrWithStripTrailing()} , " +
        "${this.buy.totalPrice().toStrWithScale(1)} 중 ${this.profit?.toStrWithScale(1)} 원 $profitStr " +
        "(${this.sellType.toInfoString()})"
    } else {
      // 주문완료되지 않은 것에 대한 String은...?
      "[${this.buy.createdAt.toHourAndMinuteString()}] " +
        "${this.coin} ${this.buy.avgPrice().toStrWithStripTrailing()} 에 매수, " +
        "매도 진행 중"
    }
  }
}
