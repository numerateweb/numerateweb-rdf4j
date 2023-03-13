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

import org.eclipse.rdf4j.common.transaction.IsolationLevel;
import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.DynamicModelFactory;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.SailConnectionListener;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.UnknownSailTransactionStateException;
import org.eclipse.rdf4j.sail.inferencer.InferencerConnection;
import org.eclipse.rdf4j.sail.inferencer.InferencerConnectionWrapper;
import org.eclipse.rdf4j.sail.model.SailModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NumerateWebInferencerConnection extends InferencerConnectionWrapper
		implements SailConnectionListener {

	/*-----------*
	 * Constants *
	 *-----------*/

	static private final Logger logger = LoggerFactory.getLogger(NumerateWebInferencerConnection.class);

	/*-----------*
	 * Variables *
	 *-----------*/

	private final NumerateWebInferencer sail;

	/*--------------*
	 * Constructors *
	 *--------------*/

	public NumerateWebInferencerConnection(NumerateWebInferencer sail, InferencerConnection con) {
		super(con);
		this.sail = sail;
		con.addConnectionListener(this);
	}

	/*---------*
	 * Methods *
	 *---------*/

	// Called by base sail
	@Override
	public void statementAdded(Statement st) {
	}

	// Called by base sail
	@Override
	public void statementRemoved(Statement st) {
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
	}

	@Override
	public void rollback() throws SailException {
		super.rollback();
	}

	protected void doInferencing() throws SailException {
		sail.doInitialInferencing(this);
	}
}