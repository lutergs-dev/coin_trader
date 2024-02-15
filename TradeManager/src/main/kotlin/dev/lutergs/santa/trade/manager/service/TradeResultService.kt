package dev.lutergs.santa.trade.manager.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.manager.domain.TradeResultRepository
import dev.lutergs.santa.trade.manager.domain.entity.ManagerTradeResult
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener

class TradeResultService(
  private val tradeResultRepository: TradeResultRepository,
  private val objectMapper: ObjectMapper
) {

  // key 는 매수주문 UUID, value 는 complete set of CompleteTradeResult
  @KafkaListener(topics = ["\${custom.kafka.topic.trade-result}"])
  fun consume(record: ConsumerRecord<String, String>) {
    record.value()
      .let { this.objectMapper.readValue(it, ManagerTradeResult::class.java) }
      .let { this.tradeResultRepository.save(it) }
      .block()
  }
}