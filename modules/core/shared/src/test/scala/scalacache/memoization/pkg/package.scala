package scalacache.memoization

import cats.effect.IO
import scalacache._

package object pkg {
  implicit var cache: Cache[IO, Int] = null

  def insidePackageObject(a: Int): Int =
    memoize(None) {
      123
    }.unsafeRunSync()

}
