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

import com.google.common.collect.Streams;
import org.eclipse.rdf4j.common.transaction.IsolationLevel;
import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.sail.SailConnectionListener;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.UnknownSailTransactionStateException;
import org.eclipse.rdf4j.sail.inferencer.InferencerConnection;
import org.eclipse.rdf4j.sail.inferencer.InferencerConnectionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

public class NumerateWebSailConnection extends InferencerConnectionWrapper
		implements SailConnectionListener {

	static private final Logger logger = LoggerFactory.getLogger(NumerateWebSailConnection.class);

	private final NumerateWebSail sail;

	private Map<Resource, List<Resource>> changedResources = new HashMap<>();
	private Model changedStatements = new LinkedHashModel();

	public NumerateWebSailConnection(NumerateWebSail sail, InferencerConnection con) {
		super(con);
		this.sail = sail;
		con.addConnectionListener(this);
	}

	@Override
	public void statementAdded(Statement st) {
		update(st, true);
	}

	@Override
	public void statementRemoved(Statement st) {
		update(st, false);
	}

	void update(Statement stmt, boolean added) {
		if (sail.inferencing) {
			return;
		}
		changedResources.putIfAbsent(stmt.getSubject(), Collections.emptyList());
		if (sail.CONSTRAINT_PROPERTY.equals(stmt.getPredicate()) ||
				RDFS.SUBCLASSOF.equals(stmt.getPredicate()) || sail.ONPROPERTY.equals(stmt.getPredicate())) {
			changedStatements.add(stmt);
		}

		// handle changes of resource types
		if (RDF.TYPE.equals(stmt.getPredicate())) {
			// track removed types
			if (! added && stmt.getObject().isResource()) {
				List<Resource> types = changedResources.get(stmt.getSubject());
				if (types.isEmpty()) {
					types = new ArrayList<>(2);
					types.add((Resource) stmt.getObject());
					changedResources.put(stmt.getSubject(), types);
				} else if (! types.contains(stmt.getObject())) {
					types.add((Resource) stmt.getObject());
				}
			}
			sail.modelAccess.invalidateResourceInfo(stmt.getSubject());
		}
	}

	@Override
	public void flushUpdates() throws SailException {
		super.flushUpdates();
	}

	@Override
	public void begin() throws SailException {
		this.begin(sail.getDefaultIsolationLevel());
	}

	@Override
	public void begin(IsolationLevel level) throws SailException {
		IsolationLevel compatibleLevel = IsolationLevels.getCompatibleIsolationLevel(level,
				sail.getSupportedIsolationLevels());
		if (compatibleLevel == null) {
			throw new UnknownSailTransactionStateException(
					"Isolation level " + level + " not compatible with this Sail");
		}
		super.begin(compatibleLevel);
	}

	@Override
	public void commit() throws SailException {
		doInferencing();
		super.commit();
		changedResources.clear();
		changedStatements.clear();
	}

	@Override
	public void rollback() throws SailException {
		super.rollback();
		changedResources.clear();
		changedStatements.clear();
	}

	private void addAffectedSubClasses(Resource clazz, Set<Resource> seen,
	                                   Map<Resource, List<IRI>> affectedClasses) {
		if (!seen.add(clazz)) {
			return;
		}
		computeAffectedProperties(clazz, affectedClasses);
		Stream.concat(changedStatements.filter(null, RDFS.SUBCLASSOF, clazz).stream(),
				getStatements(null, RDFS.SUBCLASSOF, clazz, false).stream()).forEach(stmt -> {
			addAffectedSubClasses(stmt.getSubject(), seen, affectedClasses);
		});
	}

	List<IRI> computeAffectedProperties(Resource clazz, Map<Resource, List<IRI>> affectedClasses) {
		List<IRI> properties = affectedClasses.get(clazz);
		if (properties != null) {
			return properties;
		}
		List<IRI> propertiesFinal = new ArrayList<>();
		// add properties from own constraints
		Streams.concat(changedStatements.filter(clazz, sail.CONSTRAINT_PROPERTY, null).stream(),
				getStatements(clazz, sail.CONSTRAINT_PROPERTY, null, false).stream()).forEach(stmt -> {
			if (stmt.getObject().isResource()) {
				Resource constraint = (Resource) stmt.getObject();
				Stream.concat(changedStatements.filter(constraint, sail.ONPROPERTY, null).stream(),
								getStatements(constraint, sail.ONPROPERTY, null, false).stream())
						.forEach(cStmt -> {
							if (cStmt.getObject().isIRI()) {
								IRI property = (IRI) cStmt.getObject();
								if (!propertiesFinal.contains(property)) {
									propertiesFinal.add(property);
								}
							}
						});
			}
		});
		// add properties from parent constraints
		Stream.concat(changedStatements.filter(clazz, RDFS.SUBCLASSOF, null).stream(),
				getStatements(clazz, RDFS.SUBCLASSOF, null, false).stream()).flatMap(stmt -> {
			if (stmt.getObject().isResource()) {
				return computeAffectedProperties((Resource) stmt.getObject(), affectedClasses).stream();
			}
			return Collections.<IRI>emptyList().stream();
		}).distinct().forEach(property -> {
			if (!propertiesFinal.contains(property)) {
				propertiesFinal.add(property);
			}
		});
		affectedClasses.put(clazz, propertiesFinal.isEmpty() ? Collections.emptyList() : propertiesFinal);
		return propertiesFinal;
	}

	private Map<Resource, List<IRI>> computeAffectedClasses() {
		Set<Resource> seenSubClasses = new HashSet<>();
		Map<Resource, List<IRI>> affectedClasses = new HashMap<>();
		changedStatements.filter(null, sail.CONSTRAINT_PROPERTY, null).forEach(stmt -> {
			Resource clazz = stmt.getSubject();
			computeAffectedProperties(clazz, affectedClasses);
		});
		changedStatements.filter(null, RDFS.SUBCLASSOF, null).stream().forEach(stmt -> {
			if (! computeAffectedProperties(stmt.getSubject(), affectedClasses).isEmpty()) {
				addAffectedSubClasses(stmt.getSubject(), seenSubClasses, affectedClasses);
			}
		});
		// filter classes that don't have affected properties
		for (Iterator<Map.Entry<Resource, List<IRI>>> it = affectedClasses.entrySet().iterator(); it.hasNext(); ) {
			if (it.next().getValue().isEmpty()) {
				it.remove();
			}
		}
		return affectedClasses;
	}

	protected void doInferencing() throws SailException {
		sail.reevaluate(this, changedResources, computeAffectedClasses());
	}
}