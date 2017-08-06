package com.tinylabproductions.sentry_app_aggregator.data

import org.specs2.mutable.Specification

object VersionNumberTest extends Specification {
  "ordering" >> {
    "1 < 2" >> (VersionNumber(1) should be < VersionNumber(2))
    "1 == 1" >> (VersionNumber(1) should_=== VersionNumber(1))
    "2 > 1" >> (VersionNumber(2) should be > VersionNumber(1))

    "1.0   < 1.1" >> (VersionNumber(1, 0) should be < VersionNumber(1, 1))
    "1     < 1.1" >> (VersionNumber(1) should be < VersionNumber(1, 1))
    "1     < 1.0.1" >> (VersionNumber(1) should be < VersionNumber(1, 0, 1))
    "1.1.2 < 1.2" >> (VersionNumber(1, 1, 2) should be < VersionNumber(1, 2))
    "1.0.2 < 1.0.3" >> (VersionNumber(1, 0, 2) should be < VersionNumber(1, 0, 3))
  }
}