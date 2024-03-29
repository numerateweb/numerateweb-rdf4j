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
package org.numerateweb.rdf4j.benchmark;

import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.sail.NotifyingSail;
import org.numerateweb.rdf4j.NumerateWebSail;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;

/**
 * Benchmarks incremental inferencing performance.
 */
public class IncrementalInferencingBenchmark extends InferencingBenchmarkBase {

	@Param({"10", "25", "50", "100"})
	int modifyValuesPercent;

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include("IncrementalInferencingBenchmark") // adapt to control which benchmark tests to run
				.forks(1)
				.build();

		new Runner(opt).run();
	}

	@Benchmark
	public void incrementalInference() {
		ValueFactory vf = repository.getValueFactory();
		String ns = "http://example.org/";
		Random rnd = new Random(1337);

		connection.begin(IsolationLevels.NONE);
		try {
			for (int c = 0; c < classes; c++) {
				for (int i = 0; i < instancesPerClass; i++) {
					if (rnd.nextInt(100) <= modifyValuesPercent) {
						Resource r = vf.createIRI(ns + "instance-" + c + "-" + i);
						for (int cNr = 0; cNr < constraintsPerClass; cNr++) {
							for (int p = 0; p < uniquePropertiesPerConstraint; p++) {
								connection.remove(r, vf.createIRI(ns + "p" + cNr + "-" + p), null);
								connection.add(r, vf.createIRI(ns + "p" + cNr + "-" + p),
										vf.createLiteral(1 + rnd.nextInt(10)));
							}
						}

						for (int p = 0; p < sharedPropertiesPerConstraint; p++) {
							connection.remove(r, vf.createIRI(ns + "p" + p), null);
							connection.add(r, vf.createIRI(ns + "p" + p),
									vf.createLiteral(1 + rnd.nextInt(10)));
						}
					}
				}
			}
			connection.commit();
		} finally {
			if (connection.isActive()) {
				connection.rollback();
			}
		}
	}

	@Override
	public void beforeClass(BenchmarkParams params) {
		super.beforeClass(params);
		addDataInOneTransaction();
		System.gc();
	}

	@Override
	protected NumerateWebSail createInferencer(NotifyingSail store) {
		NumerateWebSail inferencer = new NumerateWebSail(store);
		inferencer.setIncrementalInference(true);
		return inferencer;
	}
}