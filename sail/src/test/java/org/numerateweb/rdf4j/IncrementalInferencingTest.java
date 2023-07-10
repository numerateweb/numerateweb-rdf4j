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

import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SHACL;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.Test;
import org.numerateweb.math.rdf.rules.NWRULES;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IncrementalInferencingTest {
	static final String NS = "http://example.org/";
	static final ValueFactory vf = SimpleValueFactory.getInstance();

	static final String nw = "http://numerateweb.org/vocab/math/rules#";
	static final IRI constraintClass = vf.createIRI(nw + "Constraint");
	static final IRI constraint = vf.createIRI(nw + "constraint");
	static final IRI onProperty = vf.createIRI(nw + "onProperty");
	static final IRI expressionString = vf.createIRI(nw + "expressionString");

	protected NumerateWebSail createSail() {
		return new NumerateWebSail(new MemoryStore());
	}

	void createPrefixes(RepositoryConnection connection) {
		ValueFactory vf = connection.getValueFactory();
		Resource declaration = vf.createBNode();
		connection.add(vf.createIRI(NS), SHACL.DECLARE, declaration);
		connection.add(declaration, SHACL.PREFIX_PROP, vf.createLiteral(""));
		connection.add(declaration, SHACL.NAMESPACE_PROP, vf.createLiteral(NS));
	}

	Resource createConstraint(RepositoryConnection connection, Resource targetClass,
	                          Resource targetProperty, String expression) {
		ValueFactory vf = connection.getValueFactory();
		IRI prefixesResource = vf.createIRI(NS);

		Resource constraintResource = vf.createBNode();
		connection.add(targetClass, constraint, constraintResource);
		connection.add(constraintResource, RDF.TYPE, constraintClass);
		connection.add(constraintResource, onProperty, targetProperty);
		connection.add(constraintResource, expressionString, vf.createLiteral(expression));
		connection.add(constraintResource, SHACL.PREFIXES, prefixesResource);
		return constraintResource;
	}

	@Test
	public void incrementalTest() {
		NumerateWebSail sail = createSail();
		Repository repository = new SailRepository(sail);
		ValueFactory vf = repository.getValueFactory();

		IRI rectanglesClass = vf.createIRI(NS + "Rectangles");
		IRI rectangleProperty = vf.createIRI(NS + "rectangle");
		Resource rectangles = vf.createIRI(NS + "rectangles");

		IRI rectangleClass = vf.createIRI(NS + "Rectangle");
		IRI areaProperty = vf.createIRI(NS + "area");
		try (RepositoryConnection connection = repository.getConnection()) {
			connection.begin(IsolationLevels.NONE);

			createPrefixes(connection);
			createConstraint(connection, rectanglesClass, areaProperty, "sum(@@rectangle, $r -> @area($r))");
			createConstraint(connection, rectangleClass, areaProperty, "@a * @b");

			connection.add(rectangles, RDF.TYPE, rectanglesClass);

			for (int i = 0; i < 5; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);
				connection.add(r, RDF.TYPE, rectangleClass);
				connection.add(r, vf.createIRI(NS + "a"), vf.createLiteral(i));
				connection.add(r, vf.createIRI(NS + "b"), vf.createLiteral(2 * i));

				// add to list of all rectangles
				connection.add(rectangles, rectangleProperty, r);
			}
			connection.commit();
		}

		int areaSum = 0;
		try (RepositoryConnection connection = repository.getConnection()) {
			for (int i = 0; i < 5; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);

				try (RepositoryResult<Statement> result = connection.getStatements(r, areaProperty, null)) {
					Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

					assertTrue(v.isPresent());
					assertTrue(v.get().isLiteral());
					int area = i * (2 * i);
					areaSum += area;
					assertEquals("Area of rectangle " + r + " must be correct.", area, ((Literal) v.get()).intValue());
				}
			}
			try (RepositoryResult<Statement> result = connection.getStatements(rectangles, areaProperty, null)) {
				Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

				assertTrue(v.isPresent());
				assertTrue(v.get().isLiteral());
				assertEquals(areaSum, ((Literal) v.get()).intValue());
			}
		}

		try (RepositoryConnection connection = repository.getConnection()) {
			connection.begin(IsolationLevels.NONE);

			// adds additional rectangles
			for (int i = 5; i < 10; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);
				connection.add(r, RDF.TYPE, rectangleClass);
				connection.add(r, vf.createIRI(NS + "a"), vf.createLiteral(i));
				connection.add(r, vf.createIRI(NS + "b"), vf.createLiteral(2 * i));

				// add to list of all rectangles
				connection.add(rectangles, rectangleProperty, r);
			}
			connection.commit();

			// ensure that all rectangles have correct areas
			areaSum = 0;
			for (int i = 0; i < 10; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);

				try (RepositoryResult<Statement> result = connection.getStatements(r, areaProperty, null)) {
					Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

					assertTrue(v.isPresent());
					assertTrue(v.get().isLiteral());
					int area = i * (2 * i);
					areaSum += area;
					assertEquals("Area of rectangle " + r + " must be correct.", area, ((Literal) v.get()).intValue());
				}
			}
			try (RepositoryResult<Statement> result = connection.getStatements(rectangles, areaProperty, null)) {
				Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

				assertTrue(v.isPresent());
				assertTrue(v.get().isLiteral());
				assertEquals(areaSum, ((Literal) v.get()).intValue());
			}
		}

		// double value of property :a by using an update query
		String updateQuery = "prefix : <" + NS + "> " +
				"delete { ?s :a ?o } " +
				"insert { ?s :a ?newValue } " +
				"where { ?s :a ?o . bind(?o * 2 as ?newValue) }";
		try (RepositoryConnection connection = repository.getConnection()) {
			connection.prepareUpdate(updateQuery).execute();
		}

		try (RepositoryConnection connection = repository.getConnection()) {
			// ensure that area of all rectangles has been doubled
			areaSum = 0;
			for (int i = 0; i < 10; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);

				try (RepositoryResult<Statement> result = connection.getStatements(r, areaProperty, null)) {
					Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

					assertTrue(v.isPresent());
					assertTrue(v.get().isLiteral());
					int area = 2 * i * (2 * i);
					areaSum += area;
					assertEquals("Area of rectangle " + r + " must be correct.", area, ((Literal) v.get()).intValue());
				}
			}
			try (RepositoryResult<Statement> result = connection.getStatements(rectangles, areaProperty, null)) {
				Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

				assertTrue(v.isPresent());
				assertTrue(v.get().isLiteral());
				assertEquals(areaSum, ((Literal) v.get()).intValue());
			}
		}

		repository.shutDown();
	}

	@Test
	public void schemaChangeTest() {
		NumerateWebSail sail = createSail();
		Repository repository = new SailRepository(sail);
		ValueFactory vf = repository.getValueFactory();

		IRI rectanglesClass = vf.createIRI(NS + "Rectangles");
		IRI rectangleProperty = vf.createIRI(NS + "rectangle");
		Resource rectangles = vf.createIRI(NS + "rectangles");

		IRI rectangleClass = vf.createIRI(NS + "Rectangle");
		IRI areaProperty = vf.createIRI(NS + "area");
		try (RepositoryConnection connection = repository.getConnection()) {
			connection.begin(IsolationLevels.NONE);

			createPrefixes(connection);
			createConstraint(connection, rectanglesClass, areaProperty, "sum(@@rectangle, $r -> @area($r))");
			createConstraint(connection, rectangleClass, areaProperty, "@a * @b");

			connection.add(rectangles, RDF.TYPE, rectanglesClass);

			for (int i = 0; i < 5; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);
				connection.add(r, RDF.TYPE, rectangleClass);
				connection.add(r, vf.createIRI(NS + "a"), vf.createLiteral(i));
				connection.add(r, vf.createIRI(NS + "b"), vf.createLiteral(2 * i));

				// add to list of all rectangles
				connection.add(rectangles, rectangleProperty, r);
			}
			connection.commit();
		}

		int areaSum = 0;
		try (RepositoryConnection connection = repository.getConnection()) {
			for (int i = 0; i < 5; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);

				try (RepositoryResult<Statement> result = connection.getStatements(r, areaProperty, null)) {
					Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

					assertTrue(v.isPresent());
					assertTrue(v.get().isLiteral());
					int area = i * (2 * i);
					areaSum += area;
					assertEquals("Area of rectangle " + r + " must be correct.", area, ((Literal) v.get()).intValue());
				}
			}
			try (RepositoryResult<Statement> result = connection.getStatements(rectangles, areaProperty, null)) {
				Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

				assertTrue(v.isPresent());
				assertTrue(v.get().isLiteral());
				assertEquals(areaSum, ((Literal) v.get()).intValue());
			}
		}

		// add additional formula to compute the volume property
		IRI volumeProperty = vf.createIRI(NS + "volume");
		Resource rectangleVolumeConstraint;
		try (RepositoryConnection connection = repository.getConnection()) {
			connection.begin(IsolationLevels.NONE);
			createConstraint(connection, rectanglesClass, volumeProperty, "sum(@@rectangle, $r -> @volume($r))");
			rectangleVolumeConstraint = createConstraint(connection, rectangleClass, volumeProperty, "@a * @b * 5");
			connection.commit();
		}

		int volumeSum = 0;
		try (RepositoryConnection connection = repository.getConnection()) {
			for (int i = 0; i < 5; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);

				try (RepositoryResult<Statement> result = connection.getStatements(r, volumeProperty, null)) {
					Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

					assertTrue(v.isPresent());
					assertTrue(v.get().isLiteral());
					int volume = i * (2 * i) * 5;
					volumeSum += volume;
					assertEquals("Volume of rectangle " + r + " must be correct.", volume, ((Literal) v.get()).intValue());
				}
			}
			try (RepositoryResult<Statement> result = connection.getStatements(rectangles, volumeProperty, null)) {
				Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

				assertTrue(v.isPresent());
				assertTrue(v.get().isLiteral());
				assertEquals(volumeSum, ((Literal) v.get()).intValue());
			}
		}

		// change calculation of volume
		try (RepositoryConnection connection = repository.getConnection()) {
			connection.begin(IsolationLevels.NONE);
			connection.remove(rectangleClass, constraint, rectangleVolumeConstraint);
			createConstraint(connection, rectangleClass, volumeProperty, "@a * @b * 10");
			connection.commit();
		}

		volumeSum = 0;
		try (RepositoryConnection connection = repository.getConnection()) {
			for (int i = 0; i < 5; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);

				try (RepositoryResult<Statement> result = connection.getStatements(r, volumeProperty, null)) {
					Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

					assertTrue(v.isPresent());
					assertTrue(v.get().isLiteral());
					int volume = i * (2 * i) * 10;
					volumeSum += volume;
					assertEquals("Volume of rectangle " + r + " must be correct.", volume, ((Literal) v.get()).intValue());
				}
			}
			try (RepositoryResult<Statement> result = connection.getStatements(rectangles, volumeProperty, null)) {
				Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

				assertTrue(v.isPresent());
				assertTrue(v.get().isLiteral());
				assertEquals(volumeSum, ((Literal) v.get()).intValue());
			}
		}

		try (RepositoryConnection connection = repository.getConnection()) {
			connection.begin(IsolationLevels.NONE);

			// adds additional rectangles
			for (int i = 5; i < 10; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);
				connection.add(r, RDF.TYPE, rectangleClass);
				connection.add(r, vf.createIRI(NS + "a"), vf.createLiteral(i));
				connection.add(r, vf.createIRI(NS + "b"), vf.createLiteral(2 * i));

				// add to list of all rectangles
				connection.add(rectangles, rectangleProperty, r);
			}
			connection.commit();

			// ensure that all rectangles have correct areas
			areaSum = 0;
			volumeSum = 0;
			for (int i = 0; i < 10; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);

				try (RepositoryResult<Statement> result = connection.getStatements(r, areaProperty, null)) {
					Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

					assertTrue(v.isPresent());
					assertTrue(v.get().isLiteral());
					int area = i * (2 * i);
					areaSum += area;
					assertEquals("Area of rectangle " + r + " must be correct.", area, ((Literal) v.get()).intValue());
				}
				try (RepositoryResult<Statement> result = connection.getStatements(r, volumeProperty, null)) {
					Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

					assertTrue(v.isPresent());
					assertTrue(v.get().isLiteral());
					int volume = i * (2 * i) * 10;
					volumeSum += volume;
					assertEquals("Volume of rectangle " + r + " must be correct.", volume, ((Literal) v.get()).intValue());
				}
			}
			try (RepositoryResult<Statement> result = connection.getStatements(rectangles, areaProperty, null)) {
				Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

				assertTrue(v.isPresent());
				assertTrue(v.get().isLiteral());
				assertEquals(areaSum, ((Literal) v.get()).intValue());
			}
			try (RepositoryResult<Statement> result = connection.getStatements(rectangles, volumeProperty, null)) {
				Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

				assertTrue(v.isPresent());
				assertTrue(v.get().isLiteral());
				assertEquals(volumeSum, ((Literal) v.get()).intValue());
			}
		}

		// remove all constraints on :volume property
		String updateQuery = "prefix : <" + NS + "> prefix mathrl: <" + NWRULES.NAMESPACE + "> " +
				"delete { ?class mathrl:constraint ?c } " +
				"where { ?class mathrl:constraint ?c . ?c mathrl:onProperty :volume }";
		try (RepositoryConnection connection = repository.getConnection()) {
			connection.prepareUpdate(updateQuery).execute();
		}

		try (RepositoryConnection connection = repository.getConnection()) {
			// ensure that :volume properties of all rectangles are removed
			for (int i = 0; i < 10; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);
				assertTrue(connection.getStatements(r, volumeProperty, null).stream().findFirst().isEmpty());
			}
			// ensure that :volume property of :rectangles instance if removed
			assertTrue(connection.getStatements(rectangles, volumeProperty, null).stream().findFirst().isEmpty());
		}

		repository.shutDown();
	}
}
