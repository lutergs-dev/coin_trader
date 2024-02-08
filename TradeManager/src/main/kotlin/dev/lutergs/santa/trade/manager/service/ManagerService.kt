package dev.lutergs.santa.trade.manager.service

import dev.lutergs.santa.trade.manager.domain.*
import dev.lutergs.santa.universal.mongo.DangerCoinRepository
import dev.lutergs.santa.universal.util.subListOrAll
import dev.lutergs.santa.universal.util.toStrWithScale
import dev.lutergs.upbitclient.api.quotation.candle.CandleMinuteRequest
import dev.lutergs.upbitclient.api.quotation.candle.CandleMinuteResponse
import dev.lutergs.upbitclient.api.quotation.market.MarketWarning
import dev.lutergs.upbitclient.api.quotation.ticker.TickerResponse
import dev.lutergs.upbitclient.dto.Markets
import dev.lutergs.upbitclient.webclient.BasicClient
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class ManagerService(
  private val alertService: AlertService,
  private val upbitClient: BasicClient,
  private val dangerCoinRepository: DangerCoinRepository,
  private val workerController: WorkerController,
  private val workerConfig: WorkerConfig,
) {
  private val logger = LoggerFactory.getLogger(ManagerService::class.java)

  @KafkaListener(topics = ["\${custom.kafka.topic.trade-result}"])
  fun consume(record: ConsumerRecord<String, String>) {
    // consume 뜻 --> 거래 완료되었고, 이제 돈이 다시 있다.
    this.initWorker().blockLast()
  }

  private fun initWorker(): Flux<Boolean> {
    return this.upbitClient.account.getAccount()
      .filter { it.currency == "KRW" }
      .next()
      .flatMap { Mono.just(it.balance.setScale(0).toLong() - 1000) }
      .flatMapMany { totalRawMoney ->
        val workerCount = (totalRawMoney / this.workerConfig.initMaxMoney)
          .let { if (totalRawMoney % this.workerConfig.initMaxMoney >= this.workerConfig.initMinMoney) it + 1 else it }
        if (workerCount == 0L) {
          this.logger.info("코인이 팔렸지만, 현재 가진 금액 (${totalRawMoney}) 이 최소거래값 (${this.workerConfig.initMinMoney}) 보다 적어 거래하지 않습니다.")
          Flux.just(false)
        } else {
          val totalMoney = AtomicLong(totalRawMoney)
          this.findApplicableCoin(workerCount)

            // TODO : 해당 로직 리팩터링 필요
            .flatMapMany { tickers ->
              if (tickers.size < workerCount) {
                this.logger.info("${workerCount}개의 Worker 가 가능함에도, " +
                  "시장 상태에 따라 ${tickers.size}개의 Worker 만, " +
                  "${tickers.joinToString(separator = ",") { it.market.quote }} 코인을 구매합니다.")
              }
              tickers
                .map { it.market }
                .map { market ->
                  if (totalMoney.get() >= this.workerConfig.initMaxMoney) {
                    this.workerController.initWorker(this.workerConfig, market, this.workerConfig.initMaxMoney)
                      .also { this.logger.info("${market.quote} 를 ${this.workerConfig.initMaxMoney} 만큼 구매하는 Worker 를 실행시켰습니다." ) }
                    totalMoney.addAndGet(-this.workerConfig.initMaxMoney)
                  } else if (totalMoney.get() < this.workerConfig.initMaxMoney && totalMoney.get() >= this.workerConfig.initMinMoney) {
                    this.workerController.initWorker(this.workerConfig, market, totalMoney.get())
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
  }

  private fun findApplicableCoin(coinCount: Long): Mono<List<TickerResponse>> {
    if (coinCount < 0) throw IllegalArgumentException("코인 개수는 0 이상의 값을 지정해야 합니다")
    return this.upbitClient.market.getMarketCode()

      // 1. 코인 리스트 중, 한화 거래 마켓이면서, 위험 상태가 아닌 코인을 선별
      .filter {
        if (it.market.base == "KRW" && it.marketWarning == MarketWarning.CAUTION) {
          this.logger.info("코인 ${it.market.quote} 은 CAUTION 상태라서 거래하지 않습니다.")
        }
        it.market.base == "KRW" && it.marketWarning != MarketWarning.CAUTION
      }.flatMap { Mono.just(it.market) }
      .collectList()
      .doOnNext { this.logger.info("한화로 거래 가능하며, 위험 상태가 아닌 코인 리스트는 다음과 같습니다 : ${it.map { d -> d.quote }}") }
      .flatMap { Mono.just(Markets(it)) }

      // 2. 최근 24시간 거래량이 큰 순으로, 25개 선별
      .flatMapMany { this.upbitClient.ticker.getTicker(it) }
      .sort { o1, o2 -> (o2.accTradePrice24h - o1.accTradePrice24h).setScale(0).toInt() }
      .take(25, true)
      .delayElements(Duration.ofMillis(200))    // 업비트 API Timeout 고려

      // 3. 거래가능 규칙 (고가 / 저가 비율 및 RSI) 에 따라 필터링
      .filterWhen { this.isTradeable(it) }

      // 4. 호가 계산 우선순위에 따라 걸러질 수 있게 우선순위 재정렬
      .flatMap { ticker -> this.getPriority(ticker)
        .flatMap { p -> Mono.fromCallable { Pair(ticker, p) } }
      }.sort { o1, o2 -> (o2.second - o1.second).setScale(0).toInt() }
      .collectList()

      // 5. 최근 거래 중, phase 1 에서 손실을 본 코인 판별
      .flatMap { coinList ->
        this.logger.info("거래 가능한 코인 중, 우선순위에 의거한 상위 ${coinList.size}개 코인은 다음과 같습니다 : ${coinList.map { "${it.first.market.quote}(${it.second.toStrWithScale()})" }}")
        this.dangerCoinRepository.getDangerCoins()
          .collectList()
          .flatMap { dangerCoinList ->
            this.logger.info("최근에 너무 가격이 떨어져 24시간동안 거래하지 않을 코인은 다음과 같습니다 : $dangerCoinList")
            coinList
              .filterNot { dangerCoinList.contains(it.first.market.quote) }
              .let { Mono.just(it.map { p -> p.first }) }
          }
      }

      // 6. 실행해야하는 Worker 의 개수에 따라 코인 개수 필터링
      .flatMap { coinList -> when {
        coinCount <= 2 -> coinList.subListOrAll(6)
          .also { this.logger.info("선별할 코인 개수가 ${coinCount}개이기 때문에, 후보군으로 최대 6개의 코인을 선택합니다. ") }
        coinCount <= 5 -> coinList.subListOrAll((coinCount * 3).toInt())
          .also { this.logger.info("선별할 코인 개수가 ${coinCount}개이기 때문에, 후보군으로 최대 ${coinCount * 3}개의 코인을 선택합니다.") }
        else -> coinList.subListOrAll(15)
          .also { this.logger.info("선별할 코인 개수가 ${coinCount}개이기 때문에, 후보군으로 최대 15개의 코인을 선택합니다.") }
      }.let { Mono.just(it) } }
      .doOnNext { this.logger.info("최종으로 선발된 후보 코인은 다음과 같습니다 : ${it.map { t -> t.market.quote }}") }

      // 7. 랜덤으로 Worker 의 개수만큼 코인 최종 결정
      .flatMap { Mono.just(it.shuffled().take(coinCount.toInt())) }
      .doOnNext { this.logger.info("거래할 최종 코인은 ${it.map { d -> d.market.quote }} 입니다.") }
  }

  private fun getPriority(ticker: TickerResponse): Mono<BigDecimal> {
    /**
     * BID ASK (bid 가 사는거, ask 가 파는거)
     * 호가를 기준으로, 1호가부터 가중치를 둬서 비교 판단
     * 가중치는 n호가 (총구매가격 - 총판매가격) 의 ( (15-n) / 15 ) 를 반영함. 즉, 1호가에 가까운 가격일수록 크게 반영
     *
     * (2023.01.26) 근데 이건 너무 고정된 결과를 낳을 수도 있다. 일단 이걸 방지하고자 거래량 자체를 판단하는 것으로 수정
     * (2023.01.28) 손실이 너무 커서, 이거 넣어서 테스트해봐야할듯.
     * */
//    return Mono.just(ticker.accTradePrice24h)
    return this.upbitClient.orderBook.getOrderBook(Markets(listOf(ticker.market)))
      .next()
      .flatMap { orderbook ->
        val length = orderbook.orderbookUnits.size
        orderbook.orderbookUnits
          .mapIndexed { idx, data -> (data.bidSize * data.bidPrice - data.askSize * data.askPrice) * ((BigDecimal(length) - BigDecimal(idx)) / BigDecimal(length)) }
          .reduce {a, b -> a + b}
          .let { Mono.just(it) }
      }
  }

  /**
   * 최근부터 6시간 동안의 데이터를 확인해, 다음과 같은 기준을 따름.
   * 1. 고가가 현재가 기준 2배 이상, 저가가 현재가 기준 80% 이상일 때는 거래하지 않음
   * 2. RSI 지표가 20이하 80 이상인 것은 거래하지 않음.
   * */
  private fun isTradeable(ticker: TickerResponse): Mono<Boolean> {
    return this.upbitClient.candle.getMinute(CandleMinuteRequest(ticker.market, 37, 10))
      .collectList()
      .flatMap { candles ->
        var result = true

        // rule 1
        if (candles.maxOf { it.highPrice } >= ticker.tradePrice * BigDecimal(2) &&
          candles.minOf { it.lowPrice } >= ticker.tradePrice * BigDecimal(0.8)
        ) {
          result = false
          this.logger.info("코인 ${ticker.market.quote} 은 6시간동안의 고가가 현재가 기준 2배 이상, 저가가 현재가 기준 80% 이상이어서 거래하지 않습니다.")
        }

        // rule 2
        val rsi = CandleMinuteResponse.rsi(candles)
        if (rsi >= BigDecimal(80.0) || rsi <= BigDecimal(20.0)) {
          result = false
          this.logger.info("코인 ${ticker.market.quote} 은 RSI 지수가 ${rsi.toStrWithScale()} (80 이상 혹은 20 이하) 이어서 거래하지 않습니다.")
        }

        Mono.just(result)
      }
  }
}