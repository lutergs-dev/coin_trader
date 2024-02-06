package dev.lutergs.santa.trade.worker.domain.entity

import dev.lutergs.upbitclient.dto.MarketCode

data class MainTrade(
  val market: MarketCode,
  val money: Int
)

enum class SellType {
  PROFIT, LOSS, STOP_LOSS, STOP_PROFIT, TIMEOUT_PROFIT, TIMEOUT_LOSS, PLACED, NULL
}

data class TradePhase(
  val phase1: Phase,
  val phase2: Phase
) {
  fun totalWaitMinute(): Long {
    return phase1.waitMinute + phase2.waitMinute
  }
}

data class Phase(
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


