package com.threewks.thundr.gae.admin;

import java.util.HashMap;
import java.util.Map;

import com.threewks.thundr.gae.objectify.repository.AbstractRepository;


public class RepositoryRegistry {

	private Map<Class, AbstractRepository> repositories = new HashMap<>();

	public <T> void add(Class<T> entityClass, AbstractRepository<T, ?> repo) {
		this.repositories.put(entityClass, repo);
	}

	public <T> AbstractRepository<T, ?> get(Class<T> entityClass) {
		return this.repositories.get(entityClass);
	}
}
