package dev.lutergs.santa.trade.manager.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.manager.domain.*
import dev.lutergs.santa.universal.mongo.DangerCoinRepository
import dev.lutergs.santa.universal.oracle.SellType
import dev.lutergs.santa.universal.util.toStrWithScale
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.*

class AlertService(
  private val dangerCoinRepository: DangerCoinRepository,
  private val completeOrderResultRepository: CompleteOrderResultRepository,
  private val messageSender: AlertMessageSender,
  private val objectMapper: ObjectMapper,
  private val topicName: String
) {
  companion object {
    data class OrderFoldDto(
      val profit: BigDecimal,
      val sellTypeCount: MutableMap<SellType, Int>,
      val body: String
    ) {
      fun update(data: CompleteOrderResult): OrderFoldDto = OrderFoldDto(
        profit = this.profit + (data.profit ?: BigDecimal.ZERO),
        sellTypeCount = run {
          this.sellTypeCount.putIfAbsent(data.sellType, 0)
          this.sellTypeCount[data.sellType] = this.sellTypeCount[data.sellType]!! + 1
          this.sellTypeCount
        },
        body = this.body + data.toInfoString() + "\n"
      )

      fun toMessage(topicName: String): Message {
        val total =
          "1차 이득 ${this.sellTypeCount[SellType.PROFIT] ?: 0}번, 손실 ${this.sellTypeCount[SellType.LOSS] ?: 0}번, " +
            "2차 이득 ${this.sellTypeCount[SellType.STOP_PROFIT] ?: 0}번, 손실 ${this.sellTypeCount[SellType.STOP_LOSS] ?: 0}번, " +
            "시간초과 이득 ${this.sellTypeCount[SellType.TIMEOUT_PROFIT] ?: 0}번, ${this.sellTypeCount[SellType.TIMEOUT_LOSS] ?: 0}번이 있었습니다."
        return Message(
          topic = topicName,
          title = "최근 24시간 동안 ${this.profit.toStrWithScale()} 원을 벌었습니다.",
          body = "코인 매수/매도 기록은 다음과 같습니다.\n\n${this.body}\n\n$total"
        )
      }
    }
  }

  @KafkaListener(topics = ["\${custom.kafka.topic.danger-coin}"])
  fun consume(record: ConsumerRecord<String, String>) {
    this.objectMapper.readTree(record.key())
      .path("coinName").asText()
      .let { this.messageSender.sendMessage(Message.createDangerCoinMessage(this.topicName, it)) }
      .block()
  }

  fun sendRequestedEarning(lastHour: Int): Mono<String> {
    return OffsetDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(lastHour.toLong())
      .let { this.completeOrderResultRepository.getCompleteOrderResultAfter(it) }
      .filter { it.sellType.isFinished() }
      .collectList()
      .flatMap { orderEntities -> Mono.fromCallable {
        orderEntities
        .sortedBy { it.buy.placeAt }
        .fold(OrderFoldDto(BigDecimal.ZERO, mutableMapOf(), "")) { acc, data -> acc.update(data) }
          .toMessage(this.topicName)
      } }.flatMap { this.messageSender.sendMessage(it) }
  }


  fun sendAllCoinsAreDangerous(): Mono<String> {
    return this.messageSender.sendMessage(
      Message(this.topicName, "모든 코인이 위험합니다.", "모든 코인이 위험합니다. 업비트를 확인 후 조치를 취해주세요.")
    )
  }
}