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
