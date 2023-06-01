package org.numerateweb.rdf4j;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.TypeLiteral;
import net.enilink.commons.iterator.IExtendedIterator;
import net.enilink.commons.iterator.WrappedIterator;
import net.enilink.commons.util.Pair;
import net.enilink.komma.core.*;
import net.enilink.komma.literals.LiteralConverter;
import net.enilink.komma.rdf4j.RDF4JValueConverter;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.*;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.impl.ListBindingSet;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.parser.QueryParserUtil;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.helpers.SailConnectionWrapper;
import org.eclipse.rdf4j.sail.inferencer.InferencerConnection;
import org.numerateweb.math.model.OMObject;
import org.numerateweb.math.model.OMObjectBuilder;
import org.numerateweb.math.ns.INamespaces;
import org.numerateweb.math.popcorn.PopcornParser;
import org.numerateweb.math.rdf.NWMathGraphParser;
import org.numerateweb.math.rdf.rules.NWRULES;
import org.numerateweb.math.reasoner.*;
import org.numerateweb.math.util.SparqlUtils;
import org.parboiled.Parboiled;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Rdf4jModelAccess implements IModelAccess {
	protected static final String MATH_OBJECT_QUERY = new StringBuilder()
			.append("prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> ")
			.append("prefix math: <http://numerateweb.org/vocab/math#> ")
			.append("prefix mathrl: <http://numerateweb.org/vocab/math/rules#> ")
			.append("construct {")
			.append("?s ?p ?o . ")
			.append("} where {")
			.append("{ select ?s where { ?mathObj (math:arguments|math:symbol|math:operator|math:target|math:variables|math:binder|math:body|math:attributeKey|math:attributeValue|rdf:rest|rdf:first)* ?s . }} ")
			.append("?s ?p ?o .")
			.append("optional { ?s ?p ?o . bind (?s as ?result) filter not exists { [] rdf:rest ?s } }")
			.append("}").toString();
	protected static final ParsedQuery mathObjectQuery = QueryParserUtil.parseQuery(QueryLanguage.SPARQL, MATH_OBJECT_QUERY,
			null);
	static final String PREFIX = "PREFIX rdf: <" + RDF.NAMESPACE
			+ "> PREFIX rdfs: <" + RDFS.NAMESPACE + "> PREFIX owl: <"
			+ OWL.NAMESPACE + "> PREFIX sh: <" + SHACL.NAMESPACE + "> ";
	protected static final ParsedQuery directSuperClassesQuery = QueryParserUtil.parseQuery(QueryLanguage.SPARQL,
			SELECT_DIRECT_SUPERCLASSES(false), null);
	/**
	 * Comparator to sort resources according to their "semantic" level.
	 */
	static final Comparator<Resource> RANK_COMPARATOR = new Comparator<Resource>() {
		final String[] defaultNamespaces = {XSD.NAMESPACE, RDF.NAMESPACE, RDFS.NAMESPACE, OWL.NAMESPACE};

		@Override
		public int compare(Resource a, Resource b) {
			IRI aUri = a.isIRI() ? (IRI) a : null;
			IRI bUri = b.isIRI() ? (IRI) b : null;
			if (aUri == null) {
				if (bUri != null) {
					return 1;
				}
				return 0;
			} else if (bUri == null) {
				return -1;
			}
			return getRank(bUri.getNamespace()) - getRank(aUri.getNamespace());
		}

		int getRank(String namespace) {
			for (int i = 0; i < defaultNamespaces.length; i++) {
				if (namespace.equals(defaultNamespaces[i])) {
					return i;
				}
			}
			return defaultNamespaces.length + 1;
		}
	};
	private static final String CONSTRAINT_QUERY = PREFIX + SparqlUtils.prefix("mathrl", NWRULES.NAMESPACE)
			+ "SELECT distinct ?constraint ?property WHERE { " //
			+ "?c mathrl:constraint ?constraint . ?constraint mathrl:onProperty ?property " //
			+ "}";
	protected static final ParsedQuery constraintQuery = QueryParserUtil.parseQuery(QueryLanguage.SPARQL, CONSTRAINT_QUERY,
			null);
	private static final String SELECT_INSTANCES = PREFIX + "SELECT DISTINCT ?instance { ?instance a ?class . }";
	protected static final ParsedQuery instancesQuery = QueryParserUtil.parseQuery(QueryLanguage.SPARQL, SELECT_INSTANCES,
			null);
	protected final ValueFactory valueFactory;
	private final IRI USED_BY;
	private final ParsedQuery namespacesQuery = QueryParserUtil.parseQuery(QueryLanguage.SPARQL, PREFIX +
			"SELECT DISTINCT ?prefix ?namespace WHERE { ?resource sh:prefixes/owl:imports*/sh:declare [ sh:prefix ?prefix ; sh:namespace ?namespace ] }", null);
	private final Supplier<SailConnection> connection;
	private final Supplier<Dataset> dataset;
	private final RDF4JValueConverter valueConverter;
	private final LiteralConverter literalConverter;
	private final Map<Resource, Map<IReference, ResultSpec<OMObject>>> classToConstraints = new HashMap<>();
	private final Set<Pair<Resource, Resource>> dependencyCache = new HashSet<>();
	private final Map<IReference, IRI> propertyCache = new HashMap<>();
	private ICache<Resource, List<Resource>> resourceTypes;
	private Cache<String, OMObject> expressionCache = CacheBuilder.newBuilder().maximumSize(10000).build();

	public Rdf4jModelAccess(LiteralConverter literalConverter, ValueFactory valueFactory,
	                        Supplier<SailConnection> connection, Supplier<Dataset> dataset,
	                        CacheManager cacheManager) {
		this.literalConverter = literalConverter;
		this.valueFactory = valueFactory;
		this.connection = connection;
		this.dataset = dataset;
		this.valueConverter = new RDF4JValueConverter(valueFactory);
		this.resourceTypes = cacheManager.get(new TypeLiteral<>() {
		});
		this.USED_BY = valueFactory.createIRI("math:usedBy");
	}

	private static final String SELECT_DIRECT_SUPERCLASSES(boolean named) {
		return PREFIX
				+ "SELECT DISTINCT ?superClass { "
				+ "?subClass rdfs:subClassOf ?superClass . "
				+ "FILTER NOT EXISTS { ?superClass a owl:Restriction } "
				+ "FILTER NOT EXISTS {"
				+ "?subClass rdfs:subClassOf ?otherSuperClass . "
				+ "?otherSuperClass rdfs:subClassOf ?superClass . "
				+ "FILTER (?subClass != ?otherSuperClass && ?superClass != ?otherSuperClass"
				+ (named ? " && isIRI(?otherSuperClass)" : "") + ")"
				+ "} FILTER (?subClass != ?superClass"
				+ (named ? "&& isIRI(?superClass)" : "")
				+ ") } ORDER BY ?superClass";
	}

	OMObject parseExpression(Resource mathObj) {
		BindingSet bindingSet = new ListBindingSet(List.of("mathObj"), List.of(mathObj));
		IGraph statements = new LinkedHashGraph();
		try (CloseableIteration<? extends BindingSet, QueryEvaluationException> bindingsIter = connection
				.get().evaluate(mathObjectQuery.getTupleExpr(), dataset.get(), bindingSet, false)) {
			while (bindingsIter.hasNext()) {
				BindingSet bindings = bindingsIter.next();
				Value subj = bindings.getValue("s");
				Value pred = bindings.getValue("p");
				Value obj = bindings.getValue("o");
				if (subj instanceof Resource && pred instanceof IRI && obj != null) {
					statements.add(new Statement(valueConverter.fromRdf4j((Resource) subj),
							valueConverter.fromRdf4j((IRI) pred), valueConverter.fromRdf4j(obj)));
				}
			}
		}
		NWMathGraphParser parser = new NWMathGraphParser(statements, INamespaces.empty());
		return parser.parse(valueConverter.fromRdf4j(mathObj), new OMObjectBuilder());
	}

	protected List<Resource> getDirectClasses(Resource resource) {
		CacheResult<List<Resource>> result = resourceTypes.get(resource);
		if (result != null) {
			return result.value;
		}
		List<Resource> types = ((SailConnectionWrapper) connection.get()).getWrappedConnection().
				getStatements(resource, RDF.TYPE, null, false)
				.stream().map(org.eclipse.rdf4j.model.Statement::getObject)
				.filter(r -> r instanceof Resource).map(r -> (Resource) r)
				.collect(Collectors.toList());
		resourceTypes.put(resource, types);
		return types;
	}

	protected List<Resource> getDirectSuperClasses(Resource clazz) {
		Set<Resource> classes = new HashSet<>();
		BindingSet bindingSet = new ListBindingSet(List.of("subClass"), List.of(clazz));
		try (CloseableIteration<? extends BindingSet, QueryEvaluationException> bindingsIter = connection.get()
				.evaluate(directSuperClassesQuery.getTupleExpr(), dataset.get(), bindingSet, false)) {
			while (bindingsIter.hasNext()) {
				BindingSet bindings = bindingsIter.next();
				classes.add((Resource) bindings.getValue("superClass"));
			}
		}
		return new ArrayList<>(classes);
	}

	@Override
	public ResultSpec<OMObject> getExpressionSpec(Object subject, IReference property) {
		Resource subjectResource = (Resource) subject;
		for (Resource clazz : sort(getDirectClasses(subjectResource))) {
			Map<IReference, ResultSpec<OMObject>> constraints = getConstraintsForClass(clazz);
			ResultSpec<OMObject> expressionSpec = constraints.get(property);
			if (expressionSpec != null) {
				return expressionSpec;
			}
		}
		return ResultSpec.empty();
	}

	public Set<IReference> getPropertiesWithConstraintsOfResource(Resource resource) {
		Set<IReference> properties = new HashSet<>();
		for (Resource clazz : sort(getDirectClasses(resource))) {
			properties.addAll(getConstraintsForClass(clazz).keySet());
		}
		return properties;
	}

	protected Map<IReference, ResultSpec<OMObject>> getConstraintsForClass(Resource clazz) {
		Map<IReference, ResultSpec<OMObject>> constraints = classToConstraints.get(clazz);
		if (constraints == null) {
			constraints = new HashMap<>();
			classToConstraints.put(clazz, constraints);

			BindingSet bindingSet = new ListBindingSet(List.of("c"), List.of(clazz));
			try (CloseableIteration<? extends BindingSet, QueryEvaluationException> bindingsIter = connection.get()
					.evaluate(constraintQuery.getTupleExpr(), dataset.get(), bindingSet, false)) {
				while (bindingsIter.hasNext()) {
					BindingSet bindings = bindingsIter.next();
					Resource constraintResource = (Resource) bindings.getValue("constraint");
					Resource propertyResource = (IRI) bindings.getValue("property");

					OMObject mathObj = null;
					try (CloseableIteration<? extends org.eclipse.rdf4j.model.Statement, SailException> stmts =
							     connection.get().getStatements(constraintResource,
									     valueConverter.toRdf4j(NWRULES.NAMESPACE_URI.appendLocalPart("expressionString")), null, false,
									     dataset.get().getDefaultGraphs().toArray(new Resource[dataset.get().getDefaultGraphs().size()]))) {
						if (stmts.hasNext()) {
							org.eclipse.rdf4j.model.Statement stmt = stmts.next();
							String expString = ((org.eclipse.rdf4j.model.Literal) stmt.getObject()).getLabel();

							mathObj = expressionCache.get(expString, () -> {
								INamespaces namespaces = getNamespaces(stmt.getSubject());
								PopcornParser popcornParser = Parboiled.createParser(PopcornParser.class, namespaces);
								ParsingResult<Object> result = new ReportingParseRunner<>(popcornParser.Expr()).run(expString);
								if (result.matched && result.resultValue != null) {
									return (OMObject) result.resultValue;
								} else {
									// an error has occurred during parsing
									return OMObject.OME(new OMObject[]{OMObject.OMS("nw:error"),
											OMObject.OMSTR(ErrorUtils.printParseErrors(result))});
								}
							});
						}
					} catch (ExecutionException e) {
						mathObj = OMObject.OME(new OMObject[]{OMObject.OMS("nw:error"),
								OMObject.OMSTR(e.toString())});
					}
					if (mathObj == null) {
						mathObj = parseExpression(constraintResource);
					}
					constraints.put(valueConverter.fromRdf4j(propertyResource),
							ResultSpec.create(Cardinality.SINGLE, mathObj));
				}
			}

			// add inherited constraints from super classes
			for (Resource superClass : sort(getDirectSuperClasses(clazz))) {
				for (Map.Entry<IReference, ResultSpec<OMObject>> superConstraint : getConstraintsForClass(superClass).entrySet()) {
					if (!constraints.containsKey(superConstraint.getKey())) {
						constraints.put(superConstraint.getKey(), superConstraint.getValue());
					}
				}
			}
		}
		return constraints;
	}

	protected INamespaces getNamespaces(Resource constraint) {
		SimpleNamespaces namespaces = new SimpleNamespaces(INamespaces.empty());
		BindingSet bindingSet = new ListBindingSet(List.of("resource"), List.of(constraint));
		try (CloseableIteration<? extends BindingSet, QueryEvaluationException> bindingsIter = connection.get()
				.evaluate(namespacesQuery.getTupleExpr(), dataset.get(), bindingSet, false)) {
			while (bindingsIter.hasNext()) {
				BindingSet bindings = bindingsIter.next();
				String prefix = ((org.eclipse.rdf4j.model.Literal) bindings.getValue("prefix")).getLabel();
				String namespace = ((org.eclipse.rdf4j.model.Literal) bindings.getValue("namespace")).getLabel();
				namespaces.set(prefix, URIs.createURI(namespace));
			}
		}
		return namespaces;
	}

	@Override
	public IExtendedIterator<?> getInstances(IReference clazz) {
		BindingSet bindingSet = new ListBindingSet(List.of("class"), List.of(valueConverter.toRdf4j(clazz)));
		List<Object> instances = new ArrayList<>();
		try (CloseableIteration<? extends BindingSet, QueryEvaluationException> bindingsIter = connection.get()
				.evaluate(instancesQuery.getTupleExpr(), dataset.get(), bindingSet, false)) {
			while (bindingsIter.hasNext()) {
				BindingSet bindings = bindingsIter.next();
				Resource instance = (Resource) bindings.getValue("instance");
				instances.add(instance);
			}
		}
		return WrappedIterator.create(instances.iterator());
	}

	@Override
	public IReference createInstance(Object scope, URI property, URI uri, IReference clazz, Map<URI, Object> args) {
		return null;
	}

	@Override
	public IExtendedIterator<?> getPropertyValues(Object subject, IReference property,
	                                              Optional<IReference> restriction) {
		SailConnection baseConn = ((SailConnectionWrapper) connection.get()).getWrappedConnection();
		IRI propertyIri = propertyCache.computeIfAbsent(property, p -> (IRI) valueConverter.toRdf4j(p));
		Stream<? extends org.eclipse.rdf4j.model.Statement> stmts = baseConn.getStatements((Resource) subject,
				propertyIri, null, false).stream();
		if (restriction.isPresent()) {
			Resource restrictionResource = valueConverter.toRdf4j(restriction.get());
			stmts = stmts.filter(stmt -> stmt.getObject().isLiteral() ? true :
					baseConn.hasStatement((Resource) stmt.getObject(), RDF.TYPE, restrictionResource, true));
		}
		return WrappedIterator.create(stmts.map(stmt -> {
			if (stmt.getObject().isLiteral()) {
				return literalConverter.createObject((ILiteral) valueConverter.fromRdf4j(stmt.getObject()));
			}
			return stmt.getObject();
		}).collect(Collectors.toList()).iterator());
	}

	protected List<Resource> sort(List<Resource> classes) {
		Collections.sort(classes, RANK_COMPARATOR);
		return classes;
	}

	public void setPropertyValue(Object subject, IReference property, List<Object> results) {
		// ((InferencerConnection) connection.get()).removeInferredStatement((Resource) subject,
		//		(IRI) valueConverter.toRdf4j(property), null);
		if (!results.isEmpty()) {
			Object value = results.get(0);
			Value rdfValue;
			if (value instanceof Resource) {
				rdfValue = (Resource) value;
			} else if (value instanceof IValue) {
				rdfValue = valueConverter.toRdf4j((IValue) value);
			} else {
				rdfValue = valueConverter.toRdf4j(literalConverter.createLiteral(value, null));
			}
			((InferencerConnection) connection.get()).addInferredStatement((Resource) subject,
					(IRI) valueConverter.toRdf4j(property), rdfValue);
		}
		//System.out.println(String.format("%s: %s = %s", subject, property, results));
	}

	void addDependency(Pair<Object, IReference> from, Pair<Object, IReference> to) {
		if (dependencyCache.add(new Pair<>((Resource) from.getFirst(), (Resource) to.getFirst()))) {
			((InferencerConnection) connection.get()).addInferredStatement((Resource) to.getFirst(),
					USED_BY, (Resource) from.getFirst());
		}
	}

	void clearDependencyCache() {
		dependencyCache.clear();
	}

	public void invalidateType(Resource subject) {
		resourceTypes.remove(subject);
	}
}
