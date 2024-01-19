package dev.lutergs.santa.trade.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.domain.DangerCoinRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class DangerCoinSetService(
  private val repository: DangerCoinRepository,
  private val objectMapper: ObjectMapper
) {

  @KafkaListener(topics = ["danger-coin-alert"])
  fun consume(record: ConsumerRecord<String, String>) {
    this.objectMapper.readTree(record.key())
      .path("coinName").asText()
      .let { this.repository.setDangerCoin(it) }
      .block()
  }
}