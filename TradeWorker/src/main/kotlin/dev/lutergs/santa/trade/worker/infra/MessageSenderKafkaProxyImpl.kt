package dev.lutergs.santa.trade.worker.infra

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.worker.domain.MessageSender
import dev.lutergs.santa.trade.worker.domain.entity.DangerCoinMessage
import dev.lutergs.santa.trade.worker.domain.entity.TradeResultMessage
import dev.lutergs.santa.universal.mongo.DangerCoinRepository
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.*

class MessageSenderKafkaProxyImpl(
  kafkaProxyUrl: String,
  kafkaClusterName: String,
  kafkaApiKey: String,
  kafkaApiSecret: String,
  private val alarmTopicName: String,
  private val tradeResultTopicName: String,
  private val objectMapper: ObjectMapper,
  private val dangerCoinRepository: DangerCoinRepository
): MessageSender {
  private val webClient = WebClient.builder()
    .baseUrl("$kafkaProxyUrl/kafka/v3/clusters/$kafkaClusterName/topics")
    .defaultHeader("Content-Type", "application/json")
    .defaultHeader(
      "Authorization",
      Base64.getEncoder()
        .encodeToString("$kafkaApiKey:$kafkaApiSecret".toByteArray(Charsets.UTF_8))
        .let { "Basic $it" }
    ).build()
  private val logger = LoggerCreate.createLogger(this::class)

  override fun sendAlarm(msg: DangerCoinMessage): Mono<KafkaMessageResponse> {
    return this.dangerCoinRepository.setDangerCoin(msg.key.coinName)
      .then(this.sendPost("/${this.alarmTopicName}/records", KafkaMessage(msg.key, msg.value)))
  }

  override fun sendTradeResult(msg: TradeResultMessage): Mono<KafkaMessageResponse> {
    return this.sendPost("/${this.tradeResultTopicName}/records", KafkaMessage(msg.key, msg.value))
  }

  private fun sendPost(path: String, body: KafkaMessage): Mono<KafkaMessageResponse> {
    return this.webClient
      .post()
      .uri { it.path(path).build() }
      .bodyValue(body.toJsonString(this.objectMapper))
      .retrieve()
      .bodyToMono(KafkaMessageResponse::class.java)
      .retryWhen(Retry.fixedDelay(5, Duration.ofSeconds(1)))
      .doOnError {
        if (it is WebClientResponseException) {
          this.logger.error("Response Exception occured. response code : ${it.statusCode}, response body: ${it.responseBodyAsString}")
        } else {
          this.logger.error("error occured", it)
        }
      }
  }
}

data class KafkaMessage(
  val key: Any,
  val value: Any
) {
  fun toJsonString(mapper: ObjectMapper): String {
    return "{\"key\":{\"type\":\"JSON\",\"data\":${mapper.writeValueAsString(this.key)}}," +
      "\"value\":{\"type\":\"JSON\",\"data\":${mapper.writeValueAsString(this.value)}}}"
  }
}

data class KafkaMessageResponse (
  @JsonProperty("error_code") val errorCode: Int,
  @JsonProperty("cluster_id") val clusterId: String,
  @JsonProperty("topic_name") val topicName: String,
  @JsonProperty("partition_id") val partitionId: Int,
  @JsonProperty("offset") val offset: Int,
  @JsonProperty("timestamp") val timestamp: String, // TODO: 차후 deserializer 만들기
  @JsonProperty("key") val key: KafkaMessageKVResponse,
  @JsonProperty("value") val value: KafkaMessageKVResponse
)

data class KafkaMessageKVResponse (
  @JsonProperty("type") val type: String,
  @JsonProperty("size") val size: Int
)