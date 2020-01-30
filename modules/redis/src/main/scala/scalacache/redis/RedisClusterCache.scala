package scalacache.redis

import cats.effect.Async
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.exceptions.JedisClusterException
import scalacache.logging.Logger
import scalacache.redis.StringEnrichment._
import scalacache.serialization.Codec
import scalacache.{AbstractCache, CacheConfig}

import scala.concurrent.duration.{Duration, _}
import scala.language.higherKinds

class RedisClusterCache[F[_]: Async, V](val jedisCluster: JedisCluster)(
    implicit val config: CacheConfig,
    val codec: Codec[V]
) extends AbstractCache[F, V] {

  override protected final val logger = Logger.getLogger(getClass.getName)

  override protected def doGet(key: String): F[Option[V]] = Async[F].suspend {
    val bytes = jedisCluster.get(key.utf8bytes)
    val result: Codec.DecodingResult[Option[V]] = {
      if (bytes != null)
        codec.decode(bytes).right.map(Some(_))
      else
        Right(None)
    }
    result match {
      case Left(e) =>
        Async[F].raiseError(e)
      case Right(maybeValue) =>
        logCacheHitOrMiss(key, maybeValue)
        Async[F].pure(maybeValue)
    }
  }

  override protected def doPut(key: String, value: V, ttl: Option[Duration]): F[Any] = {
    Async[F].delay {
      val keyBytes   = key.utf8bytes
      val valueBytes = codec.encode(value)
      ttl match {
        case None                => jedisCluster.set(keyBytes, valueBytes)
        case Some(Duration.Zero) => jedisCluster.set(keyBytes, valueBytes)
        case Some(d) if d < 1.second =>
          if (logger.isWarnEnabled) {
            logger.warn(
              s"Because Redis (pre 2.6.12) does not support sub-second expiry, TTL of $d will be rounded up to 1 second"
            )
          }
          jedisCluster.setex(keyBytes, 1, valueBytes)
        case Some(d) =>
          jedisCluster.setex(keyBytes, d.toSeconds.toInt, valueBytes)
      }
    }
  }

  override protected def doRemove(key: String): F[Any] = Async[F].delay {
    jedisCluster.del(key.utf8bytes)
  }

  @deprecated(
    "JedisCluster doesn't support this operation, scheduled to be removed with the next jedis major release",
    "0.28.0"
  )
  override protected def doRemoveAll(): F[Any] = Async[F].raiseError {
    new JedisClusterException("No way to dispatch this command to Redis Cluster.")
  }

  override def close(): F[Any] = Async[F].delay(jedisCluster.close())
}
