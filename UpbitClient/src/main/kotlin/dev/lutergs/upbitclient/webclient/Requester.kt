package dev.lutergs.upbitclient.webclient

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.lutergs.upbitclient.api.Param
import dev.lutergs.upbitclient.dto.*
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.web.reactive.function.client.ClientResponse.Headers
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

class Requester(
  baseUrl: String,
  private val tokenGenerator: TokenGenerator
) {
  private val objectMapper = ObjectMapper()
    .registerModules(
      JavaTimeModule(),
      SimpleModule()
        .addSerializer(OrderType::class.java, OrderTypeSerializer())
        .addDeserializer(OrderType::class.java, OrderTypeDeserializer())
        .addSerializer(OrderSide::class.java, OrderSideSerializer())
        .addDeserializer(OrderSide::class.java, OrderSideDeserializer())
        .addSerializer(MarketCode::class.java, MarketCodeSerializer())
        .addDeserializer(MarketCode::class.java, MarketCodeDeserializer())
    ).registerKotlinModule()

  // TODO : 차후 Spring Webclient 의존성 제거 및 netty HttpClient 버전으로 재작성
  private val webClient = ExchangeStrategies.builder()
    .codecs { it.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(this.objectMapper)) }
    .build()
    .let { WebClient.builder().exchangeStrategies(it) }
    .baseUrl(baseUrl)
    .defaultHeader("Content-Type", "application/json")
    .build()

  fun postTest(path: String, param: Param? = null): Mono<String> {
    return this.webClient.post()
      .uri { uri ->
        uri.path(path)
          .let { builder ->
            when {
              param == null -> builder
              else -> builder.query(param.toParameterString())
            }
          }
          .build()
      }
      .header("Authorization", this.tokenGenerator.createJWT(param))
      .retrieve()
      .bodyToMono(String::class.java)
      .doOnError(WebClientResponseException::class.java) {
        println("error on requesting [${it.request?.method}] ${it.request?.uri}\nresponse: ${it.responseBodyAsString}")
      }
  }

  fun getSingleTest(path: String, param: Param? = null): Mono<String> {
    return this.webClient.get()
      .uri { uri ->
        uri.path(path)
          .let { builder ->
            when {
              param == null -> builder
              else -> builder.query(param.toParameterString())
            }
          }
          .build()
      }
      .header("Authorization", this.tokenGenerator.createJWT(param))
      .retrieve()
      .bodyToMono(String::class.java)
      .doOnError(WebClientResponseException::class.java) {
        println("error on requesting [${it.request?.method}] ${it.request?.uri}\nresponse: ${it.responseBodyAsString}")
      }
  }

  fun <T : Any> getSingle(path: String, param: Param? = null, responseClass: KClass<T>): Mono<T> {
    return this.webClient.get()
      .uri { uri ->
        uri.path(path)
          .let { builder ->
            when {
              param == null -> builder
              else -> builder.query(param.toParameterString())
            }
          }
          .build()
      }
      .header("Authorization", this.tokenGenerator.createJWT(param))
      .retrieve()
      .bodyToMono(responseClass.java)
      .doOnError(WebClientResponseException::class.java) {
        println("error on requesting [${it.request?.method}] ${it.request?.uri}\nresponse: ${it.responseBodyAsString}")
      }
  }

  fun <T : Any> getMany(path: String, param: Param? = null, responseClass: KClass<T>): Flux<T> {
    return this.webClient.get()
      .uri { uri ->
        uri.path(path)
          .let { builder ->
            when {
              param == null -> builder
              else -> builder.query(param.toParameterString())
            }
          }
          .build()
      }
      .header("Authorization", this.tokenGenerator.createJWT(param))
      .retrieve()
      .bodyToFlux(responseClass.java)
      .doOnError(WebClientResponseException::class.java) {
        println("error on requesting [${it.request?.method}] ${it.request?.uri}\nresponse: ${it.responseBodyAsString}")
      }
  }

  fun <T : Any> deleteSingle(path: String, param: Param? = null, responseClass: KClass<T>): Mono<T> {
    return this.webClient.delete()
      .uri { uri ->
        uri.path(path)
          .let { builder ->
            when {
              param == null -> builder
              else -> builder.query(param.toParameterString())
            }
          }
          .build()
      }
      .header("Authorization", this.tokenGenerator.createJWT(param))
      .retrieve()
      .bodyToMono(responseClass.java)
      .doOnError(WebClientResponseException::class.java) {
        println("error on requesting [${it.request?.method}] ${it.request?.uri}\nresponse: ${it.responseBodyAsString}")
      }
  }

  fun <T : Any> postSingle(path: String, param: Param? = null, responseClass: KClass<T>): Mono<T> {
    return this.webClient.post()
      .uri { uri ->
        uri.path(path)
          .let { builder ->
            when {
              param == null -> builder
              else -> builder.query(param.toParameterString())
            }
          }
          .build()
      }
      .header("Authorization", this.tokenGenerator.createJWT(param))
      .retrieve()
      .bodyToMono(responseClass.java)
      .doOnError(WebClientResponseException::class.java) {
        println("error on requesting [${it.request?.method}] ${it.request?.uri}\nresponse: ${it.responseBodyAsString}")
      }
  }

  fun extractRequestLimitOnSecond(headers: Headers): Int {
    return headers.header("Remaining-Req")[0].split(";")[2].split("=")[1].trim().toInt()
  }
}