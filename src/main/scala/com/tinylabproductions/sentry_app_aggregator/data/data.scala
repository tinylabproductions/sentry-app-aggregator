package com.tinylabproductions.sentry_app_aggregator.data

import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.HttpMessage
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshal, Unmarshaller}
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.util.Try

case class AppKey(s: String) extends AnyVal
object AppKey {
  def apply(parts: Vector[String]): AppKey = apply(parts.mkString("|"))

  implicit val format: Format[AppKey] = Format(
    Reads.StringReads.map(apply),
    Writes.StringWrites.contramap(_.s)
  )

  implicit val unmarshaller: Unmarshaller[String, AppKey] =
    Unmarshaller.strict(apply)

  implicit val ordering: Ordering[AppKey] = Ordering.by((_: AppKey).s)
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
  def sentryRequestReads(appKeyTags: Vector[String]): Reads[AppData] = {
    val tags = JsPath \ "tags"
    val appKeyReads =
      appKeyTags
        .map(tag => (tags \ tag).readWithDefault("").map(Vector(_)))
        .reduceLeft { (a, b) =>
          for (aVec <- a; bVec <- b) yield aVec ++ bVec
        }
        .map(AppKey.apply)
    val releaseReads =
      // release from android comes in a format of your.package.name-version
      //
      // last always succeeds, even on an empty string:
      // > "".split("-").last => ""
      (JsPath \ "release")
        .read[String]
        .map(s => JsString(s.split("-").last))
        .andThen(VersionNumber.format)
    (appKeyReads and releaseReads)(AppData.apply _)
  }

  val gzipMessageUnmarshaller: Unmarshaller[HttpMessage, String] =
    Unmarshaller.withMaterializer { _ => implicit materializer => msg =>
      val `content-encoding` = msg.getHeader("Content-Encoding")
      if (`content-encoding`.isPresent && `content-encoding`.get().value() == "gzip") {
        val decompressedResponse = msg.entity.transformDataBytes(Gzip.decoderFlow)
        Unmarshal(decompressedResponse).to[String]
      } else {
        Unmarshal(msg).to[String]
      }
    }

  def sentryRequestRequestUnmarshaller(reads: Reads[AppData]): FromRequestUnmarshaller[AppData] = {
    def jsErrorToString(err: JsError) = {
      JsError.toFlatForm(err).flatMap { case (path, errors) =>
        errors.map { error => s"$error @ $path" }
      }.mkString("\n")
    }

    gzipMessageUnmarshaller.map { jsonS =>
      val either =
        Try(Json.parse(jsonS)).toEither
        .left.map(t => s"Failed to parse as JSON (${t.getMessage}):\n\n$jsonS")
        .flatMap { json =>
          json.validate(reads) match {
            case JsSuccess(value, _) => Right(value)
            case error: JsError => Left(
              s"""Failed to extract AppData from JSON:
               |
               |Errors:
               |${jsErrorToString(error)}
               |
               |Json:
               |$jsonS
               |
               |""".stripMargin
            )
        } }

      either match {
        case Left(error) =>
          throw new IllegalArgumentException(error)
        case Right(value) =>
          value
      }
    }
  }
}