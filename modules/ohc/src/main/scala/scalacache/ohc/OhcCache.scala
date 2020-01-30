package scalacache.ohc

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import cats.effect.Async
import org.caffinitas.ohc.{CacheSerializer, OHCache, OHCacheBuilder}
import scalacache.logging.Logger

import scala.concurrent.duration._
import scala.language.higherKinds
import scalacache.{AbstractCache, CacheConfig}

/*
 * Thin wrapper around OHC.
 *
 * This cache implementation is synchronous.
 */
class OhcCache[F[_]: Async, V](val underlying: OHCache[String, V])(implicit val config: CacheConfig)
    extends AbstractCache[F, V] {

  override protected final val logger = Logger.getLogger(getClass.getName)

  def doGet(key: String): F[Option[V]] = {
    Async[F].delay {
      val result = Option(underlying.get(key))
      logCacheHitOrMiss(key, result)
      result
    }
  }

  def doPut(key: String, value: V, ttl: Option[Duration]): F[Any] = {
    Async[F].delay {
      ttl.fold(underlying.put(key, value))(x => underlying.put(key, value, toExpiryTime(x)))
      logCachePut(key, ttl)
    }
  }

  override def doRemove(key: String): F[Any] =
    Async[F].delay(underlying.remove(key))

  override def doRemoveAll(): F[Any] =
    Async[F].delay(underlying.clear())

  override def close(): F[Any] =
    Async[F].pure(underlying.close())

  private def toExpiryTime(ttl: Duration): Long =
    System.currentTimeMillis + ttl.toMillis

}

object OhcCache {

  val stringSerializer: CacheSerializer[String] = new CacheSerializer[String]() {

    def serialize(s: String, buf: ByteBuffer): Unit = {
      val bytes = s.getBytes(StandardCharsets.UTF_8)
      buf.putInt(bytes.length)
      buf.put(bytes)
    }

    def deserialize(buf: ByteBuffer): String = {
      val bytes = new Array[Byte](buf.getInt)
      buf.get(bytes)
      new String(bytes, StandardCharsets.UTF_8)
    }

    def serializedSize(s: String): Int =
      s.getBytes(StandardCharsets.UTF_8).length + 4

  }

  /**
    * Create a new OHC cache
    */
  def apply[F[_]: Async, V](implicit config: CacheConfig, valueSerializer: CacheSerializer[V]): OhcCache[F, V] =
    new OhcCache(
      OHCacheBuilder
        .newBuilder()
        .keySerializer(stringSerializer)
        .valueSerializer(valueSerializer)
        .timeouts(true)
        .build()
    )

  /**
    * Create a new cache utilizing the given underlying OHC cache.
    *
    * @param underlying a OHC cache configured with OHCacheBuilder.timeouts(true)
    */
  def apply[F[_]: Async, V](underlying: OHCache[String, V])(implicit config: CacheConfig): OhcCache[F, V] =
    new OhcCache(underlying)

}
