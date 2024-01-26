package dev.lutergs.santa.trade

import org.junit.jupiter.api.Test

class TradeLogicTest {

  @Test
  fun calculateNewLogic() {
    val data = listOf(100.0, 97.8, 95.34, 98.23, 100.1, 102.42, 103.42, 102.3, 103.2)
    println("${newCalculateRSI(data)}, ${oldCalculateRSI(data, data.size / 2)}")
  }


  private fun newCalculateRSI(prices: List<Double>, period: Int = prices.size / 2): Double {
    return prices
      .windowed(2, 1, false) { it[1] - it[0] }
      .let { changes ->
        val sma = changes.subList(0, period)
          .let { smaPeriod -> Pair(
            smaPeriod.filter { it > 0 }.sum() / smaPeriod.size,
            smaPeriod.filter { it < 0 }.sum() / smaPeriod.size * -1.0
          ) }
        val smoothing = 2.0 / (period + 1).toDouble()
        changes.subList(period, changes.size)
          .fold(sma) { ema, change ->
            when {
              change > 0 -> Pair(
                (change * smoothing) + (ema.first * (1.0 - smoothing)), ema.second * (1 - smoothing))
              change < 0 -> Pair(
                ema.first * (1 - smoothing), ((change * -1.0) * smoothing) + (ema.second * (1.0 - smoothing)))
              else -> Pair(ema.first * (1 - smoothing), ema.second * (1 - smoothing))
            }
          }.let {
            (if (it.second == 0.0) { 0.0 } else { it.first / it.second })
              .let { rs -> 100.0 - (100.0 / (1 + rs))}
          }
      }
  }

  private fun oldCalculateRSI(prices: List<Double>, period: Int): Double {
    var averageGain = 0.0
    var averageLoss = 0.0

    // 첫 번째 기간 동안의 평균 상승 및 하락 계산
    for (i in 1 until period) {
      val change = prices[i] - prices[i - 1]
      if (change > 0) {
        averageGain += change
      } else {
        averageLoss -= change
      }
    }

    averageGain /= period
    averageLoss /= period

    // 나머지 기간에 대한 평균 상승 및 하락 업데이트
    for (i in period until prices.size) {
      val change = prices[i] - prices[i - 1]
      if (change > 0) {
        averageGain = (averageGain * (period - 1) + change) / period
        averageLoss = (averageLoss * (period - 1)) / period
      } else {
        averageGain = (averageGain * (period - 1)) / period
        averageLoss = (averageLoss * (period - 1) - change) / period
      }
    }

    val rs = averageGain / averageLoss
    val rsi = 100 - (100 / (1 + rs))

    return rsi
  }
}