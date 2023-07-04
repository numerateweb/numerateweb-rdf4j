package org.numerateweb.rdf4j.config;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

/**
 * Defines constants for the LmdbStore schema which is used by {@link NumerateWebSailFactory}s to initialize
 * {@link org.numerateweb.rdf4j.NumerateWebSail}s.
 */
public class NumerateWebSailSchema {

	/**
	 * The NumerateWebSail schema namespace (<tt>http://rdf4j.org/config/sail/numerateweb#</tt>).
	 */
	public static final String NAMESPACE = "http://rdf4j.org/config/sail/numerateweb#";

	/**
	 * <tt>http://rdf4j.org/config/sail/numerateweb#incrementalInference</tt>
	 */
	public final static IRI INCREMENTAL_INFERENCE;

	static {
		ValueFactory factory = SimpleValueFactory.getInstance();
		INCREMENTAL_INFERENCE = factory.createIRI(NAMESPACE, "incrementalInference");
	}
}