package dev.lutergs.santa.trade.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder

@SpringBootApplication
class TradeWorkerApplication

fun main(args: Array<String>) {
  SpringApplicationBuilder(TradeWorkerApplication::class.java)
    .listeners(ApplicationContextInjector())
    .run()
}