package org.numerateweb.rdf4j;

import net.enilink.commons.iterator.IExtendedIterator;
import net.enilink.commons.util.Pair;
import net.enilink.komma.core.IReference;
import net.enilink.komma.core.URI;
import org.numerateweb.math.eval.SimpleEvaluator;
import org.numerateweb.math.model.OMObject;
import org.numerateweb.math.reasoner.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

public class Rdf4jEvaluator extends SimpleEvaluator {

	protected final static Logger logger = LoggerFactory.getLogger(Rdf4jEvaluator.class);
	protected Map<Class<?>, Function<Object, Collection<String>>> ignoreLookup = new HashMap<>();
	protected Map<Pair<Object, IReference>, List<Object>> propertiesToManagedInstances = new HashMap<>();

	public Rdf4jEvaluator(Rdf4jModelAccess modelAccess, CacheManager cacheManager) {
		super(modelAccess, cacheManager);
	}

	@Override
	public Object createInstance(URI uri, IReference clazz, Map<URI, Object> args) {
		Pair<Object, IReference> property = path.get().peekLast();
		Object instance = modelAccess.createInstance(property.getFirst(), property.getSecond().getURI(), uri, clazz, args);
		List<Object> instances = propertiesToManagedInstances.computeIfAbsent(property, k -> new ArrayList<>());
		instances.add(instance);
		return instance;
	}

	public void registerIgnoreLookup(Class<?> clazz, Function<Object, Collection<String>> getter) {
		ignoreLookup.put(clazz, getter);
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
		if (ignoreLookup.containsKey(subject.getClass())
				&& ignoreLookup.get(subject.getClass()).apply(subject).contains(property.getURI().localPart())) {
			Object value = getPropertyValue(subject, property);
			logger.trace("ignoring evaluation for ({}.{}), existing value = {}", subject, property, value);
			// property should be ignored for this subject, add current value to cache
			valueCache.put(new Pair<>(subject, property), value);
			cached = true;
		}
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

	void invalidateCache(Object subject, IReference property) {
		valueCache.remove(new Pair<>(subject, property));
	}

	void clearCache() {
		valueCache.clear();
	}
}