package dev.lutergs.santa.trade.service

import dev.lutergs.santa.trade.domain.DangerCoinRepository
import dev.lutergs.santa.trade.domain.KubernetesInfo
import dev.lutergs.santa.trade.domain.Util
import dev.lutergs.santa.trade.domain.toStrWithPoint
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
import org.springframework.kafka.annotation.KafkaListener
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class ManagerService(
  private val alertService: AlertService,
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
        val workerCount = (totalRawMoney / this.maxMoney)
          .let { if (totalRawMoney % this.maxMoney >= this.minMoney) it + 1 else it }
        val totalMoney = AtomicInteger(totalRawMoney)
        this.findApplicableCoin(workerCount)
          .flatMapMany { tickers ->
            if (tickers.size < workerCount) {
              this.logger.info("${workerCount}개의 Worker 가 가능함에도, " +
                "시장 상태에 따라 ${tickers.size}개의 Worker 만, " +
                "${tickers.joinToString(separator = ",") { it.market.quote }} 코인을 구매합니다.")
            }
            tickers
              .map { it.market }
              .map { market ->
                if (totalMoney.get() >= this.maxMoney) {
                  this.initK8sWorker(market, this.maxMoney)
                    .also { this.logger.info("${market.quote} 를 ${this.maxMoney} 만큼 구매하는 Worker 를 실행시켰습니다." ) }
                  totalMoney.addAndGet(-this.maxMoney)
                } else if (totalMoney.get() < this.maxMoney && totalMoney.get() >= this.minMoney) {
                  this.initK8sWorker(market, totalMoney.get())
                    .also { this.logger.info("${market.quote} 를 ${totalMoney.get()} 만큼 구매하는 Worker 를 실행시켰습니다." ) }
                  totalMoney.set(0)
                }
                Mono.just(true)
              }.let { Flux.concat(it) }
          }.switchIfEmpty(
            this.alertService.sendAllCoinsAreDangerous()
              .flatMap {
                this.logger.warn("모든 코인이 위험한 상태입니다! 조치가 필요합니다.")
                Mono.just(false)
              }
          )
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


  private fun findApplicableCoin(coinCount: Int): Mono<List<TickerResponse>> {
    return this.upbitClient.market.getMarketCode()
      .filter { it.market.base == "KRW" && it.marketWarning != MarketWarning.CAUTION }
      .flatMap { Mono.just(it.market) }
      .collectList()
      .doOnNext { this.logger.info("한화로 거래 가능하며, 위험 상태가 아닌 코인 리스트는 다음과 같습니다 : ${it.map { d -> d.quote }}") }
      .flatMap { Mono.just(Markets(it)) }
      .flatMapMany { this.upbitClient.ticker.getTicker(it) }
      .sort { o1, o2 -> (o2.accTradePrice24h - o1.accTradePrice24h).roundToInt() }
      .take(25, true)
      .delayElements(Duration.ofMillis(200))
      .filterWhen { this.isTradeable(it) }
      .flatMap { ticker -> this.getPriority(ticker)
        .flatMap { p -> Mono.fromCallable { Pair(ticker, p) } }
      }.sort { o1, o2 -> (o2.second - o1.second).roundToInt() }
      .take(15)
      .collectList()
      .flatMap { coinList ->
        this.logger.info("거래 가능한 코인 중, 우선순위에 의거한 상위 15개 코인은 다음과 같습니다 : ${coinList.map { "${it.first.market.quote}(${it.second.toStrWithPoint()})" }}")
        this.repository.getDangerCoins()
          .collectList()
          .flatMap { dangerCoinList ->
            this.logger.info("최근에 너무 가격이 떨어져 24시간동안 거래하지 않을 코인은 다음과 같습니다 : $dangerCoinList")
            coinList
              .filterNot { dangerCoinList.contains(it.first.market.quote) }
              .let { Mono.just(it.map { p -> p.first }) }
          }
      }
      .doOnNext { this.logger.info("최종으로 선발된 후보 코인은 다음과 같습니다 : ${it.map { t -> t.market.quote }}") }
      .flatMap { Mono.just(it.shuffled().take(coinCount)) }
      .doOnNext { this.logger.info("거래할 최종 코인은 ${it.map { d -> d.market.quote }} 입니다.") }
  }

  private fun getPriority(ticker: TickerResponse): Mono<Double> {
    /**
     * BID ASK (bid 가 사는거, ask 가 파는거)
     * 호가를 기준으로, 1호가부터 가중치를 둬서 비교 판단
     * */
    return this.upbitClient.orderBook.getOrderBook(Markets(listOf(ticker.market)))
      .next()
      .flatMap { orderbook ->
        val length = orderbook.orderbookUnits.size
        orderbook.orderbookUnits
          .mapIndexed { idx, data -> (data.bidSize - data.askSize) * ((length - idx) / length) }
          .sum()
          .let { Mono.just(it) }
      }
  }

  /**
   * 최근부터 3시간 동안의 데이터를 확인해, 다음과 같은 기준을 따름.
   * 1. 고가가 현재가 기준 2배 이상, 저가가 현재가 기준 80% 이상일 때는 거래하지 않음
   * 2. RSI 지표가 70 이상인 것은 거래하지 않음.
   * */
  private fun isTradeable(ticker: TickerResponse): Mono<Boolean> {
    return this.upbitClient.candle.getMinute(CandleMinuteRequest(ticker.market, 60, 10))
      .collectList()
      .flatMap { candles ->
        var result = true

        // rule 1
        if (candles.maxOf { it.highPrice } >= ticker.tradePrice * 2 &&
          candles.minOf { it.lowPrice } >= ticker.tradePrice * 0.8
        ) {
          result = false
          this.logger.info("코인 ${ticker.market.quote} 은 6시간동안의 고가가 현재가 기준 2배 이상, 저가가 현재가 기준 80% 이상이어서 거래하지 않습니다.")
        }

        // rule 2
        val rsi = candles
          .sortedBy { it.timestamp }
          .map { it.tradePrice }
          .let { this.calculateRSI(it, 20) }
        if (rsi >= 70.0) {
          result = false
          this.logger.info("코인 ${ticker.market.quote} 은 RSI 지수가 ${rsi.toStrWithPoint()} (70 이상) 이어서 거래하지 않습니다.")
        }

        Mono.just(result)
      }
  }

  private fun calculateRSI(prices: List<Double>, period: Int): Double {
    var averageGain = 0.0
    var averageLoss = 0.0

    // 첫 번째 기간 동안의 평균 상승 및 하락 계산
    for (i in 1 until period) {
      val change = prices[i] - prices[i - 1]
      if (change > 0) {
        averageGain += change
      } else {
        averageLoss -= change
      }
    }

    averageGain /= period
    averageLoss /= period

    // 나머지 기간에 대한 평균 상승 및 하락 업데이트
    for (i in period until prices.size) {
      val change = prices[i] - prices[i - 1]
      if (change > 0) {
        averageGain = (averageGain * (period - 1) + change) / period
        averageLoss = (averageLoss * (period - 1)) / period
      } else {
        averageGain = (averageGain * (period - 1)) / period
        averageLoss = (averageLoss * (period - 1) - change) / period
      }
    }

    val rs = averageGain / averageLoss
    val rsi = 100 - (100 / (1 + rs))

    return rsi
  }
}