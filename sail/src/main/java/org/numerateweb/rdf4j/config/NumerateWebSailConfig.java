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
package org.numerateweb.rdf4j.config;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.ModelException;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.sail.base.config.BaseSailConfig;
import org.eclipse.rdf4j.sail.config.SailConfigException;

public class NumerateWebSailConfig extends BaseSailConfig {
	private boolean incrementalInference = false;

	public NumerateWebSailConfig() {
		super(NumerateWebSailFactory.SAIL_TYPE);
	}

	public NumerateWebSailConfig(boolean incrementalInference) {
		this();
		setIncrementalInference(incrementalInference);
	}

	public boolean getIncrementalInference() {
		return incrementalInference;
	}

	public NumerateWebSailConfig setIncrementalInference(boolean incrementalInference) {
		this.incrementalInference = incrementalInference;
		return this;
	}

	@Override
	public Resource export(Model m) {
		Resource implNode = super.export(m);
		ValueFactory vf = SimpleValueFactory.getInstance();

		m.setNamespace("ns", NumerateWebSailSchema.NAMESPACE);
		if (!incrementalInference) {
			m.add(implNode, NumerateWebSailSchema.INCREMENTAL_INFERENCE, vf.createLiteral(false));
		}
		return implNode;
	}

	@Override
	public void parse(Model m, Resource implNode) throws SailConfigException {
		super.parse(m, implNode);

		try {
			Models.objectLiteral(m.getStatements(implNode, NumerateWebSailSchema.INCREMENTAL_INFERENCE, null)).ifPresent(lit -> {
				try {
					setIncrementalInference(lit.booleanValue());
				} catch (IllegalArgumentException e) {
					throw new SailConfigException(
							"Boolean value required for " + NumerateWebSailSchema.INCREMENTAL_INFERENCE + " property, found " + lit);
				}
			});
		} catch (ModelException e) {
			throw new SailConfigException(e.getMessage(), e);
		}
	}
}