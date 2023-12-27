package dev.lutergs.upbeatclient.api

import dev.lutergs.upbeatclient.webclient.Requester


/**
 * https://docs.upbit.com/docs/create-authorization-request 해당 규격 참고
 * */

interface Param {
    fun toParameterString(): String
    fun toJwtTokenString(): String
}

abstract class RequestDao(
    protected val requester: Requester
)