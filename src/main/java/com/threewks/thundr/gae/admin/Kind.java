/*
 * This file is a component of the thundr-contrib-gae-admin library,
 * a software library from Atomic Leopard.
 *
 * Copyright (C) 2015 Atomic Leopard, <admin@atomicleopard.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.threewks.thundr.gae.admin;

import java.util.List;

import com.google.appengine.api.search.Index;
import com.googlecode.objectify.impl.EntityMetadata;

public class Kind {

	private String name;
	private EntityMetadata<?> metadata;
	private Index searchIndex;
	private List<Property> properties;

	public Kind(String name, EntityMetadata<?> metadata, List<Property> properties, Index searchIndex) {
		this.name = name;
		this.metadata = metadata;
		this.properties = properties;
		this.searchIndex = searchIndex;
	}

	public String name() {
		return this.name;
	}

	public EntityMetadata<?> metadata() {
		return metadata;
	}

	public Index index() {
		return searchIndex;
	}

	public List<Property> properties() {
		return properties;
	}
}
