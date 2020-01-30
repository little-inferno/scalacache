package scalacache

import cats.effect.Async
import scalacache.logging.Logger

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.language.higherKinds

class EmptyCache[F[_]: Async, V](implicit val config: CacheConfig) extends AbstractCache[F, V] {

  override protected def logger = Logger.getLogger("EmptyCache")

  override protected def doGet(key: String) =
    Async[F].pure(None)

  override protected def doPut(key: String, value: V, ttl: Option[Duration]) =
    Async[F].pure(())

  override protected def doRemove(key: String) =
    Async[F].pure(())

  override protected def doRemoveAll() =
    Async[F].pure(())

  override def close() = Async[F].pure(())

}

class FullCache[F[_]: Async, V](value: V)(implicit val config: CacheConfig) extends AbstractCache[F, V] {

  override protected def logger = Logger.getLogger("FullCache")

  override protected def doGet(key: String) =
    Async[F].pure(Some(value))

  override protected def doPut(key: String, value: V, ttl: Option[Duration]) =
    Async[F].pure(())

  override protected def doRemove(key: String) =
    Async[F].pure(())

  override protected def doRemoveAll() =
    Async[F].pure(())

  override def close() = Async[F].pure(())

}

class ErrorRaisingCache[F[_]: Async, V](implicit val config: CacheConfig) extends AbstractCache[F, V] {

  override protected def logger = Logger.getLogger("FullCache")

  override protected def doGet(key: String) =
    Async[F].raiseError(new RuntimeException("failed to read"))

  override protected def doPut(key: String, value: V, ttl: Option[Duration]) =
    Async[F].raiseError(new RuntimeException("failed to write"))

  override protected def doRemove(key: String) =
    Async[F].pure(())

  override protected def doRemoveAll() =
    Async[F].pure(())

  override def close() = Async[F].pure(())
}

/**
  * A mock cache for use in tests and samples.
  * Does not support TTL.
  */
class MockCache[F[_]: Async, V](implicit val config: CacheConfig) extends AbstractCache[F, V] {

  override protected def logger = Logger.getLogger("MockCache")

  val mmap = collection.mutable.Map[String, V]()

  override protected def doGet(key: String) =
    Async[F].delay(mmap.get(key))

  override protected def doPut(key: String, value: V, ttl: Option[Duration]) =
    Async[F].delay(mmap.put(key, value))

  override protected def doRemove(key: String) =
    Async[F].delay(mmap.remove(key))

  override protected def doRemoveAll() =
    Async[F].delay(mmap.clear())

  override def close() = Async[F].pure(())

}

/**
  * A cache that keeps track of the arguments it was called with. Useful for tests.
  * Designed to be mixed in as a stackable trait.
  */
trait LoggingCache[F[_], V] extends AbstractCache[F, V] {
  var (getCalledWithArgs, putCalledWithArgs, removeCalledWithArgs) =
    (ArrayBuffer.empty[String], ArrayBuffer.empty[(String, Any, Option[Duration])], ArrayBuffer.empty[String])

  protected abstract override def doGet(key: String): F[Option[V]] = {
    getCalledWithArgs.append(key)
    super.doGet(key)
  }

  protected abstract override def doPut(key: String, value: V, ttl: Option[Duration]): F[Any] = {
    putCalledWithArgs.append((key, value, ttl))
    super.doPut(key, value, ttl)
  }

  protected abstract override def doRemove(key: String): F[Any] = {
    removeCalledWithArgs.append(key)
    super.doRemove(key)
  }

  def reset(): Unit = {
    getCalledWithArgs.clear()
    putCalledWithArgs.clear()
    removeCalledWithArgs.clear()
  }

}

/**
  * A mock cache that keeps track of the arguments it was called with.
  */
class LoggingMockCache[F[_]: Async, V] extends MockCache[F, V] with LoggingCache[F, V]
