package integrationtests

import java.util.UUID

import cats.effect.IO
import org.scalatest._
import net.spy.memcached.{AddrUtil, MemcachedClient}
import redis.clients.jedis.JedisPool

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.higherKinds
import scala.util.control.NonFatal
import scalacache._
import scalacache.caffeine.CaffeineCache
import scalacache.memcached.MemcachedCache
import scalacache.redis.RedisCache

class IntegrationTests extends FlatSpec with Matchers with BeforeAndAfterAll {

  private val memcachedClient = new MemcachedClient(AddrUtil.getAddresses("localhost:11211"))
  private val jedisPool       = new JedisPool("localhost", 6379)

  override def afterAll(): Unit = {
    memcachedClient.shutdown()
    jedisPool.close()
  }

  private def memcachedIsRunning: Boolean = {
    try {
      memcachedClient.get("foo")
      true
    } catch { case _: Exception => false }
  }

  private def redisIsRunning: Boolean = {
    try {
      val jedis = jedisPool.getResource()
      try {
        jedis.ping()
        true
      } finally {
        jedis.close()
      }
    } catch {
      case NonFatal(_) => false
    }
  }

  case class CacheBackend(name: String, cache: Cache[IO, String])

  private val caffeine = CacheBackend("Caffeine", CaffeineCache[IO, String])
  private val memcached: Seq[CacheBackend] =
    if (memcachedIsRunning) {
      Seq(
        {
          import scalacache.serialization.binary._
          CacheBackend("(Memcached) ⇔ (binary codec)", MemcachedCache[IO, String](memcachedClient))
        }, {
          import scalacache.serialization.circe._
          CacheBackend("(Memcached) ⇔ (circe codec)", MemcachedCache[IO, String](memcachedClient))
        }
      )
    } else {
      alert("Skipping Memcached integration tests because Memcached does not appear to be running on localhost.")
      Nil
    }

  private val redis: Seq[CacheBackend] =
    if (redisIsRunning)
      Seq(
        {
          import scalacache.serialization.binary._
          CacheBackend("(Redis) ⇔ (binary codec)", RedisCache[IO, String](jedisPool))
        }, {
          import scalacache.serialization.circe._
          CacheBackend("(Redis) ⇔ (circe codec)", RedisCache[IO, String](jedisPool))
        }
      )
    else {
      alert("Skipping Redis integration tests because Redis does not appear to be running on localhost.")
      Nil
    }

  val backends: List[CacheBackend] = List(caffeine) ++ memcached ++ redis

  for (CacheBackend(name, cache) <- backends) {

    s"$name ⇔ (cats-effect IO)" should "defer the computation and give the correct result" in {
      implicit val theCache: Cache[IO, String] = cache

      val key          = UUID.randomUUID().toString
      val initialValue = UUID.randomUUID().toString

      val program =
        for {
          _             <- put[IO, String](key)(initialValue)
          readFromCache <- get[IO, String](key)
          updatedValue = "prepended " + readFromCache.getOrElse("couldn't find in cache!")
          _                   <- put[IO, String](key)(updatedValue)
          finalValueFromCache <- get[IO, String](key)
        } yield finalValueFromCache

      checkComputationHasNotRun(key)

      val result: Option[String] = program.unsafeRunSync()
      assert(result.contains("prepended " + initialValue))
    }
  }

  private def checkComputationHasNotRun(key: String)(implicit cache: Cache[IO, String]): Unit = {
    Thread.sleep(1000)
    assert(scalacache.get[IO, String](key).unsafeRunSync().isEmpty)
  }

}
