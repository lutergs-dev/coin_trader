package dev.lutergs.santa.trade.worker.infra.impl

import dev.lutergs.santa.trade.worker.domain.Manager
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class ManagerImpl(
  managerUrl: String
): Manager {
  private val webClient = WebClient.builder()
    .baseUrl(managerUrl)
    .build()
  private val logger = LoggerFactory.getLogger(ManagerImpl::class.java)
  override fun executeNewWorker(): Mono<Boolean> {
    return this.webClient
      .post()
      .uri { it.path("/trigger-manager").build() }
      .contentType(MediaType.APPLICATION_JSON)
      .retrieve()
      .toBodilessEntity()
      .flatMap { Mono.just(true) }
      .onErrorResume {
        this.logger.error("Manager request 중 에러가 발생했습니다! : $it")
        Mono.just(false)
      }
  }
}