package scalacache.redis

import cats.effect.Async
import redis.clients.jedis._

import scala.language.higherKinds
import scalacache.CacheConfig
import scalacache.serialization.Codec

/**
  * Thin wrapper around Jedis
  */
class RedisCache[F[_]: Async, V](val jedisPool: JedisPool)(implicit val config: CacheConfig, val codec: Codec[V])
    extends RedisCacheBase[F, V] {

  type JClient = Jedis

  protected def doRemoveAll(): F[Any] = Async[F].delay {
    val jedis = jedisPool.getResource()
    try {
      jedis.flushDB()
    } finally {
      jedis.close()
    }
  }

}

object RedisCache {

  /**
    * Create a Redis client connecting to the given host and use it for caching
    */
  def apply[F[_]: Async, V](host: String, port: Int)(implicit config: CacheConfig, codec: Codec[V]): RedisCache[F, V] =
    apply(new JedisPool(host, port))

  /**
    * Create a cache that uses the given Jedis client pool
    * @param jedisPool a Jedis pool
    */
  def apply[F[_]: Async, V](jedisPool: JedisPool)(implicit config: CacheConfig, codec: Codec[V]): RedisCache[F, V] =
    new RedisCache[F, V](jedisPool)

}
