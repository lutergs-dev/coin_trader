package dev.lutergs.upbeatclient.dto

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

class DateDeserializer: JsonDeserializer<LocalDate>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LocalDate {
        val dateString = p.text
        return LocalDate.of(
            dateString.substring(0, 4).toInt(),
            dateString.substring(4, 6).toInt(),
            dateString.substring(6, 8).toInt()
        )
    }
}

class TimeDeserializer: JsonDeserializer<LocalTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LocalTime {
        val dateString = p.text
        return LocalTime.of(
            dateString.substring(0, 2).toInt(),
            dateString.substring(2, 4).toInt(),
            dateString.substring(4, 6).toInt()
        )
    }
}

class DateWithHyphenDeserializer: JsonDeserializer<LocalDate>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LocalDate {
        val dateStrings = p.text.split("-")
        return LocalDate.of(
            dateStrings[0].toInt(),
            dateStrings[1].toInt(),
            dateStrings[2].toInt()
        )
    }
}

class NumberStringDeserializer: JsonDeserializer<Double>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Double {
        return p.text.toDouble()
    }
}


class MarketCodeDeserializer: JsonDeserializer<MarketCode>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MarketCode {
        return p.text.split("-").let {
            MarketCode(it[0], it[1])
        }
    }
}

class OffsetDateTimeDeserializer: JsonDeserializer<OffsetDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OffsetDateTime {
        return p.text.let { OffsetDateTime.parse(it) }
    }
}

class UuidDeserializer: JsonDeserializer<UUID>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UUID {
        return p.text.let { UUID.fromString(it) }
    }
}