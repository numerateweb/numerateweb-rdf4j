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

import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.config.SailConfigException;
import org.eclipse.rdf4j.sail.config.SailFactory;
import org.eclipse.rdf4j.sail.config.SailImplConfig;
import org.numerateweb.rdf4j.NumerateWebSail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SailFactory} that creates {@link NumerateWebSail}s based on RDF configuration data.
 */
public class NumerateWebSailFactory implements SailFactory {

	/**
	 * The type of repositories that are created by this factory.
	 *
	 * @see SailFactory#getSailType()
	 */
	public static final String SAIL_TYPE = "rdf4j:NumerateWebSail";
	private static final Logger logger = LoggerFactory.getLogger(NumerateWebSailFactory.class);

	/**
	 * Returns the Sail's type: <tt>rdf4j:NumerateWebSail</tt>.
	 */
	@Override
	public String getSailType() {
		return SAIL_TYPE;
	}

	@Override
	public SailImplConfig getConfig() {
		return new NumerateWebSailConfig();
	}

	@Override
	public Sail getSail(SailImplConfig config) throws SailConfigException {
		if (!SAIL_TYPE.equals(config.getType())) {
			throw new SailConfigException("Invalid Sail type: " + config.getType());
		}

		NumerateWebSail sail = new NumerateWebSail();

		if (config instanceof NumerateWebSailConfig) {
			sail.setIncrementalInference(((NumerateWebSailConfig) config).getIncrementalInference());
		} else {
			logger.warn("Config is instance of {} is not NumerateWebSailConfig.", config.getClass().getName());
		}
		return sail;
	}
}