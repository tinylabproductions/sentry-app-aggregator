package com.tinylabproductions.sentry_app_aggregator.data

import math.Ordering.Implicits._
import com.tinylabproductions.sentry_app_aggregator.data.Counters.PerVersionNumber

object Counters {
  case class PerVersionNumber(map: Map[VersionNumber, Int], latest: Option[VersionNumber]) {
    def apply(versionNumber: VersionNumber): Int =
      map.getOrElse(versionNumber, 0)

    def +(versionNumber: VersionNumber, pingsToSwitchLatestVersion: Int): PerVersionNumber = {
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
        map = map.updated(versionNumber, newCount),
        latest = newLatest
      )
    }

    def shouldPass(versionNumber: VersionNumber): Boolean =
      latest.fold(true)(versionNumber >= _)
  }
  object PerVersionNumber {
    val empty = apply(Map.empty, None)
  }

  val empty = apply(Map.empty)
}
case class Counters(counters: Map[AppKey, Counters.PerVersionNumber]) {
  def forApp(appKey: AppKey): PerVersionNumber =
    counters.getOrElse(appKey, PerVersionNumber.empty)

  def +(appData: AppData, pingsToSwitchLatestVersion: Int): Counters = {
    val app = forApp(appData.key) + (appData.versionNumber, pingsToSwitchLatestVersion)
    copy(counters = counters updated (appData.key, app))
  }

  def shouldTransmit(appData: AppData): Boolean =
    forApp(appData.key).shouldPass(appData.versionNumber)
}