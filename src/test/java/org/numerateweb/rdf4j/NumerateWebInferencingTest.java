package org.numerateweb.rdf4j;

import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;

public class NumerateWebInferencingTest {
	protected NumerateWebInferencer createSail() {
		NumerateWebInferencer sailStack = new NumerateWebInferencer(new MemoryStore());
		return sailStack;
	}

	@Test
	public void simpleTest() throws IOException {
		NumerateWebInferencer sail = createSail();
		Repository repository = new SailRepository(sail);
		String ns = "http://example.org/vocab#";
		ValueFactory vf = repository.getValueFactory();

		Random rnd = new Random(1337);
		try (RepositoryConnection connection = repository.getConnection()) {
			connection.begin();
			try {
				connection.add(getClass().getResource("/constraints-text.ttl"), RDFFormat.TURTLE);

				for (int i = 0; i < 10000; i++) {
					Resource r = vf.createIRI(ns + "rect" + i++);
					connection.add(r, RDF.TYPE, vf.createIRI(ns + "Rectangle"));
					connection.add(r, vf.createIRI(ns + "a"), vf.createLiteral(1 + rnd.nextInt(10)));
					connection.add(r, vf.createIRI(ns + "b"), vf.createLiteral(1 + rnd.nextInt(10)));

					// add to list of shapes
					connection.add(vf.createIRI(ns + "shapes"), vf.createIRI(ns + "shape"), r);
				}
				connection.commit();
			} finally {
				if (connection.isActive()) {
					connection.rollback();
				}
			}
		}

		for (int i = 0; i < 100; i++) {
			long start = System.currentTimeMillis();

			// TODO inserting fixed value multiple times does not work
			String updateQuery = "prefix : <http://example.org/vocab#> " +
					"delete { ?s :a ?o } " +
					"insert { ?s :a " + i + " } " +
					"where { ?s a :Rectangle ; :a ?o }";
			try (RepositoryConnection connection = repository.getConnection()) {
				connection.prepareUpdate(updateQuery).execute();

				// connection.getStatements(null, vf.createIRI(ns + "a"), null).forEach(st -> System.out.println(st));
			}

			System.out.println("Update took: " + (System.currentTimeMillis() - start));
		}
	}
}
