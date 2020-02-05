package scalacache.redis

import cats.effect.Async
import redis.clients.jedis._
import scalacache._
import scalacache.serialization.Codec

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class RedisClusterCacheSpec extends RedisCacheSpecBase with RedisTestUtil {

  type JClient = JedisClusterClient
  type JPool   = JedisCluster

  override val withJedis = assumingRedisClusterIsRunning _

  def constructCache[F[_]: Async, V](jedisCluster: JedisCluster)(implicit codec: Codec[V]): CacheAlg[F, V] =
    new RedisClusterCache[F, V](jedisCluster)

  def flushRedis(client: JClient): Unit =
    client.underlying.getClusterNodes.asScala.mapValues(_.getResource.flushDB())

  def assumingRedisClusterIsRunning(f: (JPool, JClient) => Unit): Unit = {
    Try {
      val jedisCluster = new JedisCluster(
        Set(
          new HostAndPort("localhost", 7000),
          new HostAndPort("localhost", 7001),
          new HostAndPort("localhost", 7002),
          new HostAndPort("localhost", 7003),
          new HostAndPort("localhost", 7004),
          new HostAndPort("localhost", 7005)
        ).asJava
      )

      if (jedisCluster.getClusterNodes.asScala.isEmpty)
        throw new IllegalStateException("No connections initialized")
      else
        jedisCluster.getClusterNodes.asScala.mapValues(_.getResource.ping())

      (jedisCluster, new JedisClusterClient(jedisCluster))
    } match {
      case Failure(_)              => alert("Skipping tests because it does not appear Redis Cluster is running on localhost.")
      case Success((pool, client)) => f(pool, client)
    }
  }

  runTestsIfPossible()

}
