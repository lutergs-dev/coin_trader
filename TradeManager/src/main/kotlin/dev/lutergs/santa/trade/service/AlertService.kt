package dev.lutergs.santa.trade.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.domain.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import reactor.core.publisher.Mono
import java.time.*
import java.time.format.DateTimeFormatter

class AlertService(
  private val dangerCoinRepository: DangerCoinRepository,
  private val tradeHistoryRepository: TradeHistoryRepository,
  private val messageSender: AlertMessageSender,
  private val objectMapper: ObjectMapper,
  private val topicName: String
) {
  data class OrderFoldDto(
    val profit: Double,
    val sellTypeCount: MutableMap<SellType, Int>,
    val body: String
  ) {
    fun update(data: CompleteOrderResult): OrderFoldDto = OrderFoldDto(
      profit = this.profit + (data.profit ?: 0.0),
      sellTypeCount = run {
        this.sellTypeCount.putIfAbsent(data.sellType, 0)
        this.sellTypeCount[data.sellType] = this.sellTypeCount[data.sellType]!! + 1
        this.sellTypeCount
      },
      body = this.body + data.toInfoString() + "\n"
    )
    fun toMessage(topicName: String): Message {
      val total = "1차 이득 ${this.sellTypeCount[SellType.PROFIT] ?: 0}번, 손실 ${this.sellTypeCount[SellType.LOSS] ?: 0}번, " +
        "2차 이득 ${this.sellTypeCount[SellType.STOP_PROFIT] ?: 0} 번, ${this.sellTypeCount[SellType.STOP_LOSS] ?: 0}번, " +
        "시간초과 이득 ${this.sellTypeCount[SellType.TIMEOUT_PROFIT] ?: 0} 번, ${this.sellTypeCount[SellType.TIMEOUT_LOSS] ?: 0}번이 있었습니다."
      return Message(
        topic = topicName,
        title = "최근 24시간 동안 ${this.profit.toStrWithPoint()} 원을 벌었습니다.",
        body = "코인 매수/매도 기록은 다음과 같습니다.\n\n${this.body}\n\n$total"
      )
    }
  }



  @KafkaListener(topics = ["danger-coin-alert"])
  fun consume(record: ConsumerRecord<String, String>) {
    this.objectMapper.readTree(record.key())
      .path("coinName").asText()
      .let { Mono.zip(
        this.dangerCoinRepository.setDangerCoin(it),
        this.messageSender.sendMessage(Message.createDangerCoinMessage(this.topicName, it))
      ) }
      .block()
  }

  fun sendRequestedEarning(lastHour: Int): Mono<String> {
    return OffsetDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(lastHour.toLong())
      .let { this.tradeHistoryRepository.getTradeHistoryAfter(it) }
      .let { orderEntityFlux ->
        orderEntityFlux
          .filter { it.sellType.isFinished() }
          .collectList()
          .flatMap { orderEntities -> Mono.fromCallable {
            orderEntities
            .sortedBy { it.buy.placeAt }
            .fold(OrderFoldDto(0.0, mutableMapOf(), "")) { acc, data -> acc.update(data) }
              .toMessage(this.topicName)
          } }.flatMap { this.messageSender.sendMessage(it) }
      }
  }

  fun sendAllCoinsAreDangerous(): Mono<String> {
    return this.messageSender.sendMessage(
      Message(this.topicName, "모든 코인이 위험합니다.", "모든 코인이 위험합니다. 업비트를 확인 후 조치를 취해주세요.")
    )
  }
}