package dev.lutergs.santa.trade.infra

import dev.lutergs.santa.trade.domain.KubernetesInfo
import dev.lutergs.upbeatclient.webclient.BasicClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpringConfiguration {

  @Bean
  fun upbitClient(
    @Value("\${custom.upbit.access-key}") accessKey: String,
    @Value("\${custom.upbit.secret-key}") secretKey: String
  ): BasicClient = BasicClient(accessKey, secretKey)

  @Bean
  fun kubernetesInfo(
    @Value("\${custom.kubernetes.namespace}") namespace: String,
    @Value("\${custom.kubernetes.image.pull-secret-name}") imagePullSecretName: String,
    @Value("\${custom.kubernetes.image.pull-policy}") imagePullPolicy: String,
    @Value("\${custom.kubernetes.image.name}") imageName: String,
    @Value("\${custom.kubernetes.env-secret-name}") envSecretName: String
  ): KubernetesInfo {
    return KubernetesInfo(namespace, imagePullSecretName, imagePullPolicy, imageName, envSecretName)
  }
}