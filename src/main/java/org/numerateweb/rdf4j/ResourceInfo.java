package org.numerateweb.rdf4j;

import org.eclipse.rdf4j.model.Resource;

import java.util.ArrayList;
import java.util.List;

class ResourceInfo {
	final List<Resource> types = new ArrayList<>(1);
	final List<Resource> contexts = new ArrayList<>(1);
}