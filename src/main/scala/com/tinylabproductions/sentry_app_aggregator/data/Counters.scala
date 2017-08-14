package com.tinylabproductions.sentry_app_aggregator.data

import com.tinylabproductions.sentry_app_aggregator.data.Counters.AppCounters
import play.api.libs.json._

import scala.math.Ordering.Implicits._

object Counters {
  case class AppCounters(counters: Map[VersionNumber, Int], latest: Option[VersionNumber]) {
    def apply(versionNumber: VersionNumber): Int =
      counters.getOrElse(versionNumber, 0)

    def +(versionNumber: VersionNumber, pingsToSwitchLatestVersion: Int): AppCounters = {
      val newCount = apply(versionNumber) + 1
      val newLatest = latest match {
        case Some(currentLatestVersion) if
          newCount >= pingsToSwitchLatestVersion
          && versionNumber > currentLatestVersion
        =>
          Some(versionNumber)
        case orig @ Some(_) =>
          orig
        case None =>
          Some(versionNumber)
      }
      copy(
        counters = counters.updated(versionNumber, newCount),
        latest = newLatest
      )
    }

    def shouldPass(versionNumber: VersionNumber): Boolean =
      latest.fold(true)(versionNumber >= _)
  }
  object AppCounters {
    val empty = apply(Map.empty, None)

    implicit val format: OFormat[AppCounters] = Json.format[AppCounters]
  }

  val empty = apply(Map.empty)

  implicit val format: OFormat[Counters] = Json.format[Counters]
}
case class Counters(counters: Map[AppKey, Counters.AppCounters]) {
  def forApp(appKey: AppKey): AppCounters =
    counters.getOrElse(appKey, AppCounters.empty)

  def +(appData: AppData, pingsToSwitchLatestVersion: Int): Counters = {
    val app = forApp(appData.key) + (appData.versionNumber, pingsToSwitchLatestVersion)
    copy(counters = counters updated (appData.key, app))
  }

  def shouldTransmit(appData: AppData): Boolean =
    forApp(appData.key).shouldPass(appData.versionNumber)
}