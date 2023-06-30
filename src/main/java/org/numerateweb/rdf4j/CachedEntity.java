package org.numerateweb.rdf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.google.common.collect.ImmutableSet;

/**
 * A wrapper for caching a specific RDF resource (entity) with its properties in
 * different contexts (named graphs or models).
 */
class CachedEntity {
	/**
	 * A factory that can be used with Guava's cache implementation.
	 */
	public static final Callable<CachedEntity> FACTORY = () -> new CachedEntity();

	Map<Object, Map<Object, Object>> contextToProperties;

	Map<Object, Object> ensureProperties(Object context) {
		if (contextToProperties == null) {
			contextToProperties = new HashMap<>();
		}
		Map<Object, Object> properties = contextToProperties.get(context);
		if (properties == null) {
			properties = new HashMap<>();
			contextToProperties.put(context, properties);
		}
		return properties;
	}

	/**
	 * Associates the specified value with the specified property for an entity
	 * in this cache. If the entity previously contained a mapping for this
	 * context, the old value is replaced by the specified value.
	 *
	 * @param context
	 *            context to the entity to be accessed.
	 * @param property
	 *            property with which the specified value is to be associated.
	 * @param value
	 *            value to be associated with the specified property.
	 * @return previous value associated with specified property, or
	 *         <code>null</code> if there was no mapping for property.
	 */
	public synchronized Object put(Object context, Object property, Object value) {
		return ensureProperties(context).put(property, value);
	}

	/**
	 * Removes the mapping for this context from a entity. Returns the value to
	 * which the entity previously associated the property, or <code>null</code>
	 * if the entity contained no mapping for this property.
	 *
	 * @param context
	 *            context to the entity to be accessed.
	 * @param property
	 *            property whose mapping is to be removed from the entity
	 * @return previous value associated with specified entity's property
	 */
	public synchronized Object remove(Object context, Object property) {
		Map<Object, Object> properties = contextToProperties == null ? null : contextToProperties.get(context);
		if (properties == null) {
			return null;
		}
		return properties.remove(property);
	}

	/**
	 * Removes all property data of the entity.
	 *
	 * @return true if the data was removed, false if the data was not found
	 */
	public synchronized boolean clearProperties() {
		if (contextToProperties != null) {
			contextToProperties.clear();
			return true;
		}
		return false;
	}

	/**
	 * Access a property for an entity with the given context.
	 *
	 * @param context
	 *            context to the entity to be accessed.
	 * @param property
	 *            property whose value is to be retrieved.
	 * @return returns data for the specified property of the entity denoted by
	 *         context.
	 */
	public synchronized Object get(Object context, Object property) {
		Map<Object, Object> properties = contextToProperties == null ? null : contextToProperties.get(context);
		if (properties == null) {
			return null;
		}
		return properties.get(property);
	}
}