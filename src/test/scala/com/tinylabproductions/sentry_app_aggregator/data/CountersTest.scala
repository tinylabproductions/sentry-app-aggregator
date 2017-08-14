package com.tinylabproductions.sentry_app_aggregator.data

import org.specs2.mutable.Specification

object CountersTest extends Specification {
  "app counters" >> {
    import Counters.AppCounters
    
    val v1 = VersionNumber(1, 0)
    val v1_2 = VersionNumber(1, 2)
    val v1_3 = VersionNumber(1, 3)

    val cnt = AppCounters(
      Map(v1_2 -> 3),
      Some(v1)
    )

    "#apply" >> {
      "should return the number if it exists" >> {
        cnt(v1_2) should_=== 3
      }
      "should return 0 if it does not exist" >> {
        cnt(v1) should_=== 0
      }
    }

    "#shouldPass" >> {
      "should return true" >> {
        "if number is the same" >> {
          cnt.shouldPass(v1) should beTrue
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
          (cnt + (v1_2, 0)).counters(v1_2) should_=== 4
        }

        "when previous does not exist" >> {
          (cnt + (v1, 0)).counters(v1) should_=== 1
        }
      }

      "latest version switching" >> {
        val orig = cnt.latest

        "should not switch to older version" >> {
          (0 until 100).foldLeft(cnt) { (cnt, _) => cnt + (v1, 0) }.latest should_=== orig
        }

        "should not switch to newer version if not enough pings" >> {
          val pings = 3
          (cnt + (v1_3, pings) + (v1_3, pings)).latest should_=== orig
        }

        "should switch to given version if previous latest wasn't known" >> {
          (cnt.copy(latest = None) + (v1_3, 100)).latest should_=== Some(v1_3)
        }

        "should switch to newer version if enough pings" >> {
          val pings = 2
          (cnt + (v1_3, pings) + (v1_3, pings)).latest should_=== Some(v1_3)
        }
      }
    }
  }
}