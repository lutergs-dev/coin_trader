package dev.lutergs.santa.trade.manager.service

import dev.lutergs.santa.trade.manager.domain.AlertMessageSender
import dev.lutergs.santa.trade.manager.domain.TradeResultRepository
import dev.lutergs.santa.trade.manager.domain.entity.ManagerTradeResult
import dev.lutergs.santa.trade.manager.domain.entity.Message
import dev.lutergs.santa.util.SellType
import dev.lutergs.santa.util.toStrWithScale
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneId

class AnalyticService(
  private val tradeResultRepository: TradeResultRepository,
  private val messageSender: AlertMessageSender
) {
  companion object {
    data class OrderFoldDto(
      val profit: BigDecimal,
      val sellTypeCount: MutableMap<SellType, Int>,
      val body: String
    ) {
      fun update(data: ManagerTradeResult): OrderFoldDto = OrderFoldDto(
        profit = this.profit + (data.profit ?: BigDecimal.ZERO),
        sellTypeCount = run {
          this.sellTypeCount.putIfAbsent(data.sellType, 0)
          this.sellTypeCount[data.sellType] = this.sellTypeCount[data.sellType]!! + 1
          this.sellTypeCount
        },
        body = this.body + data.toInfoString() + "\n"
      )

      fun toMessage(hour: Int): Message {
        val total =
          "1차 이득 ${this.sellTypeCount[SellType.PROFIT] ?: 0}번, 손실 ${this.sellTypeCount[SellType.LOSS] ?: 0}번, " +
          "2차 이득 ${this.sellTypeCount[SellType.STOP_PROFIT] ?: 0}번, 손실 ${this.sellTypeCount[SellType.STOP_LOSS] ?: 0}번, " +
          "시간초과 이득 ${this.sellTypeCount[SellType.TIMEOUT_PROFIT] ?: 0}번, 손실 ${this.sellTypeCount[SellType.TIMEOUT_LOSS] ?: 0}번이 있었습니다."
        return Message(
          title = "최근 ${hour}시간 동안 ${this.profit.toStrWithScale()} 원을 벌었습니다.",
          body = "코인 매수/매도 기록은 다음과 같습니다.\n\n${this.body}\n$total"
        )
      }
    }
  }

  fun sendRequestedEarning(lastHour: Int): Mono<String> {
    return OffsetDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(lastHour.toLong())
      .let { this.tradeResultRepository.getAllResultAfterDateTime(it) }
      .filter { it.sellType.isFinished() }
      .collectList()
      .flatMap { orderEntities -> Mono.fromCallable {
        orderEntities
          .sortedBy { it.buy.createdAt }
          .fold(OrderFoldDto(BigDecimal.ZERO, mutableMapOf(), "")) { acc, data -> acc.update(data) }
          .toMessage(lastHour)
      } }.flatMap { this.messageSender.sendMessage(it) }
  }


  fun createChartOfFinishedOrders() {

  }


}