package org.numerateweb.rdf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper for caching a specific RDF resource (entity) with its properties in
 * different contexts (named graphs or models).
 */
class CachedEntity {
	static final Object NULL = new Object();
	Map<Object, Data> values = new HashMap<>();

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
	 */
	public synchronized void put(Object context, Object property, Object value) {
		if (context == null) {
			context = NULL;
		}
		Data data = values.get(property);
		Data newData = new Data(context, value);
		if (data != null) {
			data = data.removeFirst(context).append(newData);
		} else {
			data = newData;
		}
		values.put(property, data);
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
	 */
	public synchronized void remove(Object context, Object property) {
		Data data = values.get(property);
		if (data != null) {
			values.put(property, data.removeFirst(context == null ? NULL : context));
		}
	}

	/**
	 * Removes all property data of the entity.
	 */
	public synchronized void clearProperties() {
		values.clear();
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
		Data data = values.get(property);
		if (data != null) {
			return data.first(context == null ? NULL : context).getValue();
		}
		return null;
	}
}