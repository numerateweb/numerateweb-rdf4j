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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.OWL;
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
import org.numerateweb.math.util.SparqlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;

public class NumerateWebSail extends NotifyingSailWrapper {

	static private final Logger logger = LoggerFactory.getLogger(NumerateWebSail.class);

	private static final String TARGETS_QUERY = Rdf4jModelAccess.PREFIX +
			SparqlUtils.prefix("mathrl", NWRULES.NAMESPACE)
			+ "SELECT ?instance ?property ?targetGraph { " //
			+ "{ select * { "
			+ "  ?c mathrl:constraint ?constraint . ?constraint mathrl:onProperty ?property ." //
			+ "  ?type rdfs:subClassOf* ?c" //
			+ "} }"
			+ "{ graph ?targetGraph { ?instance a ?type } } union { ?instance a ?type filter (!bound(?targetGraph)) }" //
			+ "}";
	protected static final ParsedQuery targetsQuery = QueryParserUtil.parseQuery(QueryLanguage.SPARQL, TARGETS_QUERY,
			null);
	private static final IsolationLevels READ_COMMITTED = IsolationLevels.READ_COMMITTED;
	private static final DatasetInfo EMPTY_DATASET = new DatasetInfo();
	protected final CacheManager cacheManager = new CacheManager(GuavaCache::new);
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
	private DatasetInfo activeDataset = EMPTY_DATASET;
	private Cache<Resource, DatasetInfo> datasetCache = CacheBuilder.newBuilder().maximumSize(10000).build();
	private boolean incrementalInference = true;

	private Cache<Object, CachedEntity> propertyCache;

	public NumerateWebSail() {
		super();
	}

	public NumerateWebSail(NotifyingSail baseSail) {
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
		Supplier<Resource[]> contextSupplier = () -> this.activeDataset.context;
		Supplier<Dataset> datasetSupplier = () -> this.activeDataset.dataset;
		modelAccess = new Rdf4jModelAccess(literalConverter,
				getValueFactory(), connSupplier, contextSupplier, datasetSupplier, cacheManager);
		propertyCache = CacheBuilder.newBuilder().maximumSize(100000).build();
	}

	@Override
	public NotifyingSailConnection getConnection() throws SailException {
		return new NumerateWebSailConnection(this, (InferencerConnection) super.getConnection());
	}

	public synchronized void reevaluate(SailConnection connection, Set<Resource> changedResources,
	                                    Set<Resource> changedClasses) {
		try {
			inferencing = true;

			modelAccess.clearDependencyCache();

			this.connection.set(connection);
			if (!initialInferencingDone || !incrementalInference) {
				doFullInferencing(connection);
				initialInferencingDone = true;
			} else {
				doIncrementalInferencing(connection, changedResources, changedClasses);
			}
		} finally {
			inferencing = false;
		}
	}

