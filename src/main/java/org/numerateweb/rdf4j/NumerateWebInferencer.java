package org.numerateweb.rdf4j;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import net.enilink.komma.core.IReference;
import net.enilink.komma.core.KommaModule;
import net.enilink.komma.literals.LiteralConverter;
import net.enilink.komma.rdf4j.RDF4JValueConverter;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.transaction.IsolationLevel;
import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.impl.ListBindingSet;
import org.eclipse.rdf4j.query.impl.SimpleDataset;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.parser.QueryParserUtil;
import org.eclipse.rdf4j.sail.NotifyingSail;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.helpers.NotifyingSailWrapper;
import org.eclipse.rdf4j.sail.inferencer.InferencerConnection;
import org.numerateweb.math.rdf.NWMathModule;
import org.numerateweb.math.rdf.rules.NWRULES;
import org.numerateweb.math.reasoner.CacheManager;
import org.numerateweb.math.reasoner.ICache;
import org.numerateweb.math.reasoner.ICacheFactory;
import org.numerateweb.math.util.SparqlUtils;

import java.util.*;
import java.util.function.Supplier;

public class NumerateWebInferencer extends NotifyingSailWrapper {

	private static final String TARGETS_QUERY = Rdf4jModelAccess.PREFIX +
			SparqlUtils.prefix("mathrl", NWRULES.NAMESPACE)
			+ "SELECT ?instance ?property ?targetGraph WHERE { " //
			+ "?c mathrl:constraint ?constraint . ?constraint mathrl:onProperty ?property ." //
			// + "?targetGraph owl:imports* ?constraints ."
			+ "?instance a [ rdfs:subClassOf* ?c ]" //
			//+ "optional { ?instance a [ rdfs:subClassOf* ?c ] filter ! bound(?targetGraph) }"
			+ "}";
	protected static final ParsedQuery targetsQuery = QueryParserUtil.parseQuery(QueryLanguage.SPARQL, TARGETS_QUERY,
			null);
	private static final String INVALID_TARGETS_QUERY = Rdf4jModelAccess.PREFIX +
			SparqlUtils.prefix("mathrl", NWRULES.NAMESPACE)
			+ "SELECT distinct ?instance { " //
			+ "{ select ?invalidated { ?invalidated <math:invalid> true } } ?invalidated <math:usedBy>* ?instance ."
			+ "}";
	protected static final ParsedQuery invalidTargetsQuery = QueryParserUtil.parseQuery(QueryLanguage.SPARQL, INVALID_TARGETS_QUERY,
			null);
	private static final IsolationLevels READ_COMMITTED = IsolationLevels.READ_COMMITTED;
	protected final CacheManager cacheManager = new CacheManager(new ICacheFactory() {
		@Override
		public <K, V> ICache<K, V> create() {
			return new GuavaCache<>();
		}
	});
	protected Injector injector;
	protected RDF4JValueConverter valueConverter;
	protected LiteralConverter literalConverter;
	protected Map<Resource, Rdf4jEvaluator> evaluators = new HashMap<>();
	protected boolean initialInferencingDone = false;
	protected ThreadLocal<SailConnection> connection = new ThreadLocal<>();
	protected Rdf4jModelAccess modelAccess;
	volatile boolean inferencing = false;
	IRI USED_BY;
	IRI CONSTRAINT_PROPERTY;
	private Set<Resource> changedResources = new HashSet<>();
	private Set<Resource> changedClasses = new HashSet<>();

	public NumerateWebInferencer() {
		super();
	}

	public NumerateWebInferencer(NotifyingSail baseSail) {
		super(baseSail);
	}

	@Override
	public void init() throws SailException {
		super.init();
		KommaModule module = new NWMathModule();
		injector = Guice.createInjector(new LiteralConverterModule(module), new AbstractModule() {
			@Override
			protected void configure() {
				bind(Locale.class).toInstance(Locale.getDefault());
				bind(ValueFactory.class).toInstance(getValueFactory());
			}
		});
		valueConverter = injector.getInstance(RDF4JValueConverter.class);
		literalConverter = injector.getInstance(LiteralConverter.class);
		USED_BY = getValueFactory().createIRI("math:usedBy");
		CONSTRAINT_PROPERTY = getValueFactory().createIRI(NWRULES.PROPERTY_CONSTRAINT.toString());
		Supplier<SailConnection> connSupplier = () -> this.connection.get();
		SimpleDataset dataset = new SimpleDataset();
		Supplier<Dataset> datasetSupplier = () -> dataset;
		modelAccess = new Rdf4jModelAccess(literalConverter,
				getValueFactory(), connSupplier, datasetSupplier, cacheManager);
	}

