package com.tinylabproductions.sentry_app_aggregator.data

import java.net.URI
import java.nio.file.Path

import akka.http.scaladsl.model.Uri
import com.tinylabproductions.akka_http_daemon.AkkaHttpDaemon
import com.tinylabproductions.sentry_app_aggregator.data.Cfg._

import scala.concurrent.duration.FiniteDuration

case class Cfg(
  http: AkkaHttpDaemon.Config, counters: Cfg.Counters, actor: MainActor, tags: Tags, proxy: Proxy
)
object Cfg {
  case class Counters(path: Path)
  case class Tags(appNameTag: String, versionNumberTag: String)
  case class Proxy(timeout: FiniteDuration)
  case class MainActor(pingsForVersionSwitch: Int, sentryUrl: URI) {
    val sentryUri: Uri = Uri(sentryUrl.toString)
  }
}