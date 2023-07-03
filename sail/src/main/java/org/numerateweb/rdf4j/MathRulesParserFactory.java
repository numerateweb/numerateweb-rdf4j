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