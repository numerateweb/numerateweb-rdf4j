package org.numerateweb.rdf4j;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.enilink.komma.core.URI;
import org.numerateweb.math.ns.INamespaces;

class SimpleNamespaces implements INamespaces {
	final INamespaces base;
	BiMap<String, URI> mappings = HashBiMap.create();

	public SimpleNamespaces(INamespaces base) {
		this.base = base;
	}

	public void set(String prefix, URI namespace) {
		mappings.put(prefix, namespace);
	}

	@Override
	public URI getNamespace(String prefix) {
		return mappings.get(prefix);
	}

	@Override
	public String getPrefix(URI namespace) {
		return mappings.inverse().get(namespace);
	}
}