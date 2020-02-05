package scalacache

import cats.effect.IO
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Success, Try}

class AbstractCacheSpec extends FlatSpec with Matchers with BeforeAndAfter {

  val cache = new LoggingMockCache[IO, String]

  before {
    cache.mmap.clear()
    cache.reset()
  }

  behavior of "#get"

  it should "call doGet on the concrete cache" in {
    cache.get("foo")
    cache.getCalledWithArgs(0) should be("foo")
  }

  it should "use the CacheKeyBuilder to build the cache key" in {
    cache.get("foo", 123)
    cache.getCalledWithArgs(0) should be("foo:123")
  }

  it should "not call doGet on the concrete cache if cache reads are disabled" in {
    implicit val flags: Flags = Flags(readsEnabled = false)
    cache.get("foo")
    cache.getCalledWithArgs should be(empty)
  }

  it should "conditionally call doGet on the concrete cache depending on the readsEnabled flag" in {
    def possiblyGetFromCache(key: String): Unit = {
      implicit def flags: Flags = Flags(readsEnabled = (key == "foo"))
      cache.get(key)
    }
    possiblyGetFromCache("foo")
    possiblyGetFromCache("bar")
    cache.getCalledWithArgs.size should be(1)
    cache.getCalledWithArgs(0) should be("foo")
  }

  behavior of "#put"

  it should "call doPut on the concrete cache" in {
    cache.put("foo")("bar", Some(1 second))
    cache.putCalledWithArgs(0) should be(("foo", "bar", Some(1 second)))
  }

  it should "not call doPut on the concrete cache if cache writes are disabled" in {
    implicit val flags: Flags = Flags(writesEnabled = false)
    cache.put("foo")("bar", Some(1 second))
    cache.putCalledWithArgs should be(empty)
  }

  it should "call doPut with no TTL if the provided TTL is not finite" in {
    cache.put("foo")("bar", Some(Duration.Inf))
    cache.putCalledWithArgs(0) should be(("foo", "bar", None))
  }

  behavior of "#remove"

  it should "call doRemove on the concrete cache" in {
    cache.remove("baz")
    cache.removeCalledWithArgs(0) should be("baz")
  }

  it should "concatenate key parts correctly" in {
    cache.remove("hey", "yeah")
    cache.removeCalledWithArgs(0) should be("hey:yeah")
  }

  behavior of "#caching and flags"

  it should "run the block and cache its result if cache reads are disabled" in {
    cache.mmap.put("myKey", "value from cache")

    implicit val flags: Flags = Flags(readsEnabled = false)

    var called = false
    val result = cache
      .caching("myKey")(None) {
        called = true
        "result of block"
      }
      .unsafeRunSync()

    cache.getCalledWithArgs should be(empty)
    called should be(true)
    result should be("result of block")

    cache.putCalledWithArgs(0) should be("myKey", "result of block", None)
  }

  it should "run the block but not cache its result if cache writes are disabled" in {
    implicit val flags: Flags = Flags(writesEnabled = false)

    var called = false
    val result = cache
      .caching("myKey")(None) {
        called = true
        "result of block"
      }
      .unsafeRunSync()

    cache.getCalledWithArgs(0) should be("myKey")
    called should be(true)
    cache.putCalledWithArgs should be(empty)
    result should be("result of block")
  }

  behavior of "#cachingF and flags"

  it should "run the block and cache its result if cache reads are disabled" in {
    cache.mmap.put("myKey", "value from cache")

    implicit val flags: Flags = Flags(readsEnabled = false)

    var called = false
    val result = cache
      .cachingF("myKey")(None) {
        IO {
          called = true
          "result of block"
        }
      }
      .unsafeRunSync()

    cache.getCalledWithArgs should be(empty)
    called should be(true)
    result should be("result of block")

    cache.putCalledWithArgs(0) should be("myKey", "result of block", None)
  }

  it should "run the block but not cache its result if cache writes are disabled" in {
    implicit val flags: Flags = Flags(writesEnabled = false)

    var called = false
    val result = cache
      .cachingF("myKey")(None) {
        IO {
          called = true
          "result of block"
        }
      }
      .unsafeRunSync()

    cache.getCalledWithArgs(0) should be("myKey")
    called should be(true)
    cache.putCalledWithArgs should be(empty)
    result should be("result of block")
  }

}