	public void doIncrementalInferencing(SailConnection connection, Set<Resource> changedResources,
	                                     Set<Resource> changedClasses) {
		Set<Resource> seen = new HashSet<>();
		Queue<Resource> toUpdate = new LinkedList<>();

		logger.info("Updating {} resources", changedResources.size());

		for (Resource instance : changedResources) {
			// could be optimized by just removing the changed properties
			propertyCache.invalidate(instance);

			ResourceInfo instanceInfo = modelAccess.getResourceInfo(instance);
			Set<IReference> properties = modelAccess.getPropertiesWithConstraintsOfResource(instanceInfo);
			for (IReference property : properties) {
				((InferencerConnection) connection).removeInferredStatement(instance,
						modelAccess.mapProperty(property), null);
			}
			if (!properties.isEmpty()) {
				if (seen.add(instance)) {
					toUpdate.add(instance);
				}
				// remove all incoming usedBy edges
				connection.getStatements(null, USED_BY, instance, true).stream().forEach(stmt -> {
					Resource parameter = stmt.getSubject();
					((InferencerConnection) connection).removeInferredStatement(parameter, USED_BY,
							instance, stmt.getContext());
				});
			}
		}
		changedResources.clear();
		changedClasses.clear();

		while (!toUpdate.isEmpty()) {
			Resource instance = toUpdate.remove();
			ResourceInfo instanceInfo = modelAccess.getResourceInfo(instance);

			for (Resource context : instanceInfo.contexts) {
				activeDataset = getDataset(context, connection);
				Rdf4jEvaluator evaluator = evaluators.computeIfAbsent(context, graph -> {
					return new Rdf4jEvaluator(modelAccess, propertyCache, cacheManager);
				});
				for (IReference property : modelAccess.getPropertiesWithConstraintsOfResource(instanceInfo)) {
					evaluator.evaluateRoot(instance, property, Optional.empty());
				}

				// recursively update dependents
				connection.getStatements(instance, USED_BY, null, true).stream().forEach(stmt -> {
					Resource usedBy = (Resource) stmt.getObject();
					((InferencerConnection) connection).removeInferredStatement(instance, USED_BY, stmt.getObject(),
							stmt.getContext());
					if (seen.add(usedBy)) {
						toUpdate.add(usedBy);
						propertyCache.invalidate(usedBy);

						ResourceInfo usedByInfo = modelAccess.getResourceInfo(instance);
						Set<IReference> properties = modelAccess.getPropertiesWithConstraintsOfResource(usedByInfo);
						for (Resource usedByCtx : instanceInfo.contexts) {
							for (IReference property : properties) {
								((InferencerConnection) connection).removeInferredStatement(usedBy,
										modelAccess.mapProperty(property), null, usedByCtx);
							}
						}
					}
				});
			}
		}
	}

	public void doFullInferencing(SailConnection connection) {
		// clear cache completely
		propertyCache.invalidateAll();

		// remove all usedBy statements
		((InferencerConnection) connection).removeInferredStatement(null, USED_BY, null);

		SimpleDataset dataset = new SimpleDataset();
		BindingSet bindingSet = new ListBindingSet(List.of(), List.of());

		try (CloseableIteration<? extends BindingSet, QueryEvaluationException> bindingsIter = connection
				.evaluate(targetsQuery.getTupleExpr(), dataset, bindingSet, false)) {
			while (bindingsIter.hasNext()) {
				BindingSet bindings = bindingsIter.next();
				Resource instance = (Resource) bindings.getValue("instance");
				Resource property = (Resource) bindings.getValue("property");
				Resource targetGraph = (Resource) bindings.getValue("targetGraph");

				activeDataset = getDataset(targetGraph, connection);
				Rdf4jEvaluator evaluator = evaluators.computeIfAbsent(targetGraph, graph -> {
					return new Rdf4jEvaluator(modelAccess, propertyCache, cacheManager);
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

	public boolean getIncrementalInference() {
		return incrementalInference;
	}

	public void setIncrementalInference(boolean incrementalInference) {
		this.incrementalInference = incrementalInference;
	}

	private DatasetInfo getDataset(Resource context, SailConnection connection) {
		if (context == null) {
			return EMPTY_DATASET;
		}
		DatasetInfo datasetInfo = datasetCache.getIfPresent(context);
		if (datasetInfo == null) {
			List<Resource> contexts = new ArrayList<>();

			Set<Resource> seen = new HashSet<>();
			Queue<Resource> queue = new LinkedList<>();
			queue.add(context);
			while (!queue.isEmpty()) {
				Resource currentContext = queue.remove();
				contexts.add(currentContext);
				connection.getStatements(currentContext, OWL.IMPORTS, null, false, currentContext).stream()
						.filter(stmt -> stmt.getObject() instanceof IRI)
						.forEach(stmt -> {
							Resource imported = (Resource) stmt.getObject();
							if (seen.add(imported)) {
								queue.add(imported);
							}
						});
			}
			datasetInfo.context = contexts.toArray(new Resource[contexts.size()]);
			for (Resource ctx : contexts) {
				datasetInfo.dataset.addDefaultGraph((IRI) ctx);
				datasetInfo.dataset.addNamedGraph((IRI) ctx);
			}
			datasetCache.put(context, datasetInfo);
		}
		return datasetInfo;
	}

	private static class DatasetInfo {
		Resource[] context = null;
		SimpleDataset dataset = new SimpleDataset();
	}
}