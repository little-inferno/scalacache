package scalacache.caffeine

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}

import cats.effect.Async
import com.github.benmanes.caffeine.cache.{Caffeine, Cache => CCache}
import scalacache.logging.Logger
import scalacache.{AbstractCache, CacheConfig, Entry}

import scala.concurrent.duration.Duration
import scala.language.higherKinds

/*
 * Thin wrapper around Caffeine.
 *
 * This cache implementation is synchronous.
 */
class CaffeineCache[F[_]: Async, V](val underlying: CCache[String, Entry[V]])(
    implicit val config: CacheConfig,
    clock: Clock = Clock.systemUTC()
) extends AbstractCache[F, V] {

  override protected final val logger = Logger.getLogger(getClass.getName)

  def doGet(key: String): F[Option[V]] = {
    Async[F].delay {
      val entry = underlying.getIfPresent(key)
      val result = {
        if (entry == null || entry.isExpired)
          None
        else
          Some(entry.value)
      }
      logCacheHitOrMiss(key, result)
      result
    }
  }

  def doPut(key: String, value: V, ttl: Option[Duration]): F[Any] = {
    Async[F].delay {
      val entry = Entry(value, ttl.map(toExpiryTime))
      underlying.put(key, entry)
      logCachePut(key, ttl)
    }
  }

  override def doRemove(key: String): F[Any] =
    Async[F].delay(underlying.invalidate(key))

  override def doRemoveAll(): F[Any] =
    Async[F].delay(underlying.invalidateAll())

  override def close(): F[Any] = {
    // Nothing to do
    Async[F].pure(())
  }

  private def toExpiryTime(ttl: Duration): Instant =
    Instant.now(clock).plus(ttl.toMillis, ChronoUnit.MILLIS)

}

object CaffeineCache {

  /**
    * Create a new Caffeine cache
    */
  def apply[F[_]: Async, V](implicit config: CacheConfig): CaffeineCache[F, V] =
    apply(Caffeine.newBuilder().build[String, Entry[V]]())

  /**
    * Create a new cache utilizing the given underlying Caffeine cache.
    *
    * @param underlying a Caffeine cache
    */
  def apply[F[_]: Async, V](underlying: CCache[String, Entry[V]])(implicit config: CacheConfig): CaffeineCache[F, V] =
    new CaffeineCache(underlying)

}
