package com.tinylabproductions.sentry_app_aggregator.data

import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromRequestUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.util.Try

case class AppKey(s: String) extends AnyVal
object AppKey {
  implicit val format: Format[AppKey] = Format(
    Reads.StringReads.map(apply),
    Writes.StringWrites.contramap(_.s)
  )

  implicit val unmarshaller: Unmarshaller[String, AppKey] =
    Unmarshaller.strict(apply)
}

case class VersionNumber(parts: List[Int]) {
  def asString: String = parts.mkString(".")
  override def toString: String = asString
}
object VersionNumber {
  def apply(parts: Int*): VersionNumber = apply(parts.toList)
  def fromString(s: String): Try[VersionNumber] =
    Try(apply(s.split('.').map(_.toInt)(collection.breakOut): List[Int]))

  implicit val ordering: Ordering[VersionNumber] = {
    val zeroList = List(0)

    def compare(num1: List[Int], num2: List[Int]): Boolean = {
      (num1, num2) match {
        case (Nil, Nil) => false
        case (v1, Nil) =>
          compare(v1, zeroList)
        case (Nil, v2) =>
          compare(zeroList, v2)
        case (v1 :: v1Rest, v2 :: v2Rest) =>
          val cmp = v1 compareTo v2
          if (cmp == 0) compare(v1Rest, v2Rest)
          else cmp < 0
      }
    }

    Ordering.fromLessThan { (vn1, vn2) =>
      compare(vn1.parts, vn2.parts)
    }
  }

  implicit val format: Format[VersionNumber] = Format(
    Reads { js =>
      Reads.StringReads.reads(js).flatMap { str =>
        fromString(str).fold(
          err => JsError(s"Can't read '$str' as version number: ${err.getMessage}"),
          JsSuccess(_)
        )
      }
    },
    Writes.StringWrites.contramap(_.asString)
  )

  implicit val unmarshaller: Unmarshaller[String, VersionNumber] =
    Unmarshaller.strict { str => fromString(str).get }
}

case class AppData(key: AppKey, versionNumber: VersionNumber)
object AppData {
  def sentryRequestReads(appNameTag: String, appVersionTag: String): Reads[AppData] = {
    val tags = JsPath \ "tags"
    (
      (tags \ appNameTag).read[AppKey] and
      (tags \ appVersionTag).read[VersionNumber]
    )(AppData.apply _)
  }

  def sentryRequestEntityUnmarshaller(reads: Reads[AppData]): FromEntityUnmarshaller[AppData] =
    PlayJsonSupport.unmarshaller(reads)

  def sentryRequestRequestUnmarshaller(reads: Reads[AppData]): FromRequestUnmarshaller[AppData] = {
    val um = sentryRequestEntityUnmarshaller(reads)
    Unmarshaller.withMaterializer { implicit ec => implicit materializer => request =>
      um(request.entity)
    }
  }
}