package com.tinylabproductions.sentry_app_aggregator

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.typed.scaladsl.Actor
import akka.typed.{ActorSystem => TActorSystem}
import com.tinylabproductions.akka_http_daemon.AkkaHttpDaemon
import com.tinylabproductions.sentry_app_aggregator.actors.MainActor
import com.tinylabproductions.sentry_app_aggregator.data.{Cfg, Counters}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import configs.Result.{Failure, Success}
import configs.syntax._
import play.api.libs.json.{JsError, JsSuccess, Json}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main {
  def main(args: Array[String]): Unit = {
    implicit val log: Logger = Logger("main")

    ConfigFactory.load().extract[Cfg] match {
      case Success(cfg) =>
        readCounters(cfg)
      case Failure(error) =>
        log.error(s"Can't parse config file: ${error.messages}")
    }
  }

  def readCounters(cfg: Cfg)(implicit log: Logger): Unit = {
    val initialCountersContents =
      new String(Files.readAllBytes(cfg.counters.path), StandardCharsets.UTF_8)
    Json.parse(initialCountersContents).validate[Counters] match {
      case JsSuccess(counters, path @ _) =>
        run(cfg, counters)
      case JsError(errors) =>
        log.error(s"Errors while reading ${cfg.counters.path}: ${errors.mkString("\n")}")
    }
  }

  def run(cfg: Cfg, initialCounters: Counters)(implicit log: Logger): Unit = {
    val httpActorSystem = ActorSystem("http")
    val httpMaterializer = ActorMaterializer()(httpActorSystem)
    val http = Http(httpActorSystem)
    val mainActor = TActorSystem(
      "main",
      Actor.mutable[MainActor.Protocol] { ctx =>
        new MainActor(cfg.actor, initialCounters, log, ctx, http, httpMaterializer)
      }
    )

    val allRoutes =
      routes.ping.route(mainActor.narrow) ~
      routes.error.route(cfg.tags, cfg.proxy, mainActor.narrow)(
        httpActorSystem.scheduler, httpActorSystem.dispatcher
      )

    val _ = new AkkaHttpDaemon(
      http, cfg.http, allRoutes,
      afterBind = () => {},
      afterUnbind = () => {
        println(Await.result(mainActor.terminate(), Duration.Inf))
      }
    )
  }
}