package org.numerateweb.rdf4j;

import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.numerateweb.math.reasoner.AbstractCache;
import org.numerateweb.math.reasoner.CacheResult;

class GuavaCache<K, T> extends AbstractCache<K, T> {
	private Cache<Object, Object> cache;

	public GuavaCache() {
		cache = CacheBuilder.newBuilder().maximumSize(10000000)
				.expireAfterWrite(60, TimeUnit.SECONDS).build();
	}

	private final static Object NULL = new Object();

	@Override
	public void put(K key, T value) {
		cache.put(key, value == null ? NULL : value);
	}

	@Override
	protected CacheResult<T> getInternal(K key) {
		return convert(cache.getIfPresent(key));
	}

	protected CacheResult<T> convert(Object value) {
		return value == null ? null : new CacheResult<T>(getRealValue(value));
	}

	@SuppressWarnings("unchecked")
	protected T getRealValue(Object value) {
		return value == NULL ? null : (T) value;
	}

	@Override
	public T remove(K key) {
		Object value = getInternal(key);
		cache.invalidate(key);
		return getRealValue(value == NULL);
	}

	@Override
	public void clear() {
		cache.invalidateAll();
	}
}
