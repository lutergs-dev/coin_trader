package dev.lutergs.santa.trade.worker

import dev.lutergs.santa.trade.worker.infra.LoggerCreate
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.PropertySource
import java.util.*

class ApplicationContextInjector: ApplicationListener<ApplicationEnvironmentPreparedEvent> {
  private val logger = LoggerFactory.getLogger(this.javaClass)
  override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
    // get settings from env
    val environment = event.environment
    val envName = environment.getProperty("spring.profiles.active")

    // set current timezone to Seoul
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))

    // set logger
    LoggerCreate.setAppName(environment.getProperty("custom.id") ?: "WORKER_UNKNOWN_ID")

    this.logger.info(String.format("Inject variable info to Spring complete! current env is %s", envName))
//    this.printCurrentProperties(environment)
  }

  private fun printCurrentProperties(environment: ConfigurableEnvironment) {
    environment.propertySources.stream()
      .filter { propertySource: PropertySource<*>? -> propertySource is EnumerablePropertySource<*> }
      .map { propertySource: PropertySource<*> -> (propertySource as EnumerablePropertySource<*>).propertyNames }
      .flatMap(Arrays::stream)
      .sorted()
      .forEach { propName: String? ->
        System.out.printf("\t\t%s : %s%n", propName, environment.getProperty(propName!!))
      }
  }
}