package com.tinylabproductions.sentry_app_aggregator.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.typed.ActorRef
import com.tinylabproductions.sentry_app_aggregator.actors.MainActor
import com.tinylabproductions.sentry_app_aggregator.data.{AppData, AppKey, Cfg, VersionNumber}

object ping {
  def route(
    formFieldNames: Cfg.Names,
    sendTo: ActorRef[MainActor.PingReceived]
  ): Route = {
    val appKeyDirective =
      formFieldNames.appKey
      .map(name => formField(name ? "").map(Vector(_)))
      .reduceLeft((a, b) => (a & b).tmap { case (av, bv) => av ++ bv })
      .map(AppKey.apply)

    (
      path("ping") & post & appKeyDirective &
        formField(formFieldNames.versionNumber.as[VersionNumber])
    ) { (app, version) =>
      sendTo ! MainActor.PingReceived(AppData(app, version))
      complete("OK")
    }
  }
}