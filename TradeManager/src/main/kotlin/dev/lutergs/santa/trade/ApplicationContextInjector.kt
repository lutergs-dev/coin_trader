package dev.lutergs.santa.trade

import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.util.Config
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

    // set kubernetes default client
    this.setKubernetesClient(environment)

    this.logger.info(String.format("Inject variable info to Spring complete! current env is %s", envName))
    this.printCurrentProperties(environment)
  }

  private fun setKubernetesClient(environment: ConfigurableEnvironment) {
    try {
      environment.getProperty("custom.kubernetes.kube-config-location")
        ?.run { Config.fromConfig(this)
          .run { Configuration.setDefaultApiClient(this) }
        } ?: this.logger.info("kubernetes kubeconfig 파일이 존재하지 않습니다. Kubernetes 를 설정하지 않습니다.")
    } catch (e: IllegalArgumentException) {
      this.logger.error("kubeconfig placeholder 를 resolve 하지 못했습니다. Kubernetes 를 설정하지 않습니다.")
    }
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