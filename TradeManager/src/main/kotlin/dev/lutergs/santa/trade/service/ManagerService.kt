package dev.lutergs.santa.trade.service

import dev.lutergs.santa.trade.domain.DangerCoinRepository
import dev.lutergs.santa.trade.domain.KubernetesInfo
import dev.lutergs.santa.trade.domain.Util
import dev.lutergs.upbitclient.api.quotation.candle.CandleMinuteRequest
import dev.lutergs.upbitclient.api.quotation.market.MarketWarning
import dev.lutergs.upbitclient.api.quotation.ticker.TickerResponse
import dev.lutergs.upbitclient.dto.MarketCode
import dev.lutergs.upbitclient.dto.Markets
import dev.lutergs.upbitclient.webclient.BasicClient
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class ManagerService(
  private val upbitClient: BasicClient,
  private val repository: DangerCoinRepository,
  private val kubernetesInfo: KubernetesInfo,
  private val maxMoney: Int,
  private val minMoney: Int
) {
  private val api = BatchV1Api()
  private val logger = LoggerFactory.getLogger(ManagerService::class.java)

//  init {
//    this.initWorker()
//      .onErrorComplete {
//        if (it is ApiException) {
//          println(it.responseBody)
//          println(it.responseHeaders)
//          println(it.localizedMessage)
//        }
//        it is ApiException
//      }.blockLast()
//  }

  @KafkaListener(topics = ["trade-result"])
  fun consume(record: ConsumerRecord<String, String>) {
    // consume 뜻 --> 거래 완료되었고, 이제 돈이 다시 있다.
    this.initWorker().blockLast()
  }

  private fun initWorker(): Flux<Boolean> {
    return this.upbitClient.account.getAccount()
      .filter { it.currency == "KRW" }
      .next()
      .flatMap { Mono.just(it.balance.roundToInt() - 1000) }
      .flatMapMany { totalRawMoney ->
        val totalMoney = AtomicInteger(totalRawMoney)
        this.findApplicableCoin()
          .flatMap { ticker ->
            if (totalMoney.get() >= this.maxMoney) {
              this.initK8sWorker(ticker.market, this.maxMoney)
                .also { this.logger.info("total money = {}, initiated worker = {}", totalMoney.get(), it) }
              totalMoney.addAndGet(-this.maxMoney)
              Mono.just(true)
            } else if (totalMoney.get() < this.maxMoney && totalMoney.get() >= this.minMoney) {
              this.initK8sWorker(ticker.market, totalMoney.get())
                .also { this.logger.info("total money = {}, initiated worker = {}", totalMoney.get(), it) }
              totalMoney.set(0)
              Mono.just(true)
            } else {
              Mono.just(false)
            }
          }.repeat { totalMoney.get() >= this.minMoney }
      }
  }

  private fun initK8sWorker(market: MarketCode, money: Int): V1Job {
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
          .ttlSecondsAfterFinished(3600)
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
                  .imagePullSecrets(listOf(V1LocalObjectReference()
                    .name(this.kubernetesInfo.imagePullSecretName)
                  ))
                  .containers(listOf(V1Container()
                    .name("coin-trade-worker")
                    .image(this.kubernetesInfo.imageName)
                    .imagePullPolicy(this.kubernetesInfo.imagePullPolicy)
                    .envFrom(listOf(V1EnvFromSource()
                      .secretRef(V1SecretEnvSource().name(this.kubernetesInfo.envSecretName))
                    ))
                    .env(listOf(
                      V1EnvVar().name("START_MARKET").value(market.toString()),
                      V1EnvVar().name("START_MONEY").value(money.toString()),
                      V1EnvVar().name("APP_ID").value(generatedStr)
                    ))
                  ))
              )
          )
      )
      .let {
        this.api.createNamespacedJob(this.kubernetesInfo.namespace, it, null, null, null, null)
      }
  }


  private fun findApplicableCoin(): Mono<TickerResponse> {
    return this.upbitClient.market.getMarketCode()
      .filter { it.market.base == "KRW" && it.marketWarning != MarketWarning.CAUTION }
      .flatMap { Mono.just(it.market) }
      .collectList()
      .flatMap { Mono.just(Markets(it)) }
      .flatMapMany { this.upbitClient.ticker.getTicker(it) }
      .sort { o1, o2 -> (o2.accTradePrice24h - o1.accTradePrice24h).roundToInt() }
      .take(25, true)
      .delayElements(Duration.ofMillis(200))
      .flatMap {
        this.getPriorityAndTradeable(it)
          .flatMap { priorityAndTradable ->
            if (priorityAndTradable.t2) Mono.just(Pair(it, priorityAndTradable.t1))
            else Mono.empty()
          }
      }.sort { o1, o2 -> (o2.second - o1.second).roundToInt() }
      .take(15)
      .collectList()
      .flatMap { coinList ->
        this.repository.getDangerCoins()
          .collectList()
          .flatMap { dangerCoinList ->
            coinList
              .filterNot { dangerCoinList.contains(it.first.market.quote) }
              .let { Mono.just(it) }
          }
      }
      .flatMap { Mono.just(it.random().first) }
  }

  private fun getPriorityAndTradeable(ticker: TickerResponse): Mono<Tuple2<Double, Boolean>> {
    /**
     * BID ASK (bid 가 사는거, ask 가 파는거)
     * 호가를 기준으로, 1호가부터 가중치를 둬서 비교 판단
     * */
    val priority = this.upbitClient.orderBook.getOrderBook(Markets(listOf(ticker.market)))
      .next()
      .flatMap { orderbook ->
        val length = orderbook.orderbookUnits.size
        orderbook.orderbookUnits
          .mapIndexed { idx, data -> (data.bidSize - data.askSize) * ((length - idx) / length) }
          .sum()
          .let { Mono.just(it) }
      }

    /**
     * 최근부터 3시간 동안의 데이터를 확인해, 다음과 같은 기준을 따름.
     * 1. 고가가 현재가 기준 2배 이상, 저가가 현재가 기준 80% 이상일 때는 거래하지 않음
     * */
    val isTradable = this.upbitClient.candle.getMinute(CandleMinuteRequest(ticker.market, 3, 60))
      .reduce(Pair(0.0, 0.0)) { acc, candle ->
        Pair(
          if (acc.first < candle.highPrice) candle.highPrice else acc.first,
          if (acc.second > candle.lowPrice) candle.lowPrice else acc.second)
      }.flatMap { Mono.fromCallable {
        // rule 1
        if (it.first >= ticker.tradePrice * 2 && it.second >= ticker.tradePrice * 0.8) false
        else true
      } }

    return Mono.zip(priority, isTradable)
  }

}