package dev.lutergs.upbeatclient.webclient

import dev.lutergs.upbeatclient.api.Param
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

class Requester(
    baseUrl: String,
    private val tokenGenerator: TokenGenerator
) {
    private val webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Content-Type", "application/json")
        .build()

    fun <T: Any> getSingle(path: String, param: Param? = null, responseClass: KClass<T>): Mono<T> {
        return this.webClient.get()
            .uri { uri ->
                uri.path(path)
                    .let { builder ->
                        when {
                            param == null -> builder
                            else -> builder.query(param.toParameterString())
                        } }
                    .build() }
            .header("Authorization", this.tokenGenerator.createJWT(param))
            .retrieve()
            .bodyToMono(responseClass.java)
    }

    fun <T: Any> getMany(path: String, param: Param? = null, responseClass: KClass<T>): Flux<T> {
        return this.webClient.get()
            .uri { uri ->
                uri.path(path)
                    .let { builder ->
                        when {
                            param == null -> builder
                            else -> builder.query(param.toParameterString())
                        } }
                    .build() }
            .header("Authorization", this.tokenGenerator.createJWT(param))
            .retrieve()
            .bodyToFlux(responseClass.java)
    }

    fun <T: Any> deleteSingle(path: String, param: Param? = null, responseClass: KClass<T>): Mono<T> {
        return this.webClient.delete()
            .uri { uri ->
                uri.path(path)
                    .let { builder ->
                        when {
                            param == null -> builder
                            else -> builder.query(param.toParameterString())
                        } }
                    .build() }
            .header("Authorization", this.tokenGenerator.createJWT(param))
            .retrieve()
            .bodyToMono(responseClass.java)
    }

    fun <T: Any> postSingle(path: String, param: Param? = null, responseClass: KClass<T>): Mono<T> {
        return this.webClient.post()
            .uri { uri ->
                uri.path(path)
                    .let { builder ->
                        when {
                            param == null -> builder
                            else -> builder.query(param.toParameterString())
                        } }
                    .build() }
            .header("Authorization", this.tokenGenerator.createJWT(param))
            .retrieve()
            .bodyToMono(responseClass.java)
    }
}