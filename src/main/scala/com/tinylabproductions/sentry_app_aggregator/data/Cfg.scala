package com.tinylabproductions.sentry_app_aggregator.data

import java.nio.file.Path

import akka.http.scaladsl.model.Uri
import com.tinylabproductions.akka_http_daemon.AkkaHttpDaemon
import com.tinylabproductions.sentry_app_aggregator.data.Cfg.MainActor.SentryUrl
import com.tinylabproductions.sentry_app_aggregator.data.Cfg._
import configs.Configs

import scala.concurrent.duration.FiniteDuration

case class Cfg(
  http: AkkaHttpDaemon.HttpConfig, counters: Cfg.Counters, actor: MainActor, tags: Tags, proxy: Proxy
)
object Cfg {
  case class Counters(path: Path)
  case class Tags(appKey: Vector[String], versionNumber: String) {
    require(appKey.nonEmpty, "App key tags can't be empty!")
  }
  case class Proxy(timeout: FiniteDuration)
  case class MainActor(pingsForVersionSwitch: Int, sentryUrl: SentryUrl)
  object MainActor {
    case class SentryUrl(scheme: String, host: Uri.Host, port: Int)
  }

  implicit val uriHostConfigs: Configs[Uri.Host] = Configs.stringConfigs.map(Uri.Host(_))
}