package dev.lutergs.santa.trade

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder

@SpringBootApplication
class TradeAgentApplication

fun main(args: Array<String>) {
    SpringApplicationBuilder(TradeAgentApplication::class.java)
      .listeners(ApplicationContextInjector())
      .run()
}