package com.tinylabproductions.sentry_app_aggregator.data

import akka.http.scaladsl.unmarshalling.Unmarshaller

import scala.util.Try

case class AppKey(s: String) extends AnyVal

case class VersionNumber(parts: List[Int])
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

  implicit val unmarshaller: Unmarshaller[String, VersionNumber] =
    Unmarshaller.strict { str => fromString(str).get }
}

case class AppData(key: AppKey, versionNumber: VersionNumber)