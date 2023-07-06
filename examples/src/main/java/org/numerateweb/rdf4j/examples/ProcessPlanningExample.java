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

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.numerateweb.math.rdf.rules.NWRULES;
import org.numerateweb.math.util.SparqlUtils;
import org.numerateweb.rdf4j.NumerateWebSail;

import java.io.IOException;

public class ProcessPlanningExample {

	public static void main(String... args) throws IOException {
		MemoryStore store = new MemoryStore();
		NumerateWebSail sail = new NumerateWebSail(store);
		Repository repository = new SailRepository(sail);
		try {
			ValueFactory vf = repository.getValueFactory();
			String path = "/process-planning/";
			try (RepositoryConnection connection = repository.getConnection()) {
				connection.begin();
				// add the model with concrete process-resource-combinations
				connection.add(ProcessPlanningExample.class.getResource(path + "model.ttl"),
						vf.createIRI("http://example.org/model"));
				// add vocabulary for describing processes and resources
				connection.add(ProcessPlanningExample.class.getResource(
						path + "processes-vocab.ttl"),
						vf.createIRI("http://example.org/vocab/processes"));
				// add data for the resources
				connection.add(ProcessPlanningExample.class.getResource(path + "resources.ttl"),
						vf.createIRI("http://example.org/resources"));
				// add the mathematical formulas
				connection.add(ProcessPlanningExample.class.getResource(path + "planning.nwrules"),
						vf.createIRI("http://example.org/processes/rules"));
				connection.commit();

				String query = SparqlUtils.prefix("rdfs", RDF.NAMESPACE) +
						SparqlUtils.prefix("mathrl", NWRULES.NAMESPACE)
						+ "select ?instance ?property (round(?value * 100000) / 1000 AS ?roundedValue) { " //
						+ "{ select * { "
						+ "  ?c mathrl:constraint [ mathrl:onProperty ?property ] ." //
						+ "  ?type rdfs:subClassOf* ?c" //
						+ "} }"
						+ "?instance a ?type ; ?property ?value" //
						+ "} order by ?instance ?property";

				connection.prepareTupleQuery(query).evaluate().stream().forEach(bindingSet -> {
					System.out.println(bindingSet.getBinding("instance").getValue() + ": " +
							bindingSet.getBinding("property").getValue() + " = " +
							((Literal) bindingSet.getBinding("roundedValue").getValue()).doubleValue());
				});
			}
		} finally {
			repository.shutDown();
		}
	}
}