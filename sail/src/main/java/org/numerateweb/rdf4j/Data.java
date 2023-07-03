package org.numerateweb.rdf4j;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A linked list of (key, value) pairs where properties
 * and values are arbitrary objects. Duplicate properties are explicitly
 * permitted.
 * <p>
 * This is inspired by RDF and the scala.xml.MetaData implementation.
 */
public class Data implements Iterable<Data> {
	public static final Data NULL = new Data(null, null, null);

	protected final Object key;
	protected final Data next;
	protected final Object value;

	public Data(Object key, Object value) {
		this(key, value, null);
	}

	protected Data(Object key, Object value, Data next) {
		this.key = key;
		this.value = value;
		this.next = next == NULL ? null : next;
	}

	/**
	 * Appends a data list to this list and returns a new copy.
	 *
	 * @param data The data list that should be appended
	 * @return A new list with <code>data</code> at the end
	 */
	public Data append(Data data) {
		if (this.key == null) {
			return data;
		} else if (this.next == null) {
			return copy(data);
		}
		return copy(next.append(data));
	}

	/**
	 * Create a copy of this data element.
	 *
	 * @param next The new next element
	 * @return A copy of this data element with a new next element.
	 */
	public Data copy(Data next) {
		if (key == null) {
			return next;
		} else {
			return new Data(key, value, next);
		}
	}

	/**
	 * Find the first data element within this list for the given key.
	 *
	 * @param key The key
	 * @return The data element for the given key or an empty data element
	 * if it was not found.
	 */
	@SuppressWarnings("unchecked")
	public Data first(Object key) {
		if (key != null) {
			if (key.equals(this.key)) {
				return this;
			}
			if (next != null) {
				return next.first(key);
			}
		}
		return NULL;
	}

	/**
	 * Returns the key of this data element.
	 *
	 * @return The key
	 */
	public Object getKey() {
		return key;
	}

	/**
	 * Returns the value of this data element.
	 *
	 * @return The value
	 */
	public Object getValue() {
		return value;
	}

	@Override
	public Iterator<Data> iterator() {
		return new Iterator<>() {
			@SuppressWarnings("unchecked")
			Data next = key != null ? Data.this : null;
			Data current = null;

			@Override
			public boolean hasNext() {
				return next != null;
			}

			@Override
			public Data next() {
				if (next == null) {
					throw new NoSuchElementException();
				}
				current = next;
				next = current.next;
				return current;
			}

			@Override
			public void remove() {
				if (current == null) {
					throw new IllegalStateException();
				}
				current.removeFirst(current.key);
			}
		};
	}

	/**
	 * Remove the first element with the given key.
	 *
	 * @param key The key whose element should be removed
	 * @return A copy of this data list where the first corresponding has been
	 * removed.
	 */
	@SuppressWarnings("unchecked")
	public Data removeFirst(Object key) {
		if (this.key == null || key == null) {
			return this;
		}
		if (key.equals(this.key)) {
			return next;
		}
		Data newNext = next == null ? null : next.removeFirst(key);
		return newNext != next ? copy(newNext) : this;
	}

	/**
	 * Remove the first element with the given key and value.
	 *
	 * @param key   The key whose element should be removed
	 * @param value The value whose element should be removed
	 * @return A copy of this data list where the first corresponding has been
	 * removed.
	 */
	@SuppressWarnings("unchecked")
	public Data removeFirst(Object key, Object value) {
		if (this.key == null || key == null || value == null) {
			return this;
		}
		if (key.equals(this.key) && value.equals(this.value)) {
			return next;
		}
		Data newNext = next == null ? null : next.removeFirst(key, value);
		return newNext != next ? copy(newNext) : this;
	}

	/**
	 * Returns the size of this list.
	 *
	 * @return The size of this list
	 */
	public int size() {
		if (key == null) {
			return 0;
		}
		return 1 + (next == null ? 0 : next.size());
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		for (Iterator<Data> it = this.iterator(); it.hasNext(); ) {
			Data d = it.next();
			sb.append("(").append(d.getKey()).append(", ");
			Object value = d.getValue();
			if (value instanceof Object[]) {
				sb.append(Arrays.toString((Object[]) value));
			} else {
				sb.append(value);
			}
			sb.append(")");
			if (it.hasNext()) {
				sb.append(", ");
			}
		}
		return sb.append("]").toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Data)) return false;
		Data data = (Data) o;
		return key.equals(data.key) && Objects.equals(next, data.next) && Objects.equals(value, data.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, next, value);
	}
}