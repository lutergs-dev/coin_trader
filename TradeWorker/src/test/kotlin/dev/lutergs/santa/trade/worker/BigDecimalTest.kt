package dev.lutergs.santa.trade.worker

import dev.lutergs.santa.trade.worker.domain.entity.MainTrade
import dev.lutergs.santa.trade.worker.domain.entity.Phase
import dev.lutergs.santa.trade.worker.domain.entity.TradePhase
import dev.lutergs.upbitclient.dto.MarketCode
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BigDecimalTest {

  @Test
  fun `BigDecimal 연산 검증`() {
    val test = BigDecimal(12.40)
    val profit = BigDecimal(1.5)
    println(test * (BigDecimal(1) + (BigDecimal(0.01) * profit)))

    val test2 = BigDecimal("12.5245")
    val test3 = BigDecimal(125.00)
    println(test2.stripTrailingZeros())
    println(test3.stripTrailingZeros())
  }
}