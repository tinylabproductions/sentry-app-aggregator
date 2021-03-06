package com.tinylabproductions.sentry_app_aggregator.actors

import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.typed.scaladsl.{Actor, ActorContext}
import akka.typed.{ActorRef, Behavior}
import com.tinylabproductions.sentry_app_aggregator.actors.MainActor._
import com.tinylabproductions.sentry_app_aggregator.data.{AppData, Cfg, Counters}
import com.typesafe.scalalogging.Logger

import scala.util.{Failure, Success}

object MainActor {
  sealed trait Protocol
  case class PingReceived(appData: AppData) extends Protocol
  case class ErrorReceived(
    appData: AppData, request: HttpRequest, replyTo: ActorRef[Either[Throwable, HttpResponse]]
  ) extends Protocol
  case class GetCounters(replyTo: ActorRef[Counters]) extends Protocol
}
class MainActor(
  config: Cfg.MainActor, initialCounters: Counters, log: Logger, ctx: ActorContext[Protocol],
  http: HttpExt, materializer: Materializer
) extends Actor.MutableBehavior[Protocol] {
  private[this] var counters = initialCounters

  import ctx.executionContext
  private[this] implicit val _materializer: Materializer = materializer

  private[this] def pingReceived(appData: AppData): Unit = {
    counters += (appData, config.pingsForVersionSwitch)
  }

  override def onMessage(msg: Protocol): Behavior[Protocol] =
    msg match {
      case PingReceived(appData) =>
        pingReceived(appData)
        Actor.same
      case ErrorReceived(appData, request @ _, replyTo) =>
        pingReceived(appData)

        if (counters.shouldTransmit(appData)) {
          val rewrittenRequest =
            request
              .withUri(
                request.uri
                  .withScheme(config.sentryUrl.scheme)
                  .withAuthority(config.sentryUrl.host, config.sentryUrl.port)
              )
              .withHeaders(request.headers.filterNot { h =>
                // Filter out headers which are added by forward proxy or akka
                (h is "host") || (h is "remote-address") || (h is "timeout-access")
              })
          log.debug(
            s"""Proxying request for $appData,
               |original  = $request,
               |rewritten = $rewrittenRequest
               |""".stripMargin
          )
          http.singleRequest(rewrittenRequest).onComplete {
            case Success(response) =>
              replyTo ! Right(response)
            case Failure(err) =>
              log.warn(s"Proxying request for $appData ($rewrittenRequest) failed", err)
              replyTo ! Left(err)
          }
        }
        else {
          log.debug(s"Request for $appData filtered out (request=$request)")
        }
        Actor.same
      case GetCounters(replyTo) =>
        replyTo ! counters
        Actor.same
    }
}