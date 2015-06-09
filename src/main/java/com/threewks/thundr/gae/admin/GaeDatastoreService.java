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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.atomicleopard.expressive.Expressive;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entities;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultIterable;
import com.google.appengine.api.search.Index;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.gson.GsonBuilder;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.impl.EntityMetadata;
import com.threewks.thundr.exception.BaseException;
import com.threewks.thundr.gae.objectify.repository.AbstractRepository;
import com.threewks.thundr.json.GsonSupport;
import com.threewks.thundr.logger.Logger;
import com.threewks.thundr.search.gae.IdGaeSearchService;

import jodd.typeconverter.TypeConversionException;
import jodd.util.ReflectUtil;

import org.apache.commons.lang3.StringUtils;

public class GaeDatastoreService {

	private DatastoreService datastoreService;
	private ObjectifyFactory objectifyFactory;
	private RepositoryRegistry repositoryRegistry;
	private GsonBuilder gsonBuilder;

	GaeDatastoreService(DatastoreService datastoreService, ObjectifyFactory objectifyFactory) {
		this.datastoreService = datastoreService;
		this.objectifyFactory = objectifyFactory;
	}

	public GaeDatastoreService(RepositoryRegistry repositoryRegistry) {
		this(DatastoreServiceFactory.getDatastoreService(), ObjectifyService.factory());
		this.repositoryRegistry = repositoryRegistry;
		this.gsonBuilder = GsonSupport.createBasicGsonBuilder();
	}

	public Kind getKind(String kind, boolean includePossibleValues) {
		List<Property> properties;
		EntityMetadata<?> metadata = objectifyFactory.getMetadata(kind);

		if (metadata == null) {
			return null;
		}

		if (includePossibleValues) {
			Map<String, Collection<Class<?>>> indexData = getIndexData(kind);
			properties = getProperties(kind, metadata.getEntityClass(), indexData);
		}
		else {
			properties = getProperties(metadata.getEntityClass());
		}

		AbstractRepository<?, ?> repo = repositoryRegistry.get(metadata.getEntityClass());
		Index index = getSearchIndex(repo);

		return new Kind(kind, metadata, properties, index);
	}

	public Collection<Kind> listKinds(boolean includePossibleValues) {
		List<Kind> kinds = new ArrayList<>();
		Query q = new Query(Entities.KIND_METADATA_KIND).setKeysOnly();
		PreparedQuery pq = datastoreService.prepare(q);
		for (Entity entity : pq.asIterable()) {
			Kind k = getKind(entity.getKey().getName(), includePossibleValues);
			if (k != null) {
				kinds.add(k);
			}
		}
		return kinds;
	}

	public List<?> listEntities(String kind, List<QueryOperation> query, Integer limit, Integer offset) {
		limit = clamp(limit, 0, Integer.MAX_VALUE, 100);
		offset = clamp(offset, 0, Integer.MAX_VALUE, 0);

		Class<?> type = objectifyFactory.getMetadata(kind).getEntityClass();

		com.googlecode.objectify.cmd.Query<?> ofyQuery = ObjectifyService.ofy().load().type(type);
		if (Expressive.isNotEmpty(query)) {
			for (QueryOperation element : query) {
				if (StringUtils.isNotBlank(element.field)) {
					Object value = parseValue(kind, element.field, element.value);
					String queryComponent = element.field + (StringUtils.isNotBlank(element.operation) ? " " + element.operation : "");
					ofyQuery = ofyQuery.filter(queryComponent, value);
				}
			}
		}
		ofyQuery.offset(offset);
		ofyQuery.limit(limit);

		QueryResultIterable<?> iterable = ofyQuery.iterable();
		return Expressive.list(iterable);
	}

	public int regenerateIndex(String kind) {
		Class<?> entityClass = objectifyFactory.getMetadata(kind).getEntityClass();
		AbstractRepository<?, ?> repo = repositoryRegistry.get(entityClass);
		IdGaeSearchService<?, ?> searchService = getSearchService(repo);
		searchService.removeAll();

		int offset = 0;
		int limit = 200;
		int count = 0;

		try {
			Method indexer = getIndexer(repo);

			if (indexer != null) {
				while (true) {
					List<?> results = ObjectifyService.ofy().load().type(entityClass).offset(offset).limit(limit).list();
					count += results.size();
					indexer.invoke(repo, results);

					if (results.size() < limit) {
						break;
					}
				}
			}
		}
		catch (Exception e) {
			Logger.warn(e.getMessage());
			e.printStackTrace(System.out);
		}

		return count;
	}

	private Method getIndexer(AbstractRepository<?, ?> repo) throws IllegalAccessException, InvocationTargetException {
		Method[] ms = ReflectUtil.getAccessibleMethods(repo.getClass());

		for (Method m : ms) {
			if (StringUtils.equals(m.getName(), "index")) {
				if (m.getParameterTypes()[0] == List.class) {
					ReflectUtil.forceAccess(m);
					return m;
				}
			}
		}

		return null;
	}

