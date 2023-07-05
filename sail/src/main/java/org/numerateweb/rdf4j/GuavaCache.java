/*
 * Copyright (c) 2023 Numerate Web contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
	public void remove(K key) {
		cache.invalidate(key);
	}

	@Override
	public void clear() {
		cache.invalidateAll();
	}
}
