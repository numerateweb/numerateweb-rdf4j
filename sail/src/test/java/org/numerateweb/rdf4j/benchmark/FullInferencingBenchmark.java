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

import org.eclipse.rdf4j.sail.NotifyingSail;
import org.numerateweb.rdf4j.NumerateWebSail;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmarks full inferencing performance.
 */
public class FullInferencingBenchmark extends InferencingBenchmarkBase {

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include("FullInferencingBenchmark") // adapt to control which benchmark tests to run
				.forks(1)
				.build();

		new Runner(opt).run();
	}

	@Benchmark
	public void fullInference() {
		addDataInOneTransaction();
	}

	@Override
	protected NumerateWebSail createInferencer(NotifyingSail store) {
		NumerateWebSail inferencer = new NumerateWebSail(store);
		inferencer.setIncrementalInference(false);
		return inferencer;
	}
}