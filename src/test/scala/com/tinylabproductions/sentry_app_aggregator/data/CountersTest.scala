package com.tinylabproductions.sentry_app_aggregator.data

import org.specs2.mutable.Specification

object CountersTest extends Specification {
  "app counters" >> {
    import Counters.AppCounters

    val cnt = AppCounters(
      Map(VersionNumber(1, 2) -> 3),
      Some(VersionNumber(1, 0))
    )

    "#apply" >> {
      "should return the number if it exists" >> {
        cnt(VersionNumber(1, 2)) should_=== 3
      }
      "should return 0 if it does not exist" >> {
        cnt(VersionNumber(1)) should_=== 0
      }
    }

    "#shouldPass" >> {
      "should return true" >> {
        "if number is the same" >> {
          cnt.shouldPass(VersionNumber(1, 0)) should beTrue
        }

        "if number is greater" >> {
          cnt.shouldPass(VersionNumber(1, 1)) should beTrue
        }

        "if number is none" >> {
          cnt.copy(latest = None).shouldPass(VersionNumber(0)) should beTrue
        }
      }

      "should return false" >> {
        "if number is smaller" >> {
          cnt.shouldPass(VersionNumber(0, 9, 9)) should beFalse
        }
      }
    }

    "#+" >> {
      "should increase count" >> {
        "when previous exists" >> {
          (cnt + (VersionNumber(1, 2), 0)).counters(VersionNumber(1, 2)) should_=== 4
        }

        "when previous does not exist" >> {
          (cnt + (VersionNumber(1, 0), 0)).counters(VersionNumber(1, 0)) should_=== 1
        }
      }

      "latest version switching" >> {
        "should not switch to older version" >> pending

        "should not switch to newer version if not enough pings" >> pending

        "should switch to given version if previous latest wasn't known" >> pending

        "should switch to newer version if enough pings" >> pending
      }
    }
  }
}