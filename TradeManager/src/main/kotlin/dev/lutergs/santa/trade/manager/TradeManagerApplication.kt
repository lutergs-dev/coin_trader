package dev.lutergs.santa.trade.manager

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.ComponentScan


@SpringBootApplication
@ComponentScan("dev.lutergs.santa")
class TradeManagerApplication

fun main(args: Array<String>) {
  SpringApplicationBuilder(TradeManagerApplication::class.java)
    .listeners(ApplicationContextInjector())
    .run()
}