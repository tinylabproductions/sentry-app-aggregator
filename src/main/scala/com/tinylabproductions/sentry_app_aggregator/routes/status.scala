package com.tinylabproductions.sentry_app_aggregator.routes

import akka.actor.Scheduler
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.typed.ActorRef
import akka.typed.scaladsl.AskPattern._
import akka.util.Timeout
import build_info.BuildInfo
import com.tinylabproductions.sentry_app_aggregator.actors.MainActor
import com.tinylabproductions.sentry_app_aggregator.data.AppKey
import com.tinylabproductions.sentry_app_aggregator.data.Counters.AppCounters

import scala.concurrent.ExecutionContext

object status {
  def route(
    actor: ActorRef[MainActor.GetCounters],
    timeout: Timeout, scheduler: Scheduler, executionContext: ExecutionContext
  ): Route = {
    implicit val _timeout: Timeout = timeout
    implicit val _scheduler: Scheduler = scheduler
    implicit val _ec: ExecutionContext = executionContext

    path("status") {
      val future = (actor ? MainActor.GetCounters).map { counters =>
        val rendering = counters.counters.toVector.sortBy(_._1)

        s"""$BuildInfo
           |
           |################# Latest App Versions #################
           |
           |${latestAppVersions(rendering)}
           |
           |#################     All Versions    #################
           |
           |${allInfo(rendering)}
         """.stripMargin
      }
      complete(future)
    }
  }

  type CountersForRendering = Vector[(AppKey, AppCounters)]

  def latestAppVersions(counters: CountersForRendering): String = {
    counters.map { case (appKey, appCounters) =>
      s"${appKey.s}\t\t\t${appCounters.latest}"
    }.mkString("\n")
  }

  def allInfo(counters: CountersForRendering): String = {
    counters.map { case (appKey, appCounters) =>
      val body = appCounters.counters.toVector.sortBy(_._1).map { case (versionNumber, count) =>
        s"$versionNumber\t\t = $count"
      }.mkString("\n")
      s"""### ${appKey.s} (latest = ${appCounters.latest})
         |
         |$body""".stripMargin
    }.mkString("\n\n")
  }
}
