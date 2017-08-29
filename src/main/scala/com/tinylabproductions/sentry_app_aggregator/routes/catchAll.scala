package com.tinylabproductions.sentry_app_aggregator.routes

import akka.actor.Scheduler
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.typed.ActorRef
import akka.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.tinylabproductions.sentry_app_aggregator.actors.MainActor
import com.tinylabproductions.sentry_app_aggregator.data.{AppData, Cfg}

import scala.concurrent.{ExecutionContext, Future}

object catchAll {
  def route(
    appKeyTags: Vector[String], proxy: Cfg.Proxy,
    sendTo: ActorRef[MainActor.ErrorReceived]
  )(implicit scheduler: Scheduler, ec: ExecutionContext): Route = {
    val reads = AppData.sentryRequestReads(appKeyTags)
    val unmarshaller = AppData.sentryRequestRequestUnmarshaller(reads)
    implicit val timeout: Timeout = Timeout(proxy.timeout)

    (post & extractRequest & entity(unmarshaller)) { (request, appData) =>
      val future =
        (sendTo ? { (r: ActorRef[Either[Throwable, HttpResponse]]) =>
          MainActor.ErrorReceived(appData, request, r)
        })
        .flatMap {
          case Left(err) => Future.failed(err)
          case Right(response) => Future.successful(response)
        }
      complete(future)
    }
  }
}
