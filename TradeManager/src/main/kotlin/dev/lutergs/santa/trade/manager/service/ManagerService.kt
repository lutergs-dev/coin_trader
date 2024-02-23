package dev.lutergs.santa.trade.manager.service

import dev.lutergs.santa.trade.manager.domain.*
import dev.lutergs.santa.trade.manager.domain.entity.Priority
import dev.lutergs.santa.trade.manager.domain.entity.WorkerConfig
import dev.lutergs.santa.util.subListOrAll
import dev.lutergs.santa.util.toStrWithScale
import dev.lutergs.upbitclient.api.quotation.candle.*
import dev.lutergs.upbitclient.api.quotation.market.MarketWarning
import dev.lutergs.upbitclient.api.quotation.ticker.TickerResponse
import dev.lutergs.upbitclient.dto.MarketCode
import dev.lutergs.upbitclient.dto.Markets
import dev.lutergs.upbitclient.webclient.BasicClient
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class ManagerService(
  private val dangerControlService: DangerControlService,
  private val dangerCoinRepository: DangerCoinRepository,
  private val upbitClient: BasicClient,
  private val workerController: WorkerController,
  private val workerConfig: WorkerConfig,
) {
  private val logger = LoggerFactory.getLogger(ManagerService::class.java)

  fun triggerManager(): Mono<List<Boolean>> {
    return this.initWorker().collectList()
  }

  @PostConstruct
  fun test() {
    this.findApplicableCoin(10)
      .block()
  }

  private fun initWorker(): Flux<Boolean> {
    return this.upbitClient.account.getAccount()
      .filter { it.currency == "KRW" }
      .next()
      .flatMap { Mono.just(it.balance.setScale(0, RoundingMode.HALF_UP).toLong() - 1000) }
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
            .flatMapMany { coins ->
              if (coins.size < workerCount) {
                this.logger.info("${workerCount}개의 Worker 가 가능함에도, " +
                  "시장 상태에 따라 ${coins.size}개의 Worker 만, " +
                  "${coins.joinToString(separator = ",") { it.quote }} 코인을 구매합니다.")
              }
              coins
                .map { coin ->
                  if (totalMoney.get() >= this.workerConfig.initMaxMoney) {
                    this.workerController.initWorker(this.workerConfig, coin, this.workerConfig.actualInitMaxMoney())
                      .also { this.logger.info("${coin.quote} 를 ${this.workerConfig.initMaxMoney} 만큼 구매하는 Worker 를 실행시켰습니다." ) }
                    totalMoney.addAndGet(-this.workerConfig.initMaxMoney)
                  } else if (totalMoney.get() < this.workerConfig.initMaxMoney && totalMoney.get() >= this.workerConfig.initMinMoney) {
                    this.workerController.initWorker(this.workerConfig, coin, this.workerConfig.actualInitMaxMoney(totalMoney.get()))
                      .also { this.logger.info("${coin.quote} 를 ${totalMoney.get()} 만큼 구매하는 Worker 를 실행시켰습니다." ) }
                    totalMoney.set(0)
                  }
                  Mono.just(true)
                }.let { Flux.concat(it) }
            }.switchIfEmpty(
              this.dangerControlService.sendAllCoinsAreDangerous()
                .flatMap {
                  this.logger.warn("모든 코인이 위험한 상태입니다! 조치가 필요합니다.")
                  Mono.just(false)
                }
            )
        }
      }
  }

  private fun findApplicableCoin(coinCount: Long): Mono<List<MarketCode>> {
    if (coinCount < 0) throw IllegalArgumentException("코인 개수는 0 이상의 값을 지정해야 합니다")
    return this.upbitClient.market.getMarketCode()

      // 1. 코인 리스트 중, 한화 거래 마켓이면서, 위험 상태가 아닌 코인을 선별
      .filter { response ->
        if (response.market.base == "KRW" && response.marketWarning == MarketWarning.CAUTION) {
          this.logger.info("코인 ${response.market.quote} 은 CAUTION 상태라서 거래하지 않습니다.")
        }
        response.market.base == "KRW" && response.marketWarning != MarketWarning.CAUTION
      }.flatMap { Mono.just(it.market) }
      .collectList()
      .doOnNext { this.logger.info("한화로 거래 가능하며, 위험 상태가 아닌 코인 리스트는 다음과 같습니다 : ${it.map { d -> d.quote }}") }

      .flatMapMany { marketCodes ->
        Mono.zip(
          this.upbitClient.ticker.getTicker(Markets(marketCodes)).collectList(),
          this.upbitClient.orderBook.getOrderBook(Markets(marketCodes)).collectList()
        ).flatMapMany { t ->
          val (tickers, orderBooks) = t.t1 to t.t2
          tickers

            // 2. 최근 24시간 거래량이 큰 순으로, 25개 선별
            .sortedByDescending { it.accTradePrice24h }
            .take(25)

            // 3. ticker 에 맞는 orderbook 과, candle 을 찾아서 Priority DTO 생성
            .mapNotNull { ticker ->
              orderBooks
                .find { it.market == ticker.market }
                ?.let { orderBook ->
                  this.upbitClient.candle.getMinute(CandleMinuteRequest(ticker.market, 37, 10))
                    .collectList()
                    .flatMap { candles -> Mono.fromCallable { Priority(ticker, candles, orderBook) } }
                }
            }.let { Flux.concat(it) }
        }
      }.delayElements(Duration.ofMillis(200))

      // 4. 거래가능한 것만 걸러냄
      .filter { it.isTradeable }

      // 5. 우선순위 내림차순 정렬
      .collectSortedList { p1, p2 -> p1.compareTo(p2) * -1 }
      .doOnNext { pList -> pList.forEachIndexed { idx, priority ->
        this.logger.info("${idx + 1}번째 코인: ${priority.coin},\t상승세: ${priority.isGoUp},\t우선순위 가중치: ${priority.sellBuyPriceWeight.toStrWithScale()}")
      } }

      // 6. 최근 거래 중, phase 1 에서 손실을 본 코인 판별
      .flatMap { pList ->
        this.dangerCoinRepository.getDangerCoins()
          .collectList()
          .flatMap { dangerCoinList ->
            dangerCoinList.map { it.coinName }
              .let { coinStringList ->
                this.logger.info("최근에 너무 가격이 떨어져 24시간동안 거래하지 않을 코인은 다음과 같습니다 : $coinStringList")
                pList
                  .filterNot { coinStringList.contains(it.coin.quote) }
                  .let { Mono.just(it.map { p -> p.coin }) }
              }
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
      .doOnNext { this.logger.info("최종으로 선발된 후보 코인은 다음과 같습니다 : ${it.map { t -> t.quote }}") }

      // 7. 랜덤으로 Worker 의 개수만큼 코인 최종 결정
      .flatMap { Mono.just(it.shuffled().take(coinCount.toInt())) }
      .doOnNext { this.logger.info("거래할 최종 코인은 ${it.map { d -> d.quote }} 입니다.") }
  }

  private fun getPriority(ticker: TickerResponse): Mono<BigDecimal> {
    /**
     * BID ASK (bid 가 사는거, ask 가 파는거)
     * 호가를 기준으로, 1호가부터 가중치를 둬서 비교 판단
     * 가중치는 n호가 (총구매가격 - 총판매가격) 의 ( (15-n) / 15 ) 를 반영함. 즉, 1호가에 가까운 가격일수록 크게 반영
     *
     * (2023.01.26) 근데 이건 너무 고정된 결과를 낳을 수도 있다. 일단 이걸 방지하고자 거래량 자체를 판단하는 것으로 수정
     * (2023.01.28) 손실이 너무 커서, 원래 버전으로 rollback
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
   * 1. (고가 - 현재가) : (현재가 - 저가) 의 비율이 5:1 이상이면서 고가가 저가보다 늦게 일어났을 경우엔 거래하지 않음.
   * 2. 저가와 현재가가 동일할 경우 거래하지 않음.
   * 3. 저가가 현재가보다 높을 때, 현재가의 103% 이하일 경우는 거래하지 않음.
   * 4. RSI 지표가 20이하 80 이상인 것은 거래하지 않음.
   * */
  private fun isTradeable(ticker: TickerResponse): Mono<Boolean> {
    return this.upbitClient.candle.getMinute(CandleMinuteRequest(ticker.market, 37, 10))
      .collectList()
      .flatMap { candles -> Mono.fromCallable {
        val (high, low) = candles.getHighPriceCandle() to candles.getLowPriceCandle()
        sequenceOf (
          Pair("(고가 - 현재가) : (현재가 - 저가) 의 비율이 5:1 이상이면서 고가가 저가보다 늦게 일어난 경우") {
            ((high.highPrice - ticker.tradePrice).abs() > (low.lowPrice - ticker.tradePrice).abs() * BigDecimal(5)) &&
            (high.timestamp > low.timestamp)
          },
          Pair("저가와 현재가가 동일") {
            low.lowPrice <= ticker.tradePrice
          },
          Pair("저가가 현재가보다 높을 때, 현재가의 103% 이하") {
            low.lowPrice > ticker.tradePrice && low.lowPrice <= ticker.tradePrice * BigDecimal(1.03)
          },
          Pair("RSI 지표가 20이하 80 이상") {
            val rsi = CandleMinuteResponse.rsi(candles)
            rsi >= BigDecimal(80.0) || rsi <= BigDecimal(20.0)
          }
        ).firstOrNull { it.second() }
          ?.let {
            this.logger.info("코인 ${ticker.market.quote} 는 ${it.first} 이기 때문에 거래하지 않습니다.")
            false
          } ?: true
      }
    }
  }
}