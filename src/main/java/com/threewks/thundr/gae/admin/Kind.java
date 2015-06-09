package com.threewks.thundr.gae.admin;

import java.util.List;

import com.google.appengine.api.search.Index;
import com.googlecode.objectify.impl.EntityMetadata;

public class Kind {

	private String name;
	private EntityMetadata metadata;
	private Index searchIndex;
	private List<Property> properties;

	public Kind(String name, EntityMetadata metadata, List<Property> properties, Index searchIndex) {
		this.name = name;
		this.metadata = metadata;
		this.properties = properties;
		this.searchIndex = searchIndex;
	}

	public String name() {
		return this.name;
	}

	public EntityMetadata metadata() {
		return metadata;
	}

	public Index index() {
		return searchIndex;
	}

	public List<Property> properties() {
		return properties;
	}
}
