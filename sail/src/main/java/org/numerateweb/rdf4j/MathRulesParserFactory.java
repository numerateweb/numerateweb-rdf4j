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
package org.numerateweb.rdf4j;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFParserFactory;

/**
 * An {@link RDFParserFactory} for Numerate Web rules parsers.
 */
public class MathRulesParserFactory implements RDFParserFactory {

	/**
	 * Returns the corresponding format definition.
	 */
	@Override
	public RDFFormat getRDFFormat() {
		return Rdf4jMathRulesParser.NWRULES_FORMAT;
	}

	/**
	 * Returns a new instance of {@link Rdf4jMathRulesParser}.
	 */
	@Override
	public RDFParser getParser() {
		return new Rdf4jMathRulesParser();
	}
}