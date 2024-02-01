package dev.lutergs.santa.trade.worker.infra

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass


object LoggerCreate {
  private val appName = System.getenv("APP_ID")

  fun createLogger(className: String): Logger {
    return LoggerFactory.getLogger("[${this.appName}][$className]")
  }

  fun<T : Any> createLogger(clazz: KClass<T>, vararg optionalNames: String): Logger {
    return optionalNames.joinToString(separator = "") { "[${it}]" }
      .let { LoggerFactory.getLogger("[${this.appName}][${clazz.simpleName}]${it}") }
  }

  fun<T : Any> createLogger(clazz: KClass<T>): Logger {
    return LoggerFactory.getLogger("[${this.appName}][${clazz.simpleName}]")
  }
}