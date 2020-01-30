package scalacache.redis

import cats.effect.Async
import redis.clients.jedis._
import scalacache._
import scalacache.serialization.Codec

import scala.language.postfixOps

class RedisCacheSpec extends RedisCacheSpecBase with RedisTestUtil {

  type JClient = JedisClient
  type JPool   = JedisPool

  val withJedis = assumingRedisIsRunning _

  def constructCache[F[_]: Async, V](pool: JPool)(implicit codec: Codec[V]): CacheAlg[F, V] =
    new RedisCache[F, V](jedisPool = pool)

  def flushRedis(client: JClient): Unit = client.underlying.flushDB()

  runTestsIfPossible()

}
