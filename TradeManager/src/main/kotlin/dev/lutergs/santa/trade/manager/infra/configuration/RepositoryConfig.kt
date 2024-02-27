package dev.lutergs.santa.trade.manager.infra.configuration

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*


@Configuration
class UniversalDataConfig {

  @Bean
  fun customConversion(): MongoCustomConversions = MongoCustomConversions(
    listOf(
      OffsetDateTimeWriteConverter(),
      OffsetDateTimeReadConverter()
    )
  )

  @WritingConverter
  class OffsetDateTimeWriteConverter: Converter<OffsetDateTime, Date> {
    override fun convert(source: OffsetDateTime): Date = Date.from(source.toInstant())
  }

  @ReadingConverter
  class OffsetDateTimeReadConverter: Converter<Date, OffsetDateTime> {
    override fun convert(source: Date): OffsetDateTime = source.toInstant().atOffset(ZoneOffset.ofHours(9))
  }
}