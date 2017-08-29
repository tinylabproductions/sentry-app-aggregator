package com.tinylabproductions.sentry_app_aggregator.data

import java.nio.file.Path

import akka.http.scaladsl.model.Uri
import com.tinylabproductions.akka_http_daemon.AkkaHttpDaemon
import com.tinylabproductions.sentry_app_aggregator.data.Cfg.MainActor.SentryUrl
import com.tinylabproductions.sentry_app_aggregator.data.Cfg._
import configs.Configs

import scala.concurrent.duration.FiniteDuration

case class Cfg(
  http: AkkaHttpDaemon.HttpConfig, counters: Cfg.Counters, actor: MainActor, appKey: Cfg.AppKey,
  proxy: Proxy
)
object Cfg {
  case class Counters(path: Path)
  case class Proxy(timeout: FiniteDuration)
  case class MainActor(pingsForVersionSwitch: Int, sentryUrl: SentryUrl)
  object MainActor {
    case class SentryUrl(scheme: String, host: Uri.Host, port: Int)
  }

  case class AppKey(pingFormFields: AppKey.Names, proxyAppKeyTags: Vector[String]) {
    require(proxyAppKeyTags.nonEmpty, "App key tags can't be empty!")
  }
  object AppKey {
    case class Names(appKey: Vector[String], versionNumber: String) {
      require(appKey.nonEmpty, "App key names can't be empty!")
    }
  }

  implicit val uriHostConfigs: Configs[Uri.Host] = Configs.stringConfigs.map(Uri.Host(_))
}