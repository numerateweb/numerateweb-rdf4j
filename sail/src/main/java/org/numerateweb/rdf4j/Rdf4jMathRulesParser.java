package org.numerateweb.rdf4j;

import com.google.common.io.CharStreams;
import net.enilink.komma.core.IGraph;
import net.enilink.komma.core.IReference;
import net.enilink.komma.core.LinkedHashGraph;
import net.enilink.komma.core.URI;
import net.enilink.komma.rdf4j.RDF4JValueConverter;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFParser;
import org.numerateweb.math.model.OMObject;
import org.numerateweb.math.model.OMObjectParser;
import org.numerateweb.math.ns.INamespaces;
import org.numerateweb.math.popcorn.rules.MathRulesParser;
import org.numerateweb.math.rdf.NWMathGraphBuilder;
import org.parboiled.Parboiled;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.errors.ParseError;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;
import org.parboiled.support.Position;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Rdf4jMathRulesParser extends AbstractRDFParser {
	static final RDFFormat NWRULES_FORMAT = new RDFFormat("NW-RULES", List.of("application/nwrules"),
			StandardCharsets.UTF_8, List.of("nwrules"), (IRI) null, true,
			false, false);
	static final String nw = "http://numerateweb.org/vocab/math/rules#";
	final MathRulesParser parser = Parboiled.createParser(MathRulesParser.class);

	@Override
	public RDFFormat getRDFFormat() {
		return NWRULES_FORMAT;
	}

	@Override
	public void parse(InputStream inputStream, String baseURI) throws IOException, RDFParseException, RDFHandlerException {
		parse(new InputStreamReader(inputStream), baseURI);
	}

	@Override
	public void parse(Reader reader, String baseURI) throws IOException, RDFParseException, RDFHandlerException {
		IRI constraintClass = valueFactory.createIRI(nw + "Constraint");
		IRI constraintProperty = valueFactory.createIRI(nw + "constraint");
		IRI expressionProperty = valueFactory.createIRI(nw + "expression");
		IRI onProperty = valueFactory.createIRI(nw + "onProperty");

		String content = CharStreams.toString(reader);
		ParsingResult<Object> result = new ReportingParseRunner<>(parser.Document()).run(content);
		if (result.matched && result.resultValue != null) {
			OMObject constraintSet = (OMObject) result.resultValue;
			List<OMObject> constraints = Arrays.stream(constraintSet.getArgs(), 1,
					constraintSet.getArgs().length).map(r -> (OMObject) r).collect(Collectors.toList());

			RDF4JValueConverter valueConverter = new RDF4JValueConverter(valueFactory);
			IGraph graph = new LinkedHashGraph();
			rdfHandler.startRDF();
			for (OMObject constraint : constraints) {
				if (constraint.getType() == OMObject.Type.OMA) {
					// load constraints into a map
					URI classUri = omrToUri((OMObject) constraint.getArgs()[1]);
					URI propertyUri = omrToUri((OMObject) constraint.getArgs()[2]);
					OMObject expression = (OMObject) constraint.getArgs()[3];
					IReference expressionResource = new OMObjectParser().parse(expression,
							new NWMathGraphBuilder(graph, INamespaces.empty()));

					// create the statements for describing the resource
					Resource constraintResource = valueFactory.createBNode();
					rdfHandler.handleStatement(valueFactory.createStatement(valueConverter.toRdf4j(classUri),
							constraintProperty, constraintResource));
					rdfHandler.handleStatement(valueFactory.createStatement(constraintResource,
							RDF.TYPE, constraintClass));
					rdfHandler.handleStatement(valueFactory.createStatement(constraintResource,
							onProperty, valueConverter.toRdf4j(propertyUri)));
					rdfHandler.handleStatement(valueFactory.createStatement(constraintResource,
							expressionProperty, valueConverter.toRdf4j(expressionResource)));

					// add RDF representation of expression to the graph
					graph.stream().map(valueConverter::toRdf4j).forEach(rdfHandler::handleStatement);
					graph.clear();
				}
			}
			rdfHandler.endRDF();
		} else {
			ParseError error = result.parseErrors.get(0);
			Position pos = error.getInputBuffer().getPosition(error.getStartIndex());
			throw new RDFParseException(ErrorUtils.printParseErrors(result), pos.line, pos.column);
		}
	}

	private URI omrToUri(OMObject omr) {
		return (URI) omr.getArgs()[0];
	}
}