	private Object parseValue(String kind, String fieldName, String value) {
		if (value == null) {
			return null;
		}

		Class<?> entityClass = objectifyFactory.getMetadata(kind).getEntityClass();
		Field field = getField(entityClass, fieldName);
		String fieldType = field.getType().getName();

		try {
			Class<?> clazzType = Class.forName(fieldType);
			if (value.contains(":") && !value.contains("{")) {
				// a special case for dates, but generally anything that contains a colon and not quotes that isn't an object
				value = "\"" + value + "\"";
			}
			return gsonBuilder.create().fromJson(value, clazzType);
		} catch (TypeConversionException e) {
			// ignore
		} catch (ClassNotFoundException e1) {
			// try basic types
			throw new BaseException("Field type '%s' is unknown", fieldType);
		}
		return null;
	}


	private static Integer clamp(Integer value, int min, int max, int defaultValue) {
		value = value == null ? defaultValue : value;
		return Math.min(Math.max(min, value), max);
	}


	private IdGaeSearchService<?, ?> getSearchService(AbstractRepository<?, ?> repo) {
		return (IdGaeSearchService<?, ?>) getField(repo, "searchService");
	}

	private Index getSearchIndex(AbstractRepository<?, ?> repo) {
		return (Index) getField(getSearchService(repo), "index");
	}

	private Field getField(Class<?> c, String fieldName) {
		Field[] fields = ReflectUtil.getAccessibleFields(c);
		for (Field f: fields) {
			if (StringUtils.equals(f.getName(), fieldName)) {
				return f;
			}
		}
		return null;
	}

	private Object getField(Object obj, String fieldName) {
		try {
			Field f = getField(obj.getClass(), fieldName);
			ReflectUtil.forceAccess(f);
			return f != null ? f.get(obj) : null;
		}
		catch (Exception e) {
			return null;
		}
	}

	private List<Property> getProperties(String kind, Class<?> entityClass, Map<String, Collection<Class<?>>> indexData) {
		List<Property> properties = new ArrayList<>();
		Field[] fields = ReflectUtil.getAccessibleFields(entityClass);

		for (Field f : fields) {
			Property p = new Property(f);
			Collection<Class<?>> types = indexData.get(f.getName());
			List<Object> possibleValues = getPossibleValues(kind, f, types);
			p.addPossibleValues(possibleValues);
			properties.add(p);
		}

		return properties;
	}

	private List<Property> getProperties(Class<?> entityClass) {
		List<Property> properties = new ArrayList<>();
		Field[] fields = ReflectUtil.getAccessibleFields(entityClass);

		for (Field f : fields) {
			properties.add(new Property(f));
		}

		return properties;
	}

	private static final Function<String, Class<?>> ToClass = new Function<String, Class<?>>() {
		@Override
		public Class<?> apply(String input) {
			return LowLevelTypeMap.get(input);
		}
	};

	@SuppressWarnings("unchecked")
	private Map<String, Collection<Class<?>>> getIndexData(String kind) {
		Query q = new Query(Entities.PROPERTY_METADATA_KIND).setAncestor(Entities.createKindKey(kind));
		Map<String, Collection<Class<?>>> results = new LinkedHashMap<>();
		for (Entity entity : datastoreService.prepare(q).asIterable()) {
			Collection<String> propertyRepresentations = (Collection<String>) entity.getProperty("property_representation");
			Collection<Class<?>> types = Collections2.transform(propertyRepresentations, ToClass);
			results.put(entity.getKey().getName(), types);
		}

		return results;
	}

	private static Map<String, Class<?>> LowLevelTypeMap = Expressive.map(
			"INT64", Long.class,
			"DOUBLE", Double.class,
			"BOOLEAN", Boolean.class,
			"STRING", String.class,
			"POINT", com.google.appengine.api.datastore.GeoPt.class,
			"REFERENCE", com.google.appengine.api.datastore.Key.class);


	private List<Object> getPossibleValues(String kind, Field field, Collection<Class<?>> types) {
		List<Object> existingValues = new ArrayList<>();

		if (isIndexed(field)) {
			Query query = new Query(kind).setDistinct(true);
			String fieldName = field.getName();
			Class<?> type = types != null ? Iterables.getFirst(types, String.class) : String.class;
			query.addProjection(new PropertyProjection(fieldName, type));
			List<Entity> results = datastoreService.prepare(query).asList(FetchOptions.Builder.withLimit(1000));
			List<Object> values = new ArrayList<>(results.size());

			for (Entity entity : results) {
				existingValues.add(entity.getProperty(fieldName));
			}

			existingValues.add(values);
		}

		return existingValues;
	}

	private static boolean isIndexed(Field field) {
		return field.isAnnotationPresent(com.googlecode.objectify.annotation.Index.class) || field.isAnnotationPresent(Id.class);
	}
}
