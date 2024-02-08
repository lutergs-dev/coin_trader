package dev.lutergs.santa.universal.oracle

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.core.convert.converter.Converter
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.domain.Persistable
import org.springframework.data.r2dbc.convert.EnumWriteSupport
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.io.Serializable
import java.math.BigDecimal
import java.time.OffsetDateTime

@Table(name = "coin_trade_order_list")
class TradeHistory: Persistable<String>, Serializable {
  @Column(value = "coin") var coin: String = ""

  // 매수가 최초로 완료되었을 때 DB 에 저장된다고 가정하고, buy 에 관련된 필드는 not-null 로 설정
  /* need index */@Id
  @Column(value = "buy_uuid") var buyId: String = ""
  @Column(value = "buy_price") var buyPrice: BigDecimal = BigDecimal.ZERO
  @Column(value = "buy_fee") var buyFee: BigDecimal = BigDecimal.ZERO
  @Column(value = "buy_volume") var buyVolume: BigDecimal = BigDecimal.ZERO
  @Column(value = "buy_won") var buyWon: BigDecimal = BigDecimal.ZERO
  @Column(value = "buy_place_at") var buyPlaceAt: OffsetDateTime = OffsetDateTime.now()
  @Column(value = "buy_finish_at") var buyFinishAt: OffsetDateTime = OffsetDateTime.now()

  /* need index */ @Column(value = "sell_uuid") var sellId: String? = null
  @Column(value = "sell_price") var sellPrice: BigDecimal? = null
  @Column(value = "sell_fee") var sellFee: BigDecimal? = null
  @Column(value = "sell_volume") var sellVolume: BigDecimal? = null
  @Column(value = "sell_won") var sellWon: BigDecimal? = null
  @Column(value = "sell_place_at") var sellPlaceAt: OffsetDateTime? = null
  @Column(value = "sell_finish_at") var sellFinishAt: OffsetDateTime? = null

  @Column(value = "profit") var profit: BigDecimal? = null
  @Column(value = "sell_type") var sellType: SellType = SellType.NULL

  @Transient
  @JsonIgnore
  private var newInstance: Boolean = false

  override fun getId(): String {
    return this.buyId
  }

  override fun isNew(): Boolean {
    return this.newInstance
  }

  fun setNewInstance() {
    this.newInstance = true
  }

  override fun toString(): String {
    return "TradeHistory(" +
      "coin=$coin, " +
      "buyId='$buyId', " +
      "buyPrice=$buyPrice, " +
      "buyFee=$buyFee, " +
      "buyVolume=$buyVolume, " +
      "buyWon=$buyWon, " +
      "buyPlaceAt=$buyPlaceAt, " +
      "buyFinishAt=$buyFinishAt, " +
      "sellId=$sellId, " +
      "sellPrice=$sellPrice, " +
      "sellFee=$sellFee, " +
      "sellVolume=$sellVolume, " +
      "sellWon=$sellWon, " +
      "sellPlaceAt=$sellPlaceAt, " +
      "sellFinishAt=$sellFinishAt, " +
      "profit=$profit, " +
      "sellType=$sellType" +
      ")"
  }
}

enum class SellType {
  PROFIT, LOSS, STOP_LOSS, STOP_PROFIT, TIMEOUT_PROFIT, TIMEOUT_LOSS, PLACED, NULL;

  fun isFinished(): Boolean {
    return when (this) {
      PROFIT, LOSS, STOP_PROFIT, STOP_LOSS, TIMEOUT_PROFIT, TIMEOUT_LOSS -> true
      else -> false
    }
  }

  fun toInfoString(): String {
    return when (this) {
      PROFIT -> "1차 익절"
      LOSS -> "1차 손절"
      STOP_PROFIT -> "2차 익절"
      STOP_LOSS -> "2차 손절"
      TIMEOUT_PROFIT -> "시간초과 익절"
      TIMEOUT_LOSS -> "시간초과 손절"
      PLACED -> TODO()
      NULL -> TODO()
    }
  }
}

// Enum을 String으로 변환하는 컨버터
@WritingConverter
class SellTypeToStringConverter : Converter<SellType, String> {
  override fun convert(source: SellType): String {
    return source.name
  }
}

// String을 Enum으로 변환하는 컨버터
@ReadingConverter
class StringToSellTypeConverter : Converter<String, SellType> {

  override fun convert(source: String): SellType {
    return SellType.valueOf(source)
  }
}