/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.numerateweb.rdf4j;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import net.enilink.komma.core.KommaModule;
import net.enilink.komma.em.ManagerCompositionModule;
import net.enilink.komma.literals.LiteralConverter;
import net.enilink.komma.rdf4j.RDF4JValueConverter;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.transaction.IsolationLevel;
import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
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
import org.numerateweb.math.reasoner.GuavaCacheFactory;
import org.numerateweb.math.util.SparqlUtils;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
			+ "SELECT ?instance ?property WHERE { " //
			+ "{ select distinct ?instance { { select ?invalidated { ?invalidated <math:invalid> true }} ?invalidated <math:usedBy>* ?instance . }}"
			+ "?instance a [ rdfs:subClassOf* ?c ] ." //
			+ "?c mathrl:constraint ?constraint . ?constraint mathrl:onProperty ?property ." //
			+ "}";
	protected static final ParsedQuery invalidDTargetsQuery = QueryParserUtil.parseQuery(QueryLanguage.SPARQL, INVALID_TARGETS_QUERY,
			null);
	private static final IsolationLevels READ_COMMITTED = IsolationLevels.READ_COMMITTED;

	protected Injector injector;
	protected RDF4JValueConverter valueConverter;
	protected LiteralConverter literalConverter;
	protected Map<Resource, Rdf4jEvaluator> evaluators = new HashMap<>();
	protected boolean initialInferencingDone = false;

	protected ThreadLocal<SailConnection> connection = new ThreadLocal<>();
	volatile boolean inferencing = false;

	IRI USED_BY;
	IRI INVALID;

	/*--------------*
	 * Constructors *
	 *--------------*/

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
		injector = Guice.createInjector(new ManagerCompositionModule(module), new AbstractModule() {
			@Override
			protected void configure() {
				bind(Locale.class).toInstance(Locale.getDefault());
				bind(ValueFactory.class).toInstance(getValueFactory());
			}
		});
		valueConverter = injector.getInstance(RDF4JValueConverter.class);
		literalConverter = injector.getInstance(LiteralConverter.class);
		USED_BY = getValueFactory().createIRI("math:usedBy");
		INVALID = getValueFactory().createIRI("math:invalid");
	}

	@Override
	public NotifyingSailConnection getConnection() throws SailException {
		return new NumerateWebInferencerConnection(this, (InferencerConnection) super.getConnection());
	}

	void update(SailConnection connection, Statement stmt, boolean added) {
		if (inferencing) {
			return;
		}
		try {
			inferencing = true;
			((InferencerConnection) connection).addInferredStatement(stmt.getSubject(),
					INVALID, getValueFactory().createLiteral(true));
		} finally {
			inferencing = false;
		}
		/*if (!evaluators.isEmpty()) {
			Rdf4jEvaluator evaluator = evaluators.get(stmt.getContext());
			if (evaluator != null) {
				evaluator.invalidate(stmt.getSubject(), valueConverter.fromRdf4j(stmt.getPredicate()));
			}
		}*/
	}

	public void reevaluate(SailConnection connection) {
		try {
			inferencing = true;

			this.connection.set(connection);
			if (!initialInferencingDone) {
				doFullInferencing(connection);
				initialInferencingDone = true;
			} else {
				doIncrementalInferencing(connection);
				// evaluators.values().forEach(e -> e.reevaluate());
			}
		} finally {
			inferencing = false;
		}
	}

	public void doIncrementalInferencing(SailConnection connection) {
		SimpleDataset dataset = new SimpleDataset();
		BindingSet bindingSet = new ListBindingSet(List.of(), List.of());
		Set<BindingSet> invalidTargets = connection
				.evaluate(invalidDTargetsQuery.getTupleExpr(), dataset, bindingSet, true)
				.stream().collect(Collectors.toSet());

		for (BindingSet bindings : invalidTargets) {
			Resource instance = (Resource) bindings.getValue("instance");
			Resource property = (Resource) bindings.getValue("property");
			Resource targetGraph = (Resource) bindings.getValue("targetGraph");
			((InferencerConnection) connection).removeInferredStatement(instance, USED_BY, null);
			Rdf4jEvaluator evaluator = evaluators.get(targetGraph);
			if (evaluator != null) {
				evaluator.invalidateCache(instance, valueConverter.fromRdf4j(property));
			}
			((InferencerConnection) connection).removeInferredStatement(instance, (IRI) property, null);
		}

		for (BindingSet bindings : invalidTargets) {
			Resource instance = (Resource) bindings.getValue("instance");
			Resource property = (Resource) bindings.getValue("property");
			Resource targetGraph = (Resource) bindings.getValue("targetGraph");
			Rdf4jEvaluator evaluator = evaluators.computeIfAbsent(targetGraph, graph -> {
				CacheManager cacheManager = new CacheManager(new GuavaCacheFactory());
				// TODO use thread-local connection or something like that
				Supplier<SailConnection> connSupplier = () -> this.connection.get();
				Supplier<Dataset> datasetSupplier = () -> dataset;
				Rdf4jModelAccess modelAccess = new Rdf4jModelAccess(literalConverter,
						getValueFactory(), connSupplier, datasetSupplier, cacheManager);
				return new Rdf4jEvaluator(modelAccess, cacheManager);
			});
			evaluator.evaluateRoot(instance, valueConverter.fromRdf4j(property), Optional.empty());
		}

		((InferencerConnection) connection).removeInferredStatement(null,
				INVALID, getValueFactory().createLiteral(true));

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
					CacheManager cacheManager = new CacheManager(new GuavaCacheFactory());
					// TODO use thread-local connection or something like that
					Supplier<SailConnection> connSupplier = () -> this.connection.get();
					Supplier<Dataset> datasetSupplier = () -> dataset;
					Rdf4jModelAccess modelAccess = new Rdf4jModelAccess(literalConverter,
							getValueFactory(), connSupplier, datasetSupplier, cacheManager);
					return new Rdf4jEvaluator(modelAccess, cacheManager);
				});
				evaluator.evaluateRoot(instance, valueConverter.fromRdf4j(property), Optional.empty());
			}
		}
	}

	/*---------*
	 * Methods *
	 *---------*/

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