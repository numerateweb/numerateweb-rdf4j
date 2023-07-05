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