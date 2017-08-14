package com.tinylabproductions.sentry_app_aggregator.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.typed.ActorRef
import com.tinylabproductions.sentry_app_aggregator.actors.MainActor
import com.tinylabproductions.sentry_app_aggregator.data.{AppData, AppKey, VersionNumber}

object ping {
  def route(sendTo: ActorRef[MainActor.PingReceived]): Route =
    (post & parameters(("app".as[AppKey], "version".as[VersionNumber]))) { (app, version) =>
      sendTo ! MainActor.PingReceived(AppData(app, version))
      complete("OK")
    }
}