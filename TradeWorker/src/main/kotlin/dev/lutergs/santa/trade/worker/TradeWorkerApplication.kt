package dev.lutergs.santa.trade.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan("dev.lutergs.santa")
class TradeWorkerApplication


// TODO : Spring Cloud 를 참고해서 lambda 처럼, 단일실행이 가능하도록 변경 필요
fun main(args: Array<String>) {
  SpringApplicationBuilder(TradeWorkerApplication::class.java)
    .listeners(ApplicationContextInjector())
    .run()
}