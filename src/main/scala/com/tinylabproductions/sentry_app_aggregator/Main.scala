package com.tinylabproductions.sentry_app_aggregator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, NoSuchFileException}

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.AskPattern._
import akka.typed.{ActorSystem => TActorSystem}
import akka.util.Timeout
import com.tinylabproductions.akka_http_daemon.AkkaHttpDaemon
import com.tinylabproductions.sentry_app_aggregator.actors.MainActor
import com.tinylabproductions.sentry_app_aggregator.data.{Cfg, Counters}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import configs.Result.{Failure, Success}
import configs.syntax._
import play.api.libs.json.{JsError, JsSuccess, Json}

import scala.concurrent.Await
import scala.concurrent.duration._
import Cfg.uriHostConfigs

object Main {
  def main(args: Array[String]): Unit = {
    implicit val log: Logger = Logger("main")

    ConfigFactory.load().extract[Cfg] match {
      case Success(cfg) =>
        readCounters(cfg)
      case Failure(error) =>
        log.error(s"Can't parse config file:\n${error.messages.mkString("\n")}")
    }
  }

  def readCounters(cfg: Cfg)(implicit log: Logger): Unit = {
    val counters =
      try {
        val contents = new String(Files.readAllBytes(cfg.counters.path), StandardCharsets.UTF_8)
        Json.parse(contents).validate[Counters] match {
          case JsSuccess(value, path @ _) =>
            value
          case JsError(errors) =>
            log.error(s"Errors while reading ${cfg.counters.path}: ${errors.mkString("\n")}")
            sys.exit(1)
        }
      }
      catch {
        case _: NoSuchFileException =>
          Counters.empty
      }

    run(cfg, counters)
  }

  def run(cfg: Cfg, initialCounters: Counters)(implicit log: Logger): Unit = {
    val httpActorSystem = ActorSystem("http")
    val httpMaterializer = ActorMaterializer()(httpActorSystem)
    val http = Http(httpActorSystem)

    val mainActor = TActorSystem(
      Actor.mutable[MainActor.Protocol] { ctx =>
        new MainActor(cfg.actor, initialCounters, log, ctx, http, httpMaterializer)
      },
      "main"
    )

    val allRoutes = logRequestResult("main-route") {
      routes.ping.route(cfg.appKey.pingFormFields, mainActor.narrow) ~
      routes.status.route(
        mainActor.narrow, Timeout(5.minutes), httpActorSystem.scheduler, httpActorSystem.dispatcher
      ) ~
      // This needs to be the last route
      routes.catchAll.route(cfg.appKey.proxyTags, cfg.proxy, mainActor.narrow)(
        httpActorSystem.scheduler, httpActorSystem.dispatcher
      )
    }

    val _ = new AkkaHttpDaemon(
      http, AkkaHttpDaemon.Config(cfg.http), allRoutes,
      afterBind = () => {},
      afterUnbind = () => {
        println("Requesting current counters...")
        val countersFuture = {
          implicit val timeout: Timeout = Timeout(1.minute)
          implicit val scheduler: Scheduler = mainActor.scheduler
          mainActor ? MainActor.GetCounters
        }
        val counters = Await.result(countersFuture, Duration.Inf)
        println("Writing current counters...")
        Files.write(cfg.counters.path, Json.toBytes(Json.toJson(counters)))
        println("Terminating main actor...")
        println(Await.result(mainActor.terminate(), Duration.Inf))
        println("After unbind done.")
      }
    )
  }
}