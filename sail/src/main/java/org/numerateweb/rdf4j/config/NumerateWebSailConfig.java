package org.numerateweb.rdf4j.config;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.ModelException;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.sail.base.config.BaseSailConfig;
import org.eclipse.rdf4j.sail.config.SailConfigException;

public class NumerateWebSailConfig extends BaseSailConfig {
	private boolean incrementalInference = false;

	public NumerateWebSailConfig() {
		super(NumerateWebSailFactory.SAIL_TYPE);
	}

	public NumerateWebSailConfig(boolean incrementalInference) {
		this();
		setIncrementalInference(incrementalInference);
	}

	public boolean getIncrementalInference() {
		return incrementalInference;
	}

	public NumerateWebSailConfig setIncrementalInference(boolean incrementalInference) {
		this.incrementalInference = incrementalInference;
		return this;
	}

	@Override
	public Resource export(Model m) {
		Resource implNode = super.export(m);
		ValueFactory vf = SimpleValueFactory.getInstance();

		m.setNamespace("ns", NumerateWebSailSchema.NAMESPACE);
		if (!incrementalInference) {
			m.add(implNode, NumerateWebSailSchema.INCREMENTAL_INFERENCE, vf.createLiteral(false));
		}
		return implNode;
	}

	@Override
	public void parse(Model m, Resource implNode) throws SailConfigException {
		super.parse(m, implNode);

		try {
			Models.objectLiteral(m.getStatements(implNode, NumerateWebSailSchema.INCREMENTAL_INFERENCE, null)).ifPresent(lit -> {
				try {
					setIncrementalInference(lit.booleanValue());
				} catch (IllegalArgumentException e) {
					throw new SailConfigException(
							"Boolean value required for " + NumerateWebSailSchema.INCREMENTAL_INFERENCE + " property, found " + lit);
				}
			});
		} catch (ModelException e) {
			throw new SailConfigException(e.getMessage(), e);
		}
	}
}