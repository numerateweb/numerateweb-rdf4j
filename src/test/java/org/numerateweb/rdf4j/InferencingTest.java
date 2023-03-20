package org.numerateweb.rdf4j;

import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SHACL;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InferencingTest {
	static final String NS = "http://example.org/";
	static final int seed = 1337;

	protected NumerateWebInferencer createSail() {
		return new NumerateWebInferencer(new MemoryStore());
	}

	void createPrefixes(RepositoryConnection connection) {
		ValueFactory vf = connection.getValueFactory();
		Resource declaration = vf.createBNode();
		connection.add(vf.createIRI(NS), SHACL.DECLARE, declaration);
		connection.add(declaration, SHACL.PREFIX_PROP, vf.createLiteral(""));
		connection.add(declaration, SHACL.NAMESPACE_PROP, vf.createLiteral(NS));
	}

	void createConstraints(RepositoryConnection connection, Resource targetClass,
	                       Resource targetProperty, String expression) {
		ValueFactory vf = connection.getValueFactory();
		String nw = "http://numerateweb.org/vocab/math/rules#";
		IRI constraintClass = vf.createIRI(nw + "Constraint");
		IRI constraint = vf.createIRI(nw + "constraint");
		IRI onProperty = vf.createIRI(nw + "onProperty");
		IRI expressionString = vf.createIRI(nw + "expressionString");
		IRI prefixesResource = vf.createIRI(NS);

		Resource constraintResource = vf.createBNode();
		connection.add(targetClass, constraint, constraintResource);
		connection.add(constraintResource, RDF.TYPE, constraintClass);
		connection.add(constraintResource, onProperty, targetProperty);
		connection.add(constraintResource, expressionString, vf.createLiteral(expression));
		connection.add(constraintResource, SHACL.PREFIXES, prefixesResource);
	}

	@Test
	public void basicTest() {
		NumerateWebInferencer sail = createSail();
		Repository repository = new SailRepository(sail);
		ValueFactory vf = repository.getValueFactory();

		IRI targetClass = vf.createIRI(NS + "Rectangle");
		IRI targetProperty = vf.createIRI(NS + "area");
		try (RepositoryConnection connection = repository.getConnection()) {
			connection.begin(IsolationLevels.NONE);

			createPrefixes(connection);
			createConstraints(connection, targetClass, targetProperty, "@a * @b");

			for (int i = 0; i < 10; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);
				connection.add(r, RDF.TYPE, targetClass);
				connection.add(r, vf.createIRI(NS + "a"), vf.createLiteral(i));
				connection.add(r, vf.createIRI(NS + "b"), vf.createLiteral(2 * i));
			}
			connection.commit();

			for (int i = 0; i < 10; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);

				try (RepositoryResult<Statement> result = connection.getStatements(r, targetProperty, null)) {
					Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

					assertTrue(v.isPresent());
					assertTrue(v.get().isLiteral());
					assertEquals(i * (2 * i), ((Literal) v.get()).intValue());
				}

			}
		}

		repository.shutDown();
	}

	@Test
	public void incrementalTest() {
		NumerateWebInferencer sail = createSail();
		Repository repository = new SailRepository(sail);
		ValueFactory vf = repository.getValueFactory();

		IRI targetClass = vf.createIRI(NS + "Rectangle");
		IRI targetProperty = vf.createIRI(NS + "area");
		try (RepositoryConnection connection = repository.getConnection()) {
			connection.begin(IsolationLevels.NONE);

			createPrefixes(connection);
			createConstraints(connection, targetClass, targetProperty, "@a * @b");

			for (int i = 0; i < 5; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);
				connection.add(r, RDF.TYPE, targetClass);
				connection.add(r, vf.createIRI(NS + "a"), vf.createLiteral(i));
				connection.add(r, vf.createIRI(NS + "b"), vf.createLiteral(2 * i));
			}
			connection.commit();
		}

		try (RepositoryConnection connection = repository.getConnection()) {
			for (int i = 0; i < 5; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);

				try (RepositoryResult<Statement> result = connection.getStatements(r, targetProperty, null)) {
					Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

					assertTrue(v.isPresent());
					assertTrue(v.get().isLiteral());
					assertEquals(i * (2 * i), ((Literal) v.get()).intValue());
				}
			}
		}

		try (RepositoryConnection connection = repository.getConnection()) {
			connection.begin(IsolationLevels.NONE);

			// adds additional rectangles
			for (int i = 5; i < 10; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);
				connection.add(r, RDF.TYPE, targetClass);
				connection.add(r, vf.createIRI(NS + "a"), vf.createLiteral(i));
				connection.add(r, vf.createIRI(NS + "b"), vf.createLiteral(2 * i));
			}
			connection.commit();

			// ensure that all rectangles have correct areas
			for (int i = 0; i < 10; i++) {
				Resource r = vf.createIRI(NS + "rect" + i);

				try (RepositoryResult<Statement> result = connection.getStatements(r, targetProperty, null)) {
					Optional<Value> v = result.stream().map(st -> st.getObject()).findFirst();

					assertTrue(v.isPresent());
					assertTrue(v.get().isLiteral());
					assertEquals(i * (2 * i), ((Literal) v.get()).intValue());
				}
			}
		}

		repository.shutDown();
	}
}
