package dev.lutergs.santa.trade.domain

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface DangerCoinRepository {

  fun setDangerCoin(coinName: String): Mono<String>

  fun getDangerCoins(): Flux<String>
}