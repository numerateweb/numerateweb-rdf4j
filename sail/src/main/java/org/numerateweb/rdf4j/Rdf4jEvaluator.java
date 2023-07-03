package org.numerateweb.rdf4j;

import com.google.common.cache.Cache;
import net.enilink.commons.iterator.IExtendedIterator;
import net.enilink.commons.util.Pair;
import net.enilink.komma.core.IReference;
import net.enilink.komma.core.URI;
import org.eclipse.rdf4j.model.Resource;
import org.numerateweb.math.eval.SimpleEvaluator;
import org.numerateweb.math.model.OMObject;
import org.numerateweb.math.reasoner.AbstractCache;
import org.numerateweb.math.reasoner.CacheManager;
import org.numerateweb.math.reasoner.CacheResult;
import org.numerateweb.math.reasoner.ICache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;

class Rdf4jEvaluator extends SimpleEvaluator {

	protected final static Logger logger = LoggerFactory.getLogger(Rdf4jEvaluator.class);
	protected Map<Pair<Object, IReference>, List<Object>> propertiesToManagedInstances = new HashMap<>();
	protected final Cache<Object, CachedEntity> cache;

	public Rdf4jEvaluator(Rdf4jModelAccess modelAccess, Cache<Object, CachedEntity> cache, CacheManager cacheManager) {
		super(modelAccess, cacheManager);
		this.cache = cache;
	}

	@Override
	protected ICache<Pair<Object, IReference>, Object> createValueCache(CacheManager cacheManager) {
		return new AbstractCache<>() {
			@Override
			protected CacheResult<Object> getInternal(Pair<Object, IReference> key) {
				CachedEntity entity = cache.getIfPresent(key.getFirst());
				if (entity != null) {
					Resource[] readContexts = ((Rdf4jModelAccess) modelAccess).readContext();
					if (readContexts.length > 0) {
						for (Resource ctx : readContexts) {
							Object value = entity.get(ctx, key.getSecond());
							if (value != null) {
								return new CacheResult<>(value);
							}
						}
					} else {
						Object value = entity.get(null, key.getSecond());
						if (value != null) {
							return new CacheResult<>(value);
						}
					}
				}
				return null;
			}

			@Override
			public void put(Pair<Object, IReference> key, Object o) {
				try {
					CachedEntity entity = cache.get(key.getFirst(), CachedEntity::new);
					Resource[] writeCtx = ((Rdf4jModelAccess) modelAccess).writeContext((Resource) key.getFirst());
					if (writeCtx.length > 0) {
						entity.put(writeCtx[0], key.getSecond(), o);
					} else {
						entity.put(null, key.getSecond(), o);
					}
				} catch (ExecutionException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void remove(Pair<Object, IReference> key) {
				CachedEntity entity = cache.getIfPresent(key.getFirst());
				if (entity != null) {
					Resource[] writeCtx = ((Rdf4jModelAccess) modelAccess).writeContext((Resource) key.getFirst());
					if (writeCtx.length > 0) {
						entity.remove(writeCtx[0], key.getSecond());
					} else {
						entity.remove(null, key.getSecond());
					}
				}
			}

			@Override
			public void clear() {
			}
		};
	}

	@Override
	public Object createInstance(URI uri, IReference clazz, Map<URI, Object> args) {
		Pair<Object, IReference> property = path.get().peekLast();
		Object instance = modelAccess.createInstance(property.getFirst(), property.getSecond().getURI(), uri, clazz, args);
		List<Object> instances = propertiesToManagedInstances.computeIfAbsent(property, k -> new ArrayList<>());
		instances.add(instance);
		return instance;
	}

	@Override
	protected void recordDependency(Pair<Object, IReference> from, Pair<Object, IReference> to) {
		logger.trace("adding dependency {} -> {}", from, to);
		((Rdf4jModelAccess) modelAccess).addDependency(from, to);
	}

	/**
	 * Evaluate in the root context, saves prior context path information (if any)
	 * and restores it after the evaluation.
	 */
	public Result evaluateRoot(Object subject, IReference property, Optional<IReference> restriction) {
		Path<Pair<Object, IReference>> outerPath = path.get();
		if (null == outerPath.peekLast()) {
			return evaluate(subject, property, restriction);
		}
		try {
			path.set(new Path<>());
			return evaluate(subject, property, restriction);
		} finally {
			path.set(outerPath);
		}
	}

	@Override
	public Result evaluate(Object subject, IReference property, Optional<IReference> restriction) {
		// check if already in cache
		boolean cached = (null != valueCache.get(new Pair<>(subject, property)));
		// check if the property should be calculated for this subject
		/*if (dontCalculateValue) {
			Object value = getPropertyValue(subject, property);
			logger.trace("ignoring evaluation for ({}.{}), existing value = {}", subject, property, value);
			// property should be ignored for this subject, add current value to cache
			valueCache.put(new Pair<>(subject, property), value);
			cached = true;
		}*/
		// WARNING: side effect, adds dependencies; DO NOT skip when cached!
		Result result = super.evaluate(subject, property, restriction);
		if (getConstraintExpression(subject, property) == null) {
			// value from model, no need to update field value
			return result;
		}
		if (cached) {
			// value was in cache, no need to update field value
			// WARNING: do NOT short-circuit, see side effect above!
			return result;
		}
		// update field value with expression result(s)
		try {
			List<Object> results = result.toList();
			Object singleResult = (result.isSingle()) ? results.get(0) : null;
			if (singleResult instanceof Exception || (singleResult instanceof OMObject &&
					((OMObject) singleResult).getType() == OMObject.Type.OME)) {
				logger.warn("evaluation of ({}, {}) failed: {}", subject, property, singleResult);
				return result(singleResult);
			} else {
				logger.trace("setting ({}, {}) to value={}", subject, property, results);
				((Rdf4jModelAccess) modelAccess).setPropertyValue(subject, property, results);
				return result(result.isSingle() ? singleResult : results);
			}
		} catch (NoSuchElementException nse) {
			return result(null);
		}
	}

	@Override
	protected Object getPropertyValue(Object subject, IReference property) {
		try (IExtendedIterator<?> it = modelAccess.getPropertyValues(subject, property, Optional.empty())) {
			if (!it.hasNext()) {
				return null;
			} else {
				return it.toList();
			}
		}
	}
}