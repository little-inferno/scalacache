package issue42

import cats.effect.IO
import org.scalatest.{FlatSpec, Matchers}

import scala.util.Random

class Issue42Spec extends FlatSpec with Matchers {

  case class User(id: Int, name: String)

  import scalacache._
  import memoization._

  import concurrent.duration._
  import scala.language.postfixOps

  implicit val cache: Cache[IO, User] = new MockCache()

  def generateNewName() = Random.alphanumeric.take(10).mkString

  def getUser(id: Int)(implicit flags: Flags): User =
    memoize(None) {
      User(id, generateNewName())
    }.unsafeRunSync()

  def getUserWithTtl(id: Int)(implicit flags: Flags): User =
    memoize(Some(1 days)) {
      User(id, generateNewName())
    }.unsafeRunSync()

  "memoize without TTL" should "respect implicit flags" in {
    val user1before = getUser(1)
    val user1after = {
      implicit val flags = Flags(readsEnabled = false)
      getUser(1)
    }
    user1before should not be user1after
  }

  "memoize with TTL" should "respect implicit flags" in {
    val user1before = getUserWithTtl(1)
    val user1after = {
      implicit val flags = Flags(readsEnabled = false)
      getUserWithTtl(1)
    }
    user1before should not be user1after
  }

}
