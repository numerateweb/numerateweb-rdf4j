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

import org.eclipse.rdf4j.common.transaction.IsolationLevel;
import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.sail.SailConnectionListener;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.UnknownSailTransactionStateException;
import org.eclipse.rdf4j.sail.inferencer.InferencerConnection;
import org.eclipse.rdf4j.sail.inferencer.InferencerConnectionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class NumerateWebSailConnection extends InferencerConnectionWrapper
		implements SailConnectionListener {

	static private final Logger logger = LoggerFactory.getLogger(NumerateWebSailConnection.class);

	private final NumerateWebSail sail;

	private Set<Resource> changedResources = new HashSet<>();
	private Set<Resource> changedClasses = new HashSet<>();

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
		changedResources.add(stmt.getSubject());
		if (sail.CONSTRAINT_PROPERTY.equals(stmt.getPredicate()) || RDFS.SUBCLASSOF.equals(stmt.getPredicate())) {
			changedClasses.add(stmt.getSubject());
		}

		// handle changes of resource types
		if (RDF.TYPE.equals(stmt.getPredicate())) {
			sail.modelAccess.invalidateType(stmt.getSubject());
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
		changedClasses.clear();
	}

	@Override
	public void rollback() throws SailException {
		super.rollback();
		changedResources.clear();
		changedClasses.clear();
	}

	protected void doInferencing() throws SailException {
		sail.reevaluate(this, changedResources, changedClasses);
	}
}