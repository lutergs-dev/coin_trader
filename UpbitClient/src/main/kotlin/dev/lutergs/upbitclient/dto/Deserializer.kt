package dev.lutergs.upbitclient.dto

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class TickerDateDeserializer: JsonDeserializer<LocalDate>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LocalDate {
    val dateString = p.text
    return LocalDate.of(
      dateString.substring(0, 4).toInt(),
      dateString.substring(4, 6).toInt(),
      dateString.substring(6, 8).toInt()
    )
  }
}

class TickerDateSerializer: JsonSerializer<LocalDate>() {
  override fun serialize(value: LocalDate, gen: JsonGenerator, serializers: SerializerProvider) {
    gen.writeString(value.format(dateTimeFormatter))
  }

  companion object {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
  }
}

class TickerTimeDeserializer: JsonDeserializer<LocalTime>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LocalTime {
    val dateString = p.text
    return LocalTime.of(
      dateString.substring(0, 2).toInt(),
      dateString.substring(2, 4).toInt(),
      dateString.substring(4, 6).toInt()
    )
  }
}

class TickerTimeSerializer: JsonSerializer<LocalTime>() {
  override fun serialize(value: LocalTime, gen: JsonGenerator, serializers: SerializerProvider) {
    gen.writeString(value.format(dateTimeFormatter))
  }

  companion object {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")
  }
}

class TickerDateWithHyphenDeserializer: JsonDeserializer<LocalDate>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LocalDate {
    val dateStrings = p.text.split("-")
    return LocalDate.of(
      dateStrings[0].toInt(),
      dateStrings[1].toInt(),
      dateStrings[2].toInt()
    )
  }
}

class TickerDateWithHyphenSerializer: JsonSerializer<LocalDate>() {
  override fun serialize(value: LocalDate, gen: JsonGenerator, serializers: SerializerProvider) {
    gen.writeString(value.format(dateTimeFormatter))
  }

  companion object {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  }
}

class NumberStringDeserializer: JsonDeserializer<BigDecimal>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BigDecimal {
    return BigDecimal(p.text)
  }
}

class NumberStringSerializer: JsonSerializer<BigDecimal>() {
  override fun serialize(value: BigDecimal, gen: JsonGenerator, serializers: SerializerProvider?) {
    gen.writeString(value.stripTrailingZeros().toPlainString())
  }
}


class MarketCodeDeserializer: JsonDeserializer<MarketCode>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MarketCode {
    return p.text.split("-").let {
      MarketCode(it[0], it[1])
    }
  }
}

class MarketCodeSerializer: JsonSerializer<MarketCode>() {
  override fun serialize(value: MarketCode, gen: JsonGenerator, serializers: SerializerProvider) {
    gen.writeString(value.toString())
  }

}

class OffsetDateTimeDeserializer: JsonDeserializer<OffsetDateTime>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OffsetDateTime {
    return p.text.let { OffsetDateTime.parse(it) }
  }
}


class OrderTypeDeserializer: JsonDeserializer<OrderType>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OrderType {
    return p.text.let { OrderType.fromString(it) }
  }
}

class OrderTypeSerializer: JsonSerializer<OrderType>() {
  override fun serialize(value: OrderType, gen: JsonGenerator, serializers: SerializerProvider) {
    gen.writeString(value.name.lowercase())
  }
}


class OrderSideDeserializer: JsonDeserializer<OrderSide>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OrderSide {
    return OrderSide.fromRawString(p.text)
  }
}

class OrderSideSerializer: JsonSerializer<OrderSide>() {
  override fun serialize(value: OrderSide, gen: JsonGenerator, serializers: SerializerProvider) {
    gen.writeString(value.name.lowercase())
  }
}