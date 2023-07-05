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