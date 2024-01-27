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
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("HH'시' mm'분'")


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

  fun sendTodayEarning(): Mono<String> {
    return OffsetDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1)
      .let { this.tradeHistoryRepository.getTradeHistoryAfter(it) }
      .let { orderEntityFlux ->
        orderEntityFlux
          .filter {
            it.buyPlaceAt != null &&
            it.coin != null &&
            it.buyPrice != null &&
            it.sellPrice != null &&
            it.buyFinishAt != null &&
            it.sellFinishAt != null &&
            it.profit != null &&
            it.sellType != null
          }.collectList()
          .flatMap { orderEntities -> Mono.fromCallable {
            orderEntities
              .sortedBy { it.buyPlaceAt!! }
              .joinToString(separator = "\n") {
                val isProfit = (if (it.buyPrice!! < it.sellPrice!!) "이득" else "손해")
                "[${it.buyPlaceAt!!.toLocalDateTime().format(this.dateTimeFormatter)}]" +
                  " ${it.coin!!} ${it.buyPrice!!.toStrWithPoint()} 에 매수," +
                  " ${it.sellPrice!!.toStrWithPoint()} 에 ${it.sellTypeStr()} 매도. ${it.profit!!.toStrWithPoint()} 원 $isProfit"
              }.let { body ->
                val total = orderEntities.groupBy { it.sellType ?: "ERROR" }
                  .let {
                    "이득 ${it["PROFIT"]?.size ?: 0}반, 손실 ${it["LOSS"]?.size ?: 0}번, 시간초과 ${it["TIMEOUT"]?.size ?: 0}번이 있었습니다."
                  }
                Message(
                  topic = this.topicName,
                  title = "최근 24시간 동안 ${orderEntities.sumOf { it.profit ?: 0.0 }.toStrWithPoint()} 원을 벌었습니다.",
                  body = "코인 매수/매도기록은 다음과 같습니다.\n\n$body\n\n$total"
                )
              }
          } }.flatMap { this.messageSender.sendMessage(it) }
      }
  }

  fun sendAllCoinsAreDangerous(): Mono<String> {
    return this.messageSender.sendMessage(
      Message(this.topicName, "모든 코인이 위험합니다.", "모든 코인이 위험합니다. 업비트를 확인 후 조치를 취해주세요.")
    )
  }
}