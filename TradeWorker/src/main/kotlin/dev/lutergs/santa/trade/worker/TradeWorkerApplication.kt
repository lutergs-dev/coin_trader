package dev.lutergs.santa.trade.worker

import com.influxdb.v3.client.InfluxDBClient
import com.influxdb.v3.client.Point
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.ComponentScans
import java.time.OffsetDateTime

@SpringBootApplication
@AutoConfigurationPackage
@ComponentScans(
  ComponentScan("dev.lutergs.santa.universal")
)
class TradeWorkerApplication


// TODO : Spring Cloud 를 참고해서 lambda 처럼, 단일실행이 가능하도록 변경 필요
fun main(args: Array<String>) {
  SpringApplicationBuilder(TradeWorkerApplication::class.java)
    .listeners(ApplicationContextInjector())
    .run()
}