package scalacache.memoization

import cats.effect.IO
import org.scalatest._
import scalacache._

trait CacheKeySpecCommon extends Suite with Matchers with BeforeAndAfter {

  implicit def config: CacheConfig

  implicit lazy val cache: MockCache[IO, Int] = new MockCache[IO, Int]()

  before {
    cache.mmap.clear()
  }

  def checkCacheKey(expectedKey: String)(call: IO[Int]) {
    // Run the memoize block, putting some value into the cache
    val value = call.unsafeRunSync()

    // Check that the value is in the cache, with the expected key
    cache.get(expectedKey).unsafeRunSync() should be(Some(value))
  }

  def multipleArgLists(a: Int, b: String)(c: String, d: Int): IO[Int] =
    memoize(None) {
      123
    }

  case class CaseClass(a: Int) { override def toString = "custom toString" }
  def takesCaseClass(cc: CaseClass): IO[Int] =
    memoize(None) {
      123
    }

  def lazyArg(a: => Int): IO[Int] =
    memoize(None) {
      123
    }

  def functionArg(a: String => Int): IO[Int] =
    memoize(None) {
      123
    }

  def withExcludedParams(a: Int, @cacheKeyExclude b: String, c: String)(@cacheKeyExclude d: Int): IO[Int] =
    memoize(None) {
      123
    }

}

class AClass(implicit cache: Cache[IO, Int]) {
  def insideClass(a: Int): Int =
    memoize(None) {
      123
    }.unsafeRunSync()

  class InnerClass {
    def insideInnerClass(a: Int): Int =
      memoize(None) {
        123
      }.unsafeRunSync()
  }
  val inner = new InnerClass

  object InnerObject {
    def insideInnerObject(a: Int): Int =
      memoize(None) {
        123
      }.unsafeRunSync()
  }
}

trait ATrait {
  implicit val cache: Cache[IO, Int]

  def insideTrait(a: Int): Int =
    memoize(None) {
      123
    }.unsafeRunSync()
}

object AnObject {
  implicit var cache: Cache[IO, Int] = null
  def insideObject(a: Int): Int =
    memoize(None) {
      123
    }.unsafeRunSync()
}

class ClassWithConstructorParams(b: Int) {
  implicit var cache: Cache[IO, Int] = null
  def foo(a: Int): Int =
    memoize(None) {
      a + b
    }.unsafeRunSync()
}

class ClassWithExcludedConstructorParam(b: Int, @cacheKeyExclude c: Int) {
  implicit var cache: Cache[IO, Int] = null
  def foo(a: Int): Int =
    memoize(None) {
      a + b + c
    }.unsafeRunSync()
}
