package me.escoffier.reactive.rhdg.impl;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.reactivex.core.Context;
import io.vertx.reactivex.core.Vertx;
import me.escoffier.reactive.rhdg.AsyncCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.commons.util.CloseableIterator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class AsyncCacheImpl<K, V> implements AsyncCache<K, V> {
  private static final Logger LOGGER = LogManager.getLogger("InfinispanAsyncCache");

  private final Vertx vertx;
  private final RemoteCache<K, V> cache;

  public AsyncCacheImpl(Vertx vertx, RemoteCache<K, V> cache) {
    this.vertx = Objects.requireNonNull(vertx, "Vertx must be set");
    this.cache = Objects.requireNonNull(cache, "The cache must be set");
  }

  private Context getContext() {
    return vertx.getOrCreateContext();
  }

  @Override
  public Completable put(K k, V v) {
    Objects.requireNonNull(k, "The key must be set");
    Objects.requireNonNull(v, "The value must be set");
    return getContext()
      .rxExecuteBlocking(
        fut -> {
          cache.put(k, v);
          fut.complete();
        }
      )
      .ignoreElement();
  }

  @Override
  public void listen(String address) {
    cache.addClientListener(new RemoteCacheListener(vertx, address));
  }

  @Override
  public Completable put(K k, V v, long amount, TimeUnit tu) {
    Objects.requireNonNull(k, "The key must be set");
    Objects.requireNonNull(v, "The value must be set");
    return getContext()
      .rxExecuteBlocking(
        fut -> {
          cache.put(k, v, amount, tu);
          fut.complete();
        }
      )
      .ignoreElement();
  }

  @Override
  public Maybe<V> get(K k) {
    Objects.requireNonNull(k, "The key must be set");
    return getContext()
      .<V>rxExecuteBlocking(
        fut -> {
          V v = cache.get(k);
          fut.complete(v);
        }
      ).flatMapMaybe(value -> {
        if (value != null) {
          return Maybe.just(value);
        }
        return Maybe.empty();
      });
  }

  @Override
  public Single<V> get(K k, V def) {
    Objects.requireNonNull(k, "The key must be set");
    return get(k)
      .toSingle(def);
  }

  @Override
  public Completable remove(K k) {
    Objects.requireNonNull(k, "The key must be set");
    return getContext()
      .rxExecuteBlocking(
        fut -> {
          cache.remove(k);
          fut.complete();
        }
      )
      .doOnError(err -> LOGGER.error("Error on remove", err))
      .ignoreElement();
  }

  @Override
  public Completable clear() {
    return getContext()
      .rxExecuteBlocking(
        fut -> {
          cache.clear();
          fut.complete();
        }
      )
      .doOnError(err -> LOGGER.error("Error on clear", err))
      .ignoreElement();
  }

  @Override
  public String name() {
    return cache.getName();
  }

  @Override
  public Single<Integer> size() {
    return getContext()
      .rxExecuteBlocking(
        fut -> fut.complete(cacheSize())
      );
  }

  private int cacheSize() {
    try {
      return cache.size();
    } catch (Throwable t) {
      LOGGER.error("Error on size", t);
      throw t;
    }
  }

  @Override
  public Single<Boolean> replace(K key, V oldValue, V newValue) {
    return
      getContext()
        .rxExecuteBlocking(future -> future.complete(cache.replace(key, oldValue, newValue)));
  }

  @Override
  public Single<Map<K, V>> all() {
    return
      getContext()
        .<Map<K, V>>rxExecuteBlocking(
          fut -> {
            CloseableIterator<Map.Entry<Object, Object>> iterator = cache.retrieveEntries(null, 100);
            Map<K, V> map = new LinkedHashMap<>();
            iterator.forEachRemaining(entry -> map.put((K) entry.getKey(), (V) entry.getValue()));
            fut.complete(map);
          }
        )
        .doOnError(err -> LOGGER.error("Error on all", err));
  }
}