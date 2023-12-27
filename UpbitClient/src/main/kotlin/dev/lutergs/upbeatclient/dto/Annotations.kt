package dev.lutergs.upbeatclient.dto


@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class FieldDescription(val description: String)