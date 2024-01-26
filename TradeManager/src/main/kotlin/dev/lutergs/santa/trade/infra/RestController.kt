package dev.lutergs.santa.trade.infra

import dev.lutergs.santa.trade.service.AlertService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono

@Configuration
class RestController {

  @Bean
  fun route(
    triggerController: TriggerController
  ) = router {
    accept(MediaType.APPLICATION_JSON).nest {
      POST("/earn/today", triggerController::triggerTodayEarning)
    }
  }
}

@Component
class TriggerController(
  private val alertService: AlertService
) {
  fun triggerTodayEarning(request: ServerRequest): Mono<ServerResponse> {
    return this.alertService.sendTodayEarning()
      .flatMap { ServerResponse.ok().bodyValue(it) }
  }
}