package org.numerateweb.rdf4j;

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.Test;

import java.io.IOException;

public class NumerateWebInferencingTest {
	protected NumerateWebInferencer createSail() {
		NumerateWebInferencer sailStack = new NumerateWebInferencer(new MemoryStore());
		return sailStack;
	}

	@Test
	public void simpleTest() throws IOException {
		NumerateWebInferencer sail = createSail();
		Repository repository = new SailRepository(sail);
		try (RepositoryConnection connection = repository.getConnection()) {
			connection.add(getClass().getResource("/constraints-text.ttl"), RDFFormat.TURTLE);
		}
	}
}
