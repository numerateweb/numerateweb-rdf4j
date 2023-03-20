/*******************************************************************************
 * Copyright (c) 2021 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/

package org.numerateweb.rdf4j.benchmark;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.assertj.core.util.Files;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.sail.lmdb.LmdbStore;
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig;
import org.numerateweb.rdf4j.NumerateWebInferencer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmarks inferencing performance.
 */
@State(Scope.Benchmark)
@Warmup(iterations = 2)
@BenchmarkMode({Mode.AverageTime})
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G", "-Xmn1G"})
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class InferencingBenchmark {

	@Param({"10"})
	int constraintsPerClass;

	@Param({"10"})
	int propertiesPerConstraint;

	@Param({"10000"})
	int instances;

	private SailRepository repository;
	private SailRepositoryConnection connection;
	private File file;

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include("InferencingBenchmark") // adapt to control which benchmark tests to run
				.forks(1)
				.build();

		new Runner(opt).run();
	}

	void createConstraints(String ns, Resource targetClass) {
		ValueFactory vf = repository.getValueFactory();
		IRI constraintClass = vf.createIRI("http://numerateweb.org/vocab/math/rules#Constraint");
		IRI constraint = vf.createIRI("http://numerateweb.org/vocab/math/rules#constraint");
		IRI onProperty = vf.createIRI("http://numerateweb.org/vocab/math/rules#onProperty");
		IRI expressionString = vf.createIRI("http://numerateweb.org/vocab/math/rules#expressionString");
		IRI prefixes = vf.createIRI("http://www.w3.org/ns/shacl#prefixes");
		IRI prefixesResource = vf.createIRI(ns);

		for (int c = 0; c < constraintsPerClass; c++) {
			IRI constraintProperty = vf.createIRI(ns + "c" + c);

			StringBuilder expression = new StringBuilder();
			if (c > 0) {
				expression.append("@c" + (c - 1)).append(" + ");
			}
			for (int p = 0; p < propertiesPerConstraint; p++) {
				if (p > 0) {
					expression.append(" + ");
				}
				expression.append("@p" + c + "-" + p);
			}

			Resource constraintResource = vf.createBNode();
			connection.add(targetClass, constraint, constraintResource);
			connection.add(constraintResource, RDF.TYPE, constraintClass);
			connection.add(constraintResource, onProperty, constraintProperty);
			connection.add(constraintResource, expressionString, vf.createLiteral(expression.toString()));
			connection.add(constraintResource, prefixes, prefixesResource);

			System.out.println("c: " + c + " -> " + expression);
		}
	}

	@Benchmark
	public void transactions() {
		ValueFactory vf = repository.getValueFactory();
		String ns = "http://example.org/vocab#";
		Random rnd = new Random(1337);

		connection.begin();
		try {
			connection.add(getClass().getResource("/benchmark-base.ttl"), RDFFormat.TURTLE);

			IRI clazz = vf.createIRI(ns + "Object");
			createConstraints(ns, clazz);

			for (int i = 0; i < instances; i++) {
				Resource r = vf.createIRI(ns + "instance" + i++);
				connection.add(r, RDF.TYPE, clazz);
				for (int c = 0; c < constraintsPerClass; c++) {
					for (int p = 0; p < propertiesPerConstraint; p++) {
						connection.add(r, vf.createIRI(ns + "p" + c + "-" + p), vf.createLiteral(1 + rnd.nextInt(10)));
					}
				}

				// add to list of shapes
				// connection.add(vf.createIRI(ns + "shapes"), vf.createIRI(ns + "shape"), r);
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
	public void beforeClass() {
		if (connection != null) {
			connection.close();
			connection = null;
		}
		file = Files.newTemporaryFolder();

		LmdbStoreConfig config = new LmdbStoreConfig();
		config.setForceSync(false);
		LmdbStore store = new LmdbStore(file, config);
		NumerateWebInferencer sail = new NumerateWebInferencer(store);

		repository = new SailRepository(sail);
		connection = repository.getConnection();

		System.gc();
	}

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