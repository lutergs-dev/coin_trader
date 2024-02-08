package dev.lutergs.santa.trade.manager.infra.impl

import dev.lutergs.santa.trade.manager.domain.KubernetesInfo
import dev.lutergs.santa.trade.manager.domain.WorkerConfig
import dev.lutergs.santa.trade.manager.domain.WorkerController
import dev.lutergs.santa.universal.util.Util
import dev.lutergs.santa.universal.util.toStrWithScale
import dev.lutergs.upbitclient.dto.MarketCode
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WorkerControllerImpl(
  private val kubernetesInfo: KubernetesInfo
): WorkerController {
  private val api = BatchV1Api()
  private val logger = LoggerFactory.getLogger(this::class.java)
  override fun initWorker(workerConfig: WorkerConfig, market: MarketCode, price: Long): Boolean {
    val generatedStr = Util.generateRandomString()
    return V1Job()
      .apiVersion("batch/v1")
      .kind("Job")
      .metadata(
        V1ObjectMeta()
          .name("coin-trade-worker-${generatedStr}")
          .namespace(this.kubernetesInfo.namespace)
      )
      .spec(
        V1JobSpec()
          .backoffLimit(3)
          .ttlSecondsAfterFinished(600)
          .template(
            V1PodTemplateSpec()
              .metadata(
                V1ObjectMeta()
                  .name("coin-trade-worker-${generatedStr}")
                  .namespace(this.kubernetesInfo.namespace)
                  .labels(mapOf(Pair("sidecar.istio.io/inject", "false")))
              ).spec(
                V1PodSpec()
                  .restartPolicy("Never")
                  .imagePullSecrets(
                    listOf(V1LocalObjectReference()
                      .name(this.kubernetesInfo.imagePullSecretName)
                    )
                  ).containers(
                    listOf(
                      V1Container()
                        .name("coin-trade-worker")
                        .image(this.kubernetesInfo.imageName)
                        .imagePullPolicy(this.kubernetesInfo.imagePullPolicy)
                        .envFrom(
                          listOf(V1EnvFromSource()
                            .secretRef(V1SecretEnvSource().name(this.kubernetesInfo.envSecretName))
                          )
                        ).env(
                          listOf(
                            V1EnvVar().name("PHASE_1_WAIT_MINUTE").value(workerConfig.phase1.waitMinute.toString()),
                            V1EnvVar().name("PHASE_1_PROFIT_PERCENT").value(workerConfig.phase1.profitPercent.toStrWithScale(2)),
                            V1EnvVar().name("PHASE_1_LOSS_PERCENT").value(workerConfig.phase1.lossPercent.toStrWithScale(2)),
                            V1EnvVar().name("PHASE_2_WAIT_MINUTE").value(workerConfig.phase2.waitMinute.toString()),
                            V1EnvVar().name("PHASE_2_PROFIT_PERCENT").value(workerConfig.phase2.profitPercent.toStrWithScale(2)),
                            V1EnvVar().name("PHASE_2_LOSS_PERCENT").value(workerConfig.phase2.lossPercent.toStrWithScale(2)),
                            V1EnvVar().name("START_MARKET").value(market.toString()),
                            V1EnvVar().name("START_MONEY").value(price.toString()),
                            V1EnvVar().name("APP_ID").value(generatedStr)
                          )
                        )
                    )
                  )
              )
          )
      )
      .let {
        try {
          this.api.createNamespacedJob(this.kubernetesInfo.namespace, it, null, null, null, null)
          true
        } catch (e: ApiException) {
          this.logger.error("kubernetes worker 기동 시 에러 발생!")
          this.logger.error("body : ${e.responseBody}")
          this.logger.error("headers: ${e.responseHeaders}")
          this.logger.error("message: ${e.localizedMessage}")
          false
        }
      }
  }
}