package dev.lutergs.santa.trade.manager

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.ComponentScans

@SpringBootApplication
@ComponentScans(
  ComponentScan(basePackages = arrayOf("dev.lutergs.santa.universal"))
)
class TradeManagerApplication

fun main(args: Array<String>) {
  SpringApplicationBuilder(TradeManagerApplication::class.java)
    .listeners(ApplicationContextInjector())
    .run()
}