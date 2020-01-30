package scalacache.guava

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}

import cats.effect.Async
import scalacache.{AbstractCache, CacheConfig, Entry}
import scalacache.logging.Logger
import com.google.common.cache.{Cache => GCache, CacheBuilder => GCacheBuilder}

import scala.concurrent.duration.Duration
import scala.language.higherKinds

/*
 * Thin wrapper around Google Guava.
 */
class GuavaCache[F[_]: Async, V](val underlying: GCache[String, Entry[V]])(
    implicit val config: CacheConfig,
    clock: Clock = Clock.systemUTC()
) extends AbstractCache[F, V] {

  override protected final val logger =
    Logger.getLogger(getClass.getName)

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

object GuavaCache {

  /**
    * Create a new Guava cache
    */
  def apply[F[_]: Async, V](implicit config: CacheConfig): GuavaCache[F, V] =
    apply(GCacheBuilder.newBuilder().build[String, Entry[V]]())

  /**
    * Create a new cache utilizing the given underlying Guava cache.
    *
    * @param underlying a Guava cache
    */
  def apply[F[_]: Async, V](underlying: GCache[String, Entry[V]])(implicit config: CacheConfig): GuavaCache[F, V] =
    new GuavaCache(underlying)

}
