package dev.lutergs.upbitclient.webclient

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.lutergs.upbitclient.api.Param
import java.math.BigInteger
import java.security.MessageDigest
import java.util.UUID

class TokenGenerator(
  private val accessKey: String,
  private val secretKey: String
) {
  private val algorithm = Algorithm.HMAC256(this.secretKey)
  private val messageDigest = MessageDigest.getInstance("SHA-512")

  fun createJWT(param: Param? = null): String {
    return JWT.create()
      .withClaim("access_key", accessKey)
      .withClaim("nonce", UUID.randomUUID().toString())
      .let { when (param != null) {
          true -> it
            .withClaim("query_hash", this.createQueryString(param))
            .withClaim("query_hash_alg", "SHA512")
          false -> it
      } }
      .sign(this.algorithm)
      .let { "Bearer $it" }
  }

  private fun createQueryString(param: Param): String {
    this.messageDigest.update(param.toJwtTokenString().toByteArray())
    return String.format("%0128x", BigInteger(1, this.messageDigest.digest()))
  }
}