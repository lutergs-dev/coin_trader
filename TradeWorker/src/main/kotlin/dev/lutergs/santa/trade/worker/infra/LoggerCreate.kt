package dev.lutergs.santa.trade.worker.infra

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass


object LoggerCreate {
  private var appName: String = "WORKER_NOT_INITIATED"
  private var isAppNameSetted = false

  fun setAppName(appName: String) {
    if (this.isAppNameSetted) {
      throw IllegalAccessException("Logger 생성기가 설정된 이후에 설정값에 접근했습니다.")
    } else {
      this.appName = appName
      this.isAppNameSetted = true
    }
  }

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