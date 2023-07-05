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