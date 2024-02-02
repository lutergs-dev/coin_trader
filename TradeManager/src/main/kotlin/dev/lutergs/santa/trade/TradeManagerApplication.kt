package dev.lutergs.santa.trade

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder

@SpringBootApplication
class TradeManagerApplication

fun main(args: Array<String>) {
  SpringApplicationBuilder(TradeManagerApplication::class.java)
    .listeners(ApplicationContextInjector())
    .run()
}