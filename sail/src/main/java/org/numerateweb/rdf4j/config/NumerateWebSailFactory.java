package org.numerateweb.rdf4j.config;

import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.config.SailConfigException;
import org.eclipse.rdf4j.sail.config.SailFactory;
import org.eclipse.rdf4j.sail.config.SailImplConfig;
import org.numerateweb.rdf4j.NumerateWebSail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SailFactory} that creates {@link NumerateWebSail}s based on RDF configuration data.
 */
public class NumerateWebSailFactory implements SailFactory {

	/**
	 * The type of repositories that are created by this factory.
	 *
	 * @see SailFactory#getSailType()
	 */
	public static final String SAIL_TYPE = "rdf4j:NumerateWebSail";
	private static final Logger logger = LoggerFactory.getLogger(NumerateWebSailFactory.class);

	/**
	 * Returns the Sail's type: <tt>rdf4j:NumerateWebSail</tt>.
	 */
	@Override
	public String getSailType() {
		return SAIL_TYPE;
	}

	@Override
	public SailImplConfig getConfig() {
		return new NumerateWebSailConfig();
	}

	@Override
	public Sail getSail(SailImplConfig config) throws SailConfigException {
		if (!SAIL_TYPE.equals(config.getType())) {
			throw new SailConfigException("Invalid Sail type: " + config.getType());
		}

		NumerateWebSail sail = new NumerateWebSail();

		if (config instanceof NumerateWebSailConfig) {
			sail.setIncrementalInference(((NumerateWebSailConfig) config).getIncrementalInference());
		} else {
			logger.warn("Config is instance of {} is not NumerateWebSailConfig.", config.getClass().getName());
		}
		return sail;
	}
}