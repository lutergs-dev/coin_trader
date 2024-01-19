package dev.lutergs.santa.trade.domain

data class KubernetesInfo (
  val namespace: String,
  val imagePullSecretName: String,
  val imagePullPolicy: String,
  val imageName: String,
  val envSecretName: String
)