/*******************************************************************************
 * Copyright (c) 2023 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/

package org.numerateweb.rdf4j.benchmark;

import org.apache.commons.io.FileUtils;
import org.assertj.core.util.Files;
import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.sail.NotifyingSail;
import org.eclipse.rdf4j.sail.lmdb.LmdbStore;
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.numerateweb.rdf4j.NumerateWebInferencer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks inferencing performance.
 */
@State(Scope.Benchmark)
@Warmup(iterations = 2)
@BenchmarkMode({Mode.AverageTime})
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx3G", "-Xmn1G"})
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public abstract class InferencingBenchmarkBase {

	@Param({"2000"})
	int classes;

	@Param({"5"})
	int constraintsPerClass;

	@Param({"5"})
	int sharedPropertiesPerConstraint;

	@Param({"1"})
	int uniquePropertiesPerConstraint;

	@Param({"5"})
	int instancesPerClass;

	SailRepository repository;
	SailRepositoryConnection connection;
	File file;

	void createConstraints(String ns, Resource targetClass) {
		ValueFactory vf = repository.getValueFactory();
		String nw = "http://numerateweb.org/vocab/math/rules#";
		IRI constraintClass = vf.createIRI(nw + "Constraint");
		IRI constraint = vf.createIRI(nw + "constraint");
		IRI onProperty = vf.createIRI(nw + "onProperty");
		IRI expressionString = vf.createIRI(nw + "expressionString");
		IRI prefixes = vf.createIRI("http://www.w3.org/ns/shacl#prefixes");
		IRI prefixesResource = vf.createIRI(ns);

		for (int c = 0; c < constraintsPerClass; c++) {
			IRI constraintProperty = vf.createIRI(ns + "c" + c);

			StringBuilder expression = new StringBuilder();
			if (c > 0) {
				expression.append("@c" + (c - 1)).append(" + ");
			}
			for (int p = 0; p < uniquePropertiesPerConstraint; p++) {
				if (p > 0) {
					expression.append(" + ");
				}
				expression.append("@p" + c + "-" + p);
			}
			for (int p = 0; p < sharedPropertiesPerConstraint; p++) {
				if (p > 0) {
					expression.append(" + ");
				}
				expression.append("@p" + p);
			}

			Resource constraintResource = vf.createBNode();
			connection.add(targetClass, constraint, constraintResource);
			connection.add(constraintResource, RDF.TYPE, constraintClass);
			connection.add(constraintResource, onProperty, constraintProperty);
			connection.add(constraintResource, expressionString, vf.createLiteral(expression.toString()));
			connection.add(constraintResource, prefixes, prefixesResource);

			// System.out.println("c: " + c + " -> " + expression);
		}
	}

	protected void addDataInOneTransaction() {
		ValueFactory vf = repository.getValueFactory();
		String ns = "http://example.org/";
		Random rnd = new Random(1337);

		connection.begin(IsolationLevels.NONE);
		try {
			connection.add(getClass().getResource("/benchmark-base.ttl"), RDFFormat.TURTLE);

			for (int c = 0; c < classes; c++) {
				IRI clazz = vf.createIRI(ns + "Class-" + c);
				createConstraints(ns, clazz);

				for (int i = 0; i < instancesPerClass; i++) {
					Resource r = vf.createIRI(ns + "instance-" + c + "-" + i);
					connection.add(r, RDF.TYPE, clazz);
					for (int cNr = 0; cNr < constraintsPerClass; cNr++) {
						for (int p = 0; p < uniquePropertiesPerConstraint; p++) {
							connection.add(r, vf.createIRI(ns + "p" + cNr + "-" + p),
									vf.createLiteral(1 + rnd.nextInt(10)));
						}
					}

					for (int p = 0; p < sharedPropertiesPerConstraint; p++) {
						connection.add(r, vf.createIRI(ns + "p" + p),
								vf.createLiteral(1 + rnd.nextInt(10)));
					}
				}
			}
			connection.commit();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (connection.isActive()) {
				connection.rollback();
			}
		}
	}

	@Setup(Level.Invocation)
	public void beforeClass(BenchmarkParams params) {
		if (connection != null) {
			connection.close();
			connection = null;
		}
		file = Files.newTemporaryFolder();

		/*LmdbStoreConfig config = new LmdbStoreConfig();
		config.setForceSync(false);
		NotifyingSail store = new LmdbStore(file, config);*/
		MemoryStore store = new MemoryStore();
		NumerateWebInferencer sail = createInferencer(store);

		repository = new SailRepository(sail);
		connection = repository.getConnection();

		System.gc();
	}

	protected abstract NumerateWebInferencer createInferencer(NotifyingSail store);

	@TearDown(Level.Invocation)
	public void afterClass() throws IOException {
		if (connection != null) {
			connection.close();
			connection = null;
		}
		repository.shutDown();
		FileUtils.deleteDirectory(file);
	}
}