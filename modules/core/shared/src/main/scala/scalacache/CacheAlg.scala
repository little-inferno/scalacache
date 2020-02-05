package scalacache

import cats.effect.Async

import scala.concurrent.duration.Duration
import scala.language.higherKinds

/**
  * Abstract algebra describing the operations a cache can perform
  *
  * @tparam V The value of types stored in the cache.
  * @tparam F The type of container in which the result will be wrapped.
  */
abstract class CacheAlg[F[_]: Async, V] {

  /**
    * Get a value from the cache
    *
    * @param keyParts The cache key
    * @param flags Flags used to conditionally alter the behaviour of ScalaCache
    * @return The appropriate value, if it was found in the cache
    */
  def get(keyParts: Any*)(implicit flags: Flags): F[Option[V]]

  /**
    * Insert a value into the cache, optionally setting a TTL (time-to-live)
    *
    * @param keyParts The cache key
    * @param value The value to insert
    * @param ttl The time-to-live. The cache entry will expire after this time has elapsed.
    * @param flags Flags used to conditionally alter the behaviour of ScalaCache
    */
  def put(keyParts: Any*)(value: V, ttl: Option[Duration] = None)(implicit flags: Flags): F[Any]

  /**
    * Remove the given key and its associated value from the cache, if it exists.
    * If the key is not in the cache, do nothing.
    *
    * @param keyParts data to be used to generate the cache key. This could be as simple as just a single String. See [[CacheKeyBuilder]].
    */
  def remove(keyParts: Any*): F[Any]

  /**
    * Delete the entire contents of the cache. Use wisely!
    *
    */
  def removeAll(): F[Any]

  /**
    * Get a value from the cache if it exists. Otherwise compute it, insert it into the cache, and return it.
    *
    * @param keyParts The cache key
    * @param ttl The time-to-live to use when inserting into the cache. The cache entry will expire after this time has elapsed.
    * @param f A block that computes the value
    * @param flags Flags used to conditionally alter the behaviour of ScalaCache
    * @return The value, either retrieved from the cache or computed
    */
  def caching(keyParts: Any*)(ttl: Option[Duration])(
      f: => V
  )(implicit flags: Flags): F[V]

  /**
    * Get a value from the cache if it exists. Otherwise compute it, insert it into the cache, and return it.
    *
    * @param keyParts The cache key
    * @param ttl The time-to-live to use when inserting into the cache. The cache entry will expire after this time has elapsed.
    * @param f A block that computes the value wrapped in a container
    * @param flags Flags used to conditionally alter the behaviour of ScalaCache
    * @return The value, either retrieved from the cache or computed
    */
  def cachingF(keyParts: Any*)(ttl: Option[Duration])(
      f: => F[V]
  )(implicit flags: Flags): F[V]

  /**
    * You should call this when you have finished using this Cache.
    * (e.g. when your application shuts down)
    *
    * It will take care of gracefully shutting down the underlying cache client.
    *
    * Note that you should not try to use this Cache instance after you have called this method.
    *
    */
  def close(): F[Any]

}
