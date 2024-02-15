package dev.lutergs.santa.trade.manager.domain.entity

import dev.lutergs.santa.util.toHourAndMinuteString
import java.time.OffsetDateTime


data class DangerCoin (
  val coinName: String,
  val createdAt: OffsetDateTime
) {
  fun toInfoString(): String {
    return "[${this.createdAt.toHourAndMinuteString()}] ${this.coinName} 손실 발생"
  }
}