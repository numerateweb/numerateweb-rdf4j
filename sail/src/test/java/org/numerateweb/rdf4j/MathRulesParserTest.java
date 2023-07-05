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

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;
import org.junit.Test;

import java.io.IOException;

public class MathRulesParserTest {
	@Test
	public void basicTest() throws IOException {
		RDFParser parser = Rio.createParser(Rdf4jMathRulesParser.NWRULES_FORMAT);
		parser.setRDFHandler(new AbstractRDFHandler() {
			@Override
			public void handleStatement(Statement statement) throws RDFHandlerException {
				System.out.println(statement);
			}
		});
		parser.parse(getClass().getResource("/rules.nwrules").openStream());
	}
}
