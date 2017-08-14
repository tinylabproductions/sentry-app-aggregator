package com.tinylabproductions.sentry_app_aggregator

import play.api.libs.json.{Format, JsValue, Json}

package object data {
  implicit def customKeyFormat[K : Format, V : Format]: Format[Map[K, V]] = Format(
    (json: JsValue) => json.validate[Vector[(K, V)]].map(_.toMap),
    map => Json.toJson(map.toVector)
  )
}
