package org.numerateweb.rdf4j.examples;

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.numerateweb.rdf4j.NumerateWebSail;

import java.io.IOException;

public class BrickExample {

	public static void main(String... args) throws IOException {
		MemoryStore store = new MemoryStore();
		NumerateWebSail sail = new NumerateWebSail(store);
		Repository repository = new SailRepository(sail);
		try {
			try (RepositoryConnection connection = repository.getConnection()) {
				connection.add(BrickExample.class.getResource("/brick-example.ttl"), RDFFormat.TURTLE);

				connection.getStatements(null,
						repository.getValueFactory().createIRI("https://brickschema.org/schema/Brick#grossArea"),
						null).stream().forEach(stmt -> {
					System.out.println(stmt.getSubject() + " has area " + stmt.getObject());
				});
			}
		} finally {
			repository.shutDown();
		}
	}
}