package com.tinylabproductions.sentry_app_aggregator.actors

import akka.http.scaladsl.model.HttpRequest
import akka.typed.Behavior
import akka.typed.scaladsl.Actor
import com.tinylabproductions.sentry_app_aggregator.data.{AppData, Counters}

object MainActor {
  sealed trait Protocol
  case class PingReceived(appData: AppData) extends Protocol
  case class ErrorReceived(appData: AppData, request: HttpRequest) extends Protocol

  case class Args(
    pingsForVersionSwitch: Int, counters: Counters
  )

  class Actor(args: Args) extends Actor.MutableBehavior[Protocol] {
    val pingsForVersionSwitch = args.pingsForVersionSwitch
    private[this] var counters = args.counters

    override def onMessage(msg: Protocol): Behavior[Protocol] =
      msg match {
        case PingReceived(appData) =>
          counters += (appData, pingsForVersionSwitch)
          Actor.same
        case ErrorReceived(appData, request) =>
          if (counters.shouldTransmit(appData)) {
            ???
          }
          Actor.same
      }
  }

  def behaviour(args: Args): Behavior[Protocol] = Actor.mutable(_ => new Actor(args))
}