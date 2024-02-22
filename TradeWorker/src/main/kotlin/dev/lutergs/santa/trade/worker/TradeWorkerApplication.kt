package dev.lutergs.santa.trade.worker

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan("dev.lutergs.santa")
class TradeWorkerApplication


fun main(args: Array<String>) {
  SpringApplicationBuilder(TradeWorkerApplication::class.java)
    .web(WebApplicationType.NONE)
    .listeners(ApplicationContextInjector())
    .run(*args)
}