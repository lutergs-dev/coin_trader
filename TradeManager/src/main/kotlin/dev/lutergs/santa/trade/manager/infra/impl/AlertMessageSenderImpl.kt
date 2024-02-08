package dev.lutergs.santa.trade.manager.infra.impl

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.manager.domain.AlertMessageSender
import dev.lutergs.santa.trade.manager.domain.Message
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.*

/**
 * ntfy 를 이용한 alert system
 * */
class AlertMessageSenderImpl(
  baseUrl: String,
  username: String,
  password: String,
  private val objectMapper: ObjectMapper
): AlertMessageSender {
  private val webClient = WebClient.builder()
    .baseUrl(baseUrl)
    .defaultHeader(
      "Authorization",
      Base64.getEncoder()
        .encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        .let { "Basic $it" }
    ).build()
  private val logger = LoggerFactory.getLogger(AlertMessageSenderImpl::class.java)

  override fun sendMessage(msg: Message): Mono<String> {
    return this.webClient
      .post()
      .bodyValue(this.objectMapper.writeValueAsString(Body.fromMessage(msg)))
      .retrieve()
      .bodyToMono(String::class.java)
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

private data class Body (
  @JsonProperty("topic")    val topic: String,
  @JsonProperty("title")    val title: String,
  @JsonProperty("message")  val message: String
) {
  companion object {
    fun fromMessage(msg: Message): Body {
      return Body(msg.topic, msg.title, msg.body)
    }
  }
}