	@Override
	public NotifyingSailConnection getConnection() throws SailException {
		return new NumerateWebInferencerConnection(this, (InferencerConnection) super.getConnection());
	}

	void update(SailConnection connection, Statement stmt, boolean added) {
		if (inferencing) {
			return;
		}
		changedResources.add(stmt.getSubject());
		if (CONSTRAINT_PROPERTY.equals(stmt.getPredicate()) || RDFS.SUBCLASSOF.equals(stmt.getPredicate())) {
			changedClasses.add(stmt.getSubject());
		}

		// handle changes of resource types
		if (RDF.TYPE.equals(stmt.getPredicate())) {
			modelAccess.invalidateType(stmt.getSubject());
		}

		// this is required to correctly invalidate any cached values
		if (!evaluators.isEmpty()) {
			Rdf4jEvaluator evaluator = evaluators.get(stmt.getContext());
			if (evaluator != null) {
				evaluator.invalidateCache(stmt.getSubject(), valueConverter.fromRdf4j(stmt.getPredicate()));
			}
		}
	}

	public void reevaluate(SailConnection connection) {
		try {
			inferencing = true;

			modelAccess.clearDependencyCache();

			this.connection.set(connection);
			if (!initialInferencingDone) {
				doFullInferencing(connection);
				initialInferencingDone = true;
			} else {
				doIncrementalInferencing(connection);
			}
		} finally {
			inferencing = false;
		}
	}

	public void doIncrementalInferencing(SailConnection connection) {
		Set<Resource> toUpdate = new HashSet<>();
		for (Resource instance : changedResources) {
			toUpdate.add(instance);

			Rdf4jEvaluator evaluator = evaluators.get(null);
			Set<IReference> properties = modelAccess.getPropertiesWithConstraintsOfResource(instance);
			for (IReference property : properties) {
				if (evaluator != null) {
					evaluator.invalidateCache(instance, property);
				}
				((InferencerConnection) connection).removeInferredStatement(instance,
						(IRI) valueConverter.toRdf4j(property), null);
			}
			if (!properties.isEmpty()) {
				connection.getStatements(instance, USED_BY, null, true).stream().forEach(stmt -> {
					toUpdate.add((Resource) stmt.getObject());
					((InferencerConnection) connection).removeInferredStatement(instance, USED_BY, stmt.getObject());
				});
			}
		}

		for (Resource instance : toUpdate) {
			Rdf4jEvaluator evaluator = evaluators.computeIfAbsent(null, graph -> {
				return new Rdf4jEvaluator(modelAccess, cacheManager);
			});
			for (IReference property : modelAccess.getPropertiesWithConstraintsOfResource(instance)) {
				evaluator.evaluateRoot(instance, property, Optional.empty());
			}
		}
		changedResources.clear();
		changedClasses.clear();
	}

	public void doFullInferencing(SailConnection connection) {
		SimpleDataset dataset = new SimpleDataset();
		BindingSet bindingSet = new ListBindingSet(List.of(), List.of());
		try (CloseableIteration<? extends BindingSet, QueryEvaluationException> bindingsIter = connection
				.evaluate(targetsQuery.getTupleExpr(), dataset, bindingSet, false)) {
			while (bindingsIter.hasNext()) {
				BindingSet bindings = bindingsIter.next();
				Resource instance = (Resource) bindings.getValue("instance");
				Resource property = (Resource) bindings.getValue("property");
				Resource targetGraph = (Resource) bindings.getValue("targetGraph");
				Rdf4jEvaluator evaluator = evaluators.computeIfAbsent(targetGraph, graph -> {
					return new Rdf4jEvaluator(modelAccess, cacheManager);
				});
				evaluator.evaluateRoot(instance, valueConverter.fromRdf4j(property), Optional.empty());
			}
		}
	}

	@Override
	public IsolationLevel getDefaultIsolationLevel() {
		IsolationLevel level = super.getDefaultIsolationLevel();
		if (level.isCompatibleWith(READ_COMMITTED)) {
			return level;
		} else {
			List<IsolationLevel> supported = this.getSupportedIsolationLevels();
			return IsolationLevels.getCompatibleIsolationLevel(READ_COMMITTED, supported);
		}
	}

	@Override
	public List<IsolationLevel> getSupportedIsolationLevels() {
		List<IsolationLevel> supported = super.getSupportedIsolationLevels();
		List<IsolationLevel> levels = new ArrayList<>(supported.size());
		for (IsolationLevel level : supported) {
			if (level.isCompatibleWith(READ_COMMITTED)) {
				levels.add(level);
			}
		}
		return levels;
	}
}