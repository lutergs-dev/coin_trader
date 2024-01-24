package dev.lutergs.santa.trade.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.domain.DangerCoinRepository
import dev.lutergs.santa.trade.domain.AlertMessageSender
import dev.lutergs.santa.trade.domain.Message
import dev.lutergs.santa.trade.domain.TradeHistoryRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.scheduling.annotation.Scheduled
import reactor.core.publisher.Mono
import java.time.*

class AlertService(
  private val dangerCoinRepository: DangerCoinRepository,
  private val tradeHistoryRepository: TradeHistoryRepository,
  private val messageSender: AlertMessageSender,
  private val objectMapper: ObjectMapper,
  private val topicName: String
) {

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

  @Scheduled(cron = "0 0 20 * * *")
  fun batchTodayEarning() {
    this.sendTodayEarning().block()
  }

  fun sendTodayEarning(): Mono<String> {
    return LocalDateTime.of(
      LocalDate.now(ZoneId.of("Asia/Seoul")),
      LocalTime.of(20, 0, 0)
    ).let {
      this.tradeHistoryRepository.getTradeHistoryBetweenDatetime(
        it.minusDays(1).atOffset(ZoneOffset.ofHours(9)),
        it.atOffset(ZoneOffset.ofHours(9))
      )
    }.let { Message.createProfitMessage(it, this.topicName) }
      .flatMap { this.messageSender.sendMessage(it) }
  }

  fun sendAllCoinsAreDangerous(): Mono<String> {
    return this.messageSender.sendMessage(
      Message(this.topicName, "모든 코인이 위험합니다.", "모든 코인이 위험합니다. 업비트를 확인 후 조치를 취해주세요.")
    )
  }
}