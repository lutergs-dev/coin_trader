package dev.lutergs.upbitclient.dto


@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class FieldDescription(val description: String)