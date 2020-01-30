package scalacache.cache2k

import cats.effect.Async
import org.cache2k.{Cache => CCache}
import scalacache.logging.Logger

import scala.concurrent.duration._
import scala.language.higherKinds
import scalacache.{AbstractCache, CacheConfig}

/*
 * Thin wrapper around cache2k.
 *
 * This cache implementation is synchronous.
 */
class Cache2kCache[F[_]: Async, V](val underlying: CCache[String, V])(implicit val config: CacheConfig)
    extends AbstractCache[F, V] {

  override protected final val logger = Logger.getLogger(getClass.getName)

  def doGet(key: String): F[Option[V]] = {
    Async[F].delay {
      val result = Option(underlying.peek(key))
      logCacheHitOrMiss(key, result)
      result
    }
  }

  def doPut(key: String, value: V, ttl: Option[Duration]): F[Any] = {
    Async[F].delay {
      underlying.put(key, value)
      ttl.foreach(x => underlying.expireAt(key, toExpiryTime(x)))
      logCachePut(key, ttl)
    }
  }

  override def doRemove(key: String): F[Any] =
    Async[F].delay(underlying.remove(key))

  override def doRemoveAll(): F[Any] =
    Async[F].delay(underlying.clear())

  override def close(): F[Any] =
    Async[F].delay(underlying.close())

  private def toExpiryTime(ttl: Duration): Long =
    System.currentTimeMillis + ttl.toMillis

}

object Cache2kCache {

  /**
    * Create a new cache utilizing the given underlying cache2k cache.
    *
    * @param underlying a cache2k cache configured with a ExpiryPolicy or Cache2kBuilder.expireAfterWrite(long, TimeUnit)
    */
  def apply[F[_]: Async, V](underlying: CCache[String, V])(implicit config: CacheConfig): Cache2kCache[F, V] =
    new Cache2kCache(underlying)

}
