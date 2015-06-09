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

import java.util.HashMap;
import java.util.Map;

import com.threewks.thundr.gae.objectify.repository.AbstractRepository;


public class RepositoryRegistry {

	private Map<Class<?>, AbstractRepository<?, ?>> repositories = new HashMap<>();

	public <T> void add(Class<T> entityClass, AbstractRepository<T, ?> repo) {
		this.repositories.put(entityClass, repo);
	}

	@SuppressWarnings("unchecked")
	public <T> AbstractRepository<T, ?> get(Class<T> entityClass) {
		return (AbstractRepository<T, ?>) this.repositories.get(entityClass);
	}
}
