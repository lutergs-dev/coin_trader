package dev.lutergs.santa.universal.util

import java.math.BigDecimal
import java.math.RoundingMode
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