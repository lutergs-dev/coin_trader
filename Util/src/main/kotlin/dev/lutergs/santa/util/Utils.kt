package dev.lutergs.santa.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random


object Util {
  fun generateRandomString(length: Int = 10): String {
    val charPool: List<Char> = ('a'..'z') + ('0'..'9')

    return (1..length)
      .map { Random.nextInt(0, charPool.size) }
      .map(charPool::get)
      .joinToString("")
  }
}



fun BigDecimal.toStrWithScale(point: Int = 2): String {
  return this.setScale(point, RoundingMode.HALF_UP).toString()
}

fun BigDecimal.toStrWithStripTrailing(): String {
  return this.stripTrailingZeros().toPlainString()
}

fun Double.toStrWithScale(point: Int = 2): String {
  return String.format("%.${point}f", this)
}

private val dateTimeFormatter = DateTimeFormatter.ofPattern("HH'시' mm'분'")

fun OffsetDateTime.toHourAndMinuteString(): String {
  return this.toLocalDateTime().format(dateTimeFormatter)
}

/**
 * lastIndex is exclusive
 * */
inline fun <reified T> List<T>.subListOrAll(lastIndex: Int): List<T> {
  return if (this.size > lastIndex) {
    this.subList(0, lastIndex)
  } else {
    this
  }
}