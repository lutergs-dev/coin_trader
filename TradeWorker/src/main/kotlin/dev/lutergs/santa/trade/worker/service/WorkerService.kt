package dev.lutergs.santa.trade.worker.service

import dev.lutergs.santa.trade.worker.domain.*
import dev.lutergs.santa.trade.worker.domain.entity.MainTrade
import dev.lutergs.santa.trade.worker.domain.entity.*
import dev.lutergs.santa.trade.worker.infra.LoggerCreate
import dev.lutergs.santa.util.SellType
import dev.lutergs.santa.util.toStrWithScale
import dev.lutergs.santa.util.toStrWithStripTrailing
import dev.lutergs.upbitclient.api.quotation.orderbook.OrderStep
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContext
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

class WorkerService(
  // 실행 시 필요한 변수 집합
  private val mainTrade: MainTrade,
  private val tradePhase: TradePhase,
  private val watchIntervalSecond: Int,
  private val movingAverageBigCount: Int,
  private val movingAverageSmallCount: Int,

  // repository implementation
  private val trader: Trader,
  private val priceTracker: CoinPriceTracker,
  private val alarmSender: MessageSender,
  private val manager: Manager,

  // 기타
  private val applicationContext: ApplicationContext,
) {
  private val originLogger = LoggerCreate.createLogger(this::class)
  private val p1Logger = LoggerCreate.createLogger(this::class, "Phase1")
  private val p2Logger = LoggerCreate.createLogger(this::class, "Phase2")

  private data class CurrentOrderExecuteStatus(
    val currentPrice: BigDecimal,
    val movingAverageBig: BigDecimal,
    val movingAverageSmall: BigDecimal
  ) {
    val isSellPoint = this.movingAverageBig >= movingAverageSmall
  }

  @PostConstruct
  fun run() {
    this.buyCoin()
      .flatMap { buyOrder -> this.phase1(buyOrder) }
      .flatMap { this.sendDangerousCoin(it) }
      .flatMap {
        if (it.sell == null) { this.phase2(it) }
        else { Mono.just(it) }
      }.flatMap { this.priceTracker.cleanUp(it.buy.uuid) }
      .then(Mono.defer { this.manager.executeNewWorker() })
      .doOnTerminate { this.closeApplication() }
      .block()
    this.closeApplication()
  }

  private fun buyCoin(): Mono<WorkerTradeResult> {
    return this.trader.buyMarket(this.mainTrade.market, BigDecimal(this.mainTrade.money))
      .doOnNext {
        this.originLogger.info("코인 구매가 완료되었습니다. 대상 코인 : ${it.buy.market}, 가격: ${it.buy.avgPrice().toPlainString()}, 구매량: ${it.buy.totalVolume().toStrWithScale(3)}, 총금액: ${it.buy.totalPrice().toStrWithScale()}")
        this.originLogger.info("구매 주문 UUID: ${it.buy.uuid}")
      }
  }

  private fun getCurrentPriceAndMovingAverage(wtr: WorkerTradeResult): Mono<CurrentOrderExecuteStatus> {
    return this.priceTracker.getCoinCurrentPrice(wtr)
      .flatMap { currentPrice ->
        this.priceTracker.getAvgOfLatestAB(wtr.buy.uuid, this.movingAverageBigCount, this.movingAverageSmallCount)
          .flatMap { Mono.fromCallable { Triple(currentPrice, it.first, it.second) } }
      }.flatMap {
        Mono.fromCallable { CurrentOrderExecuteStatus(it.first, it.second, it.third) }
      }
  }

  /**
   * 최초 정해진 시각 동안은, 코인을 구매하고 1차 이득점, 손실점을 설정한 후 해당 지점에 도달했을 때만 판매한다.
   * */
  private fun phase1(workerTradeResult: WorkerTradeResult, logger: Logger = this.p1Logger): Mono<WorkerTradeResult> {
    val (profitPrice, lossPrice, buyPrice) = workerTradeResult.buy.avgPrice().let { Triple(
      OrderStep.calculateOrderStepPrice(this.tradePhase.phase1.getProfitPrice(it)),
      OrderStep.calculateOrderStepPrice(this.tradePhase.phase1.getLossPrice(it)),
      OrderStep.calculateOrderStepPrice(it)
    )}
    val (isEnd, endTime) = AtomicBoolean(false) to LocalDateTime.now().plusMinutes(this.tradePhase.phase1.waitMinute)
    return Mono.defer { this.getCurrentPriceAndMovingAverage(workerTradeResult) }
      .flatMap { status ->
        this.logCurrentStatus(workerTradeResult.buy.createdAt, profitPrice, lossPrice, status.currentPrice, buyPrice, logger)
        when {
          // 이득점 도달시, 이동평균추세가 꺾이면 판매
          status.currentPrice >= profitPrice && status.isSellPoint -> this.trader.sellMarket(workerTradeResult, SellType.STOP_PROFIT)
            .flatMap { isEnd.set(true); Mono.fromCallable { it } }
            .doOnNext {
              logger.info("코인이 이득점인 ${profitPrice.toStrWithStripTrailing()}원보다 높고, 상승추세가 꺾였습니다. (${status.currentPrice.toStrWithStripTrailing()}원). 현재 가격으로 매도했습니다.")
              logger.info("이익 매도가 완료되었습니다.")
            }
          // 손실점 도달시 판매
          status.currentPrice <= lossPrice -> this.trader.sellMarket(workerTradeResult, SellType.STOP_LOSS)
            .flatMap { isEnd.set(true); Mono.fromCallable { it } }
            .doOnNext {
              logger.info("코인이 손실점인 ${lossPrice.toStrWithStripTrailing()}원보다 낮습니다. (${status.currentPrice.toStrWithStripTrailing()}원). 현재 가격으로 매도했습니다.")
              logger.info("손실 매도가 완료되었습니다.")
            }
          else -> Mono.fromCallable { workerTradeResult }
        }
      }.delayElement(Duration.ofSeconds(this.watchIntervalSecond.toLong()))
      .repeat { !isEnd.get() && LocalDateTime.now() < endTime }
      .last()
      .flatMap {
        if (!isEnd.get()) {
          logger.info("코인이 판매점에 도달하지 못했습니다. 현재 매도 주문을 취소하고, Phase 2 로 진입합니다.")
        }
        Mono.fromCallable { it }
      }
  }


  private fun sendDangerousCoin(workerTradeResult: WorkerTradeResult, logger: Logger = this.p1Logger): Mono<WorkerTradeResult> {
    return when (workerTradeResult.sellType) {
      SellType.LOSS -> this.alarmSender.sendAlarm(DangerCoinMessage.fromOrderResponse(workerTradeResult.sell!!))
        .doOnNext { logger.info("손실 매도한 코인 (${this.mainTrade.market.quote}) 에 대한 정보를 controller 로 전송했습니다.") }
        .thenReturn(workerTradeResult)
      else -> Mono.just(workerTradeResult)
    }
  }

  /**
   * 한시간 반 동안, 구매 가격보다 높으면 무조건 매매
   * */
  private fun phase2(workerTradeResult: WorkerTradeResult, logger: Logger = this.p2Logger): Mono<WorkerTradeResult> {
    logger.info("지금부터 구매평균가의 ${this.tradePhase.phase2.profitPercent}% 이상 이득을 보면서 상승추세가 꺾일 경우, ${this.tradePhase.phase2.lossPercent}% 이하로 손실을 볼 경우 매매합니다.")
    val (profitPrice, lossPrice, buyPrice) = workerTradeResult.buy.avgPrice().let { Triple(
      OrderStep.calculateOrderStepPrice(this.tradePhase.phase2.getProfitPrice(it)),
      OrderStep.calculateOrderStepPrice(this.tradePhase.phase2.getLossPrice(it)),
      OrderStep.calculateOrderStepPrice(it)
    )}
    val (isEnd, endTime) = AtomicBoolean(false) to LocalDateTime.now().plusMinutes(this.tradePhase.phase2.waitMinute)
    return Mono.defer { this.getCurrentPriceAndMovingAverage(workerTradeResult) }
      .flatMap { status ->
        this.logCurrentStatus(workerTradeResult.buy.createdAt, profitPrice, lossPrice, status.currentPrice, buyPrice, logger)
        when {
          status.currentPrice >= profitPrice && status.isSellPoint -> this.trader.sellMarket(workerTradeResult, SellType.STOP_PROFIT)
            .flatMap { isEnd.set(true); Mono.fromCallable { it } }
            .doOnNext { logger.info("코인이 이득점인 ${profitPrice.toStrWithStripTrailing()}원보다 높습니다 (${status.currentPrice.toStrWithStripTrailing()}원). 현재 가격으로 매도했습니다.")
              logger.info("이익 매도가 완료되었습니다.") }
          status.currentPrice <= lossPrice -> this.trader.sellMarket(workerTradeResult, SellType.STOP_LOSS)
            .flatMap { isEnd.set(true); Mono.fromCallable { it } }
            .doOnNext { logger.info("코인이 손실점인 ${lossPrice.toStrWithStripTrailing()}원보다 낮습니다 (${status.currentPrice.toStrWithStripTrailing()}원). 현재 가격으로 매도했습니다.")
              logger.info("손실 매도가 완료되었습니다.") }
          else -> Mono.fromCallable { workerTradeResult }
        }
      }.delayElement(Duration.ofSeconds(this.watchIntervalSecond.toLong()))
      .repeat { !isEnd.get() && LocalDateTime.now() < endTime }
      .last()
      .flatMap {
        if (!isEnd.get()) {
          logger.info("${(this.tradePhase.totalWaitMinute().toDouble() / 60.0).toStrWithScale(1)} 시간동안 기다렸지만 매매가 되지 않아, 현재 가격으로 매도합니다.")
          this.priceTracker.getCoinCurrentPrice(workerTradeResult)
            .flatMap { currentPrice ->
              val profit = workerTradeResult.buy.totalPrice() - (currentPrice * workerTradeResult.buy.avgPrice()) - (workerTradeResult.buy.paidFee * BigDecimal(2.0))
              val type = if (profit > BigDecimal.ZERO) SellType.TIMEOUT_PROFIT else SellType.TIMEOUT_LOSS
              this.trader.sellMarket(workerTradeResult, type)
                .doOnNext { logger.info("시간초과 ${if (currentPrice > it.buy.avgPrice()) "이익" else "손실"} 매도가 완료되었습니다.") }
            }
        } else {
          Mono.fromCallable { it }
        }
      }
  }

  private fun logCurrentStatus(dt: OffsetDateTime, profit: BigDecimal, loss: BigDecimal, current: BigDecimal, buy: BigDecimal, logger: Logger) {
    LocalDateTime.now()
      .takeIf { it.second < this.watchIntervalSecond }
      ?.let {
        Duration.between(dt.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime(), it)
          .let { d ->
            val hours = d.toHours()
            val minutes = d.toMinutes() % 60
            val secs = d.seconds % 60
            logger.info(
              "이득가: ${profit.toStrWithStripTrailing()}, 손실가: ${loss.toStrWithStripTrailing()}, " +
              "현재가: ${current.toStrWithStripTrailing()}, 구매가: ${buy.toStrWithStripTrailing()} " +
              "경과시간: $hours 시간 $minutes 분 $secs 초")
          }
      }
  }

  private fun closeApplication() {
    SpringApplication.exit(this.applicationContext)
  }
}