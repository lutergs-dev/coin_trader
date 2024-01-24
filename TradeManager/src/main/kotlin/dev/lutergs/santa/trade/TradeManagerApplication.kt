package dev.lutergs.santa.trade

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class TradeManagerApplication

fun main(args: Array<String>) {
    SpringApplicationBuilder(TradeManagerApplication::class.java)
      .listeners(ApplicationContextInjector())
      .run()
}