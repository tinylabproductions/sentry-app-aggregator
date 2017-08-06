package com.tinylabproductions.sentry_app_aggregator.routes

import akka.http.scaladsl.server.Directives._
import com.tinylabproductions.sentry_app_aggregator.data.VersionNumber

object ping {
  def route() = post {
    parameters(
      "app",
      "version".as[VersionNumber]
    ) { (app, version) =>

    }
  }
}