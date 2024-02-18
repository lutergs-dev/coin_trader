package dev.lutergs.santa.trade.manager.infra.controller

import dev.lutergs.santa.trade.manager.service.AnalyticService
import dev.lutergs.santa.trade.manager.service.DangerControlService
import dev.lutergs.santa.trade.manager.service.ManagerService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.queryParamOrNull
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono

@Configuration
class RestController {

  @Bean
  fun route(
    triggerController: TriggerController
  ) = router {
    accept(MediaType.APPLICATION_JSON).nest {
      POST("/trigger-hour-analytic", triggerController::triggerTodayEarning)
      POST("/trigger-manager", triggerController::triggerWorkerInit)
    }
  }
}

@Component
class TriggerController(
  private val analyticService: AnalyticService,
  private val managerService: ManagerService
) {

  // TODO : request 파라미터 추가
  fun triggerTodayEarning(request: ServerRequest): Mono<ServerResponse> {
    return this.analyticService.sendRequestedEarning(request.queryParamOrNull("hour")?.toInt() ?: 24)
      .flatMap { ServerResponse.ok().bodyValue(it) }
  }

  fun triggerWorkerInit(request: ServerRequest): Mono<ServerResponse> {
    return this.managerService.triggerManager()
      .flatMap { ServerResponse.ok().bodyValue(it) }
  }
}