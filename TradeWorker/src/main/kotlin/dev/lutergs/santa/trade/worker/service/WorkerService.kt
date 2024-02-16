package dev.lutergs.santa.trade.worker.service

import dev.lutergs.santa.trade.worker.domain.*
import dev.lutergs.santa.trade.worker.domain.entity.MainTrade
import dev.lutergs.santa.trade.worker.domain.entity.*
import dev.lutergs.santa.trade.worker.infra.LoggerCreate
import dev.lutergs.santa.util.SellType
import dev.lutergs.santa.util.toStrWithScale
import dev.lutergs.santa.util.toStrWithStripTrailing
import dev.lutergs.upbitclient.api.exchange.order.OrderRequest
import dev.lutergs.upbitclient.api.quotation.orderbook.OrderStep
import dev.lutergs.upbitclient.dto.Markets
import dev.lutergs.upbitclient.webclient.BasicClient
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.lang.IllegalStateException
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Service
class WorkerService(
  private val mainTrade: MainTrade,
  private val tradePhase: TradePhase,
  private val alarmSender: MessageSender,
  private val manager: Manager,
  private val trader: Trader,
  private val client: BasicClient,
  private val applicationContext: ApplicationContext,
  @Value("\${custom.trade.watch.interval}") private val watchIntervalSecond: Int,
) {
  private val originLogger = LoggerCreate.createLogger(this::class)
  private val p1Logger = LoggerCreate.createLogger(this::class, "Phase1")
  private val p2Logger = LoggerCreate.createLogger(this::class, "Phase2")

  @PostConstruct
  fun run() {
    this.buyCoin()
      .flatMap { buyOrder -> this.phase1(buyOrder) }
      .flatMap { this.sendDangerousCoin(it) }
      .flatMap {
        if (it.sell == null) { this.phase2(it) }
        else { Mono.just(it) }
      }.flatMap { this.manager.executeNewWorker() }
      .doOnTerminate { this.closeApplication() }
      .subscribe()
  }

  fun buyCoin(): Mono<WorkerTradeResult> {
    return this.trader.buyMarket(this.mainTrade.market, BigDecimal(this.mainTrade.money))
      .doOnNext {
        this.originLogger.info("코인 구매가 완료되었습니다. 대상 코인 : ${it.buy.market}, 가격: ${it.buy.avgPrice().toPlainString()}, 구매량: ${it.buy.totalVolume().toStrWithScale(3)}, 총금액: ${it.buy.totalPrice().toStrWithScale()}")
        this.originLogger.info("구매 주문 UUID: ${it.buy.uuid}")
      }
  }

  /**
   * 최초 정해진 시각 동안은, 코인을 구매하고 1차 이득점, 손실점을 설정한 후 해당 지점에 도달했을 때만 판매한다.
   * */
  fun phase1(workerTradeResult: WorkerTradeResult, logger: Logger = this.p1Logger): Mono<WorkerTradeResult> {
    return this.client.orderBook.getOrderBook(Markets.fromMarket(workerTradeResult.buy.market))
      .next()
      .flatMap { orderbook ->
        val (profitPrice, lossPrice, buyPrice) = workerTradeResult.buy.avgPrice().let { Triple(
          OrderStep.calculateOrderStepPrice(this.tradePhase.phase1.getProfitPrice(it)),
          OrderStep.calculateOrderStepPrice(this.tradePhase.phase1.getLossPrice(it)),
          OrderStep.calculateOrderStepPrice(it)
        )}
        this.trader.placeSellLimit(workerTradeResult, profitPrice)
          .doOnNext { logger.info("코인의 이득점을 설정했습니다. " +
            "가격 : ${profitPrice.toPlainString()}, 총금액 : ${it.buy.totalPrice().toStrWithScale(4)}. " +
            "앞으로 2시간 반동안, 코인의 가격이 ${profitPrice.toPlainString()} (이득주문점) 혹은 ${lossPrice.toPlainString()} (손실점) 에 도달하면 판매합니다.")
          }.flatMap { tr ->
            // 지정 초에 한 번씩 가격과 오더 상태를 같이 확인함
            Mono.zip(
              Mono.defer { this.client.order.getOrder(OrderRequest(tr.sell!!.uuid)) },
              Mono.defer { this.client.ticker.getTicker(Markets.fromMarket(tr.sell!!.market)).next() }
            ).flatMap { tp ->
              val (firstSellOrder, currentPrice) = tp.t1 to tp.t2.tradePrice
              this.logCurrentStatus(workerTradeResult.buy.createdAt, profitPrice, lossPrice, currentPrice, buyPrice, logger)
              when {
                // 이득주문 완료시
                firstSellOrder.isFinished() -> workerTradeResult.completeSellOrder(firstSellOrder, SellType.PROFIT)
                  .let { this.trader.finishSellLimit(it) }
                  .doOnNext { logger.info("코인을 이득을 보고 매도했습니다.") }
                // 손실점 도달시
                currentPrice <= lossPrice -> {
                  logger.info("코인이 ${this.tradePhase.phase1.lossPercent.toStrWithStripTrailing()}% 이상 손해를 보고 있습니다. 현재 가격으로 매도합니다.")
                  this.trader.cancelSellLimit(tr)
                    .doOnNext { logger.info("코인 매도 주문을 취소했습니다. 현재 가격으로 매도합니다.") }
                    .flatMap { this.trader.sellMarket(workerTradeResult, SellType.LOSS) }
                    .doOnNext { logger.info("코인 현재가 매도가 완료되었습니다.") }
                }
                else -> Mono.empty()
              }
            }.repeatWhenEmpty((this.tradePhase.phase1.waitMinute * 60 / this.watchIntervalSecond).toInt()) {
              it.delayElements(Duration.ofSeconds(this.watchIntervalSecond.toLong()))
            }.onErrorResume(IllegalStateException::class.java) {
              logger.info("코인이 판매점에 도달하지 못했습니다. 현재 매도 주문을 취소하고, Phase 2 로 진입합니다.")
              this.trader.cancelSellLimit(tr)
                .doOnNext { logger.info("코인의 매도 주문을 취소했습니다.") }
            }
          }
      }
  }


  fun sendDangerousCoin(workerTradeResult: WorkerTradeResult, logger: Logger = this.p1Logger): Mono<WorkerTradeResult> {
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
  fun phase2(workerTradeResult: WorkerTradeResult, logger: Logger = this.p2Logger): Mono<WorkerTradeResult> {
    logger.info("지금부터 구매평균가의 ${this.tradePhase.phase2.profitPercent}% 이상 이득을 보거나, ${this.tradePhase.phase2.lossPercent}% 이하로 손실을 볼 경우 매매합니다.")
    val (profitPrice, lossPrice, buyPrice) = workerTradeResult.buy.avgPrice().let { Triple(
      OrderStep.calculateOrderStepPrice(this.tradePhase.phase2.getProfitPrice(it)),
      OrderStep.calculateOrderStepPrice(this.tradePhase.phase2.getLossPrice(it)),
      OrderStep.calculateOrderStepPrice(it)
    )}
    return Mono.defer { this.client.ticker.getTicker(Markets.fromMarket(workerTradeResult.buy.market)).next() }
      .flatMap { ticker ->
        val currentPrice = ticker.tradePrice
        this.logCurrentStatus(workerTradeResult.buy.createdAt, profitPrice, lossPrice, currentPrice, buyPrice, logger)

        when {
          currentPrice >= profitPrice -> this.trader.sellMarket(workerTradeResult, SellType.STOP_PROFIT)
            .doOnNext { logger.info("코인이 이득점인 ${profitPrice.toPlainString()}원보다 높습니다 (${currentPrice.toPlainString()}원). 현재 가격으로 매도했습니다.")
              logger.info("이익 매도가 완료되었습니다.") }
          currentPrice <= lossPrice -> this.trader.sellMarket(workerTradeResult, SellType.STOP_LOSS)
            .doOnNext { logger.info("코인이 손실점인 ${lossPrice.toPlainString()}원보다 낮습니다 (${currentPrice.toPlainString()}원). 현재 가격으로 매도했습니다.")
              logger.info("손실 매도가 완료되었습니다.") }
          else -> Mono.empty()
        }
      }.repeatWhenEmpty((this.tradePhase.phase2.waitMinute * 60 / this.watchIntervalSecond).toInt()) {
        it.delayElements(Duration.ofSeconds(this.watchIntervalSecond.toLong()))
      }.onErrorResume(IllegalStateException::class.java) {
        logger.info("${(this.tradePhase.totalWaitMinute().toDouble() / 60.0).toStrWithScale(1)} 시간동안 기다렸지만 매매가 되지 않아, 현재 가격으로 매도합니다.")
        this.client.ticker.getTicker(Markets.fromMarket(workerTradeResult.buy.market)).next()
          .flatMap { ticker ->
            val type = if (ticker.tradePrice > workerTradeResult.buy.avgPrice()) SellType.TIMEOUT_PROFIT else SellType.TIMEOUT_LOSS
            this.trader.sellMarket(workerTradeResult, type)
              .doOnNext { logger.info("시간초과 ${if (ticker.tradePrice > it.buy.avgPrice()) "이익" else "손실"} 매도가 완료되었습니다.") }
          }
      }
  }

  private fun logCurrentStatus(dt: OffsetDateTime, profit: BigDecimal, loss: BigDecimal, current: BigDecimal, buy: BigDecimal, logger: Logger) {
    LocalDateTime.now()
      .takeIf { it.second < this.watchIntervalSecond }
      ?.let {
        Duration.between(dt.toLocalDateTime(), it)
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