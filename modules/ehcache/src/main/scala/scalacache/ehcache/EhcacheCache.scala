package scalacache.ehcache

import cats.effect.Async
import scalacache.{AbstractCache, CacheConfig}
import scalacache.logging.Logger

import scala.concurrent.duration.Duration
import net.sf.ehcache.{Element, Cache => Ehcache}

import scala.language.higherKinds

/**
  * Thin wrapper around Ehcache.
  */
class EhcacheCache[F[_]: Async, V](val underlying: Ehcache)(implicit val config: CacheConfig)
    extends AbstractCache[F, V] {

  override protected final val logger = Logger.getLogger(getClass.getName)

  override protected def doGet(key: String): F[Option[V]] = {
    Async[F].delay {
      val result = {
        val elem = underlying.get(key)
        if (elem == null) None
        else Option(elem.getObjectValue.asInstanceOf[V])
      }
      logCacheHitOrMiss(key, result)
      result
    }
  }

  override protected def doPut(key: String, value: V, ttl: Option[Duration]): F[Any] = {
    Async[F].delay {
      val element = new Element(key, value)
      ttl.foreach(t => element.setTimeToLive(t.toSeconds.toInt))
      underlying.put(element)
      logCachePut(key, ttl)
    }
  }

  override protected def doRemove(key: String): F[Any] =
    Async[F].delay(underlying.remove(key))

  override protected def doRemoveAll(): F[Any] =
    Async[F].delay(underlying.removeAll())

  override def close(): F[Any] = {
    // Nothing to do
    Async[F].pure(())
  }

}

object EhcacheCache {

  /**
    * Create a new cache utilizing the given underlying Ehcache cache.
    *
    * @param underlying an Ehcache cache
    */
  def apply[F[_]: Async, V](underlying: Ehcache)(implicit config: CacheConfig): EhcacheCache[F, V] =
    new EhcacheCache[F, V](underlying)

}
