package com.threewks.thundr.gae.admin;

import static com.atomicleopard.expressive.Expressive.map;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jodd.typeconverter.TypeConversionException;
import jodd.util.ReflectUtil;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;

import com.atomicleopard.expressive.EList;
import com.atomicleopard.expressive.Expressive;
import com.google.appengine.api.datastore.DatastoreNeedIndexException;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entities;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.QueryResultIterable;
import com.google.gson.GsonBuilder;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Ignore;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.impl.EntityMetadata;
import com.threewks.thundr.exception.BaseException;
import com.threewks.thundr.http.StatusCode;
import com.threewks.thundr.json.GsonSupport;
import com.threewks.thundr.logger.Logger;
import com.threewks.thundr.view.handlebars.HandlebarsView;
import com.threewks.thundr.view.json.JsonView;

public class GaeAdminDatastoreController {
	private DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
	private ObjectifyFactory objectifyFactory = ObjectifyService.factory();
	// private Cache<String, Map<String, List<String>>> kindsAndIndexedFields = new TimedCache<String, Map<String, List<String>>>(Minutes.minutes(5).toPeriod().getMillis());
	private Map<String, Class<?>> kinds = new LinkedHashMap<>();
	private ConcurrentHashMap<String, ObjectifyKind> entities = new ConcurrentHashMap<>();
	private Map<String, Map<ObjectifyField, List<Object>>> possibleValues = new ConcurrentHashMap<>();
	private GsonBuilder gsonBuilder;

	public GaeAdminDatastoreController() {
		this(GsonSupport.createBasicGsonBuilder());
	}

	public GaeAdminDatastoreController(GsonBuilder gsonBuilder) {
		this.gsonBuilder = gsonBuilder;
	}

	public HandlebarsView view() {
		return new HandlebarsView("/hbs/gae/admin/datastore.hbs");
	}

	public JsonView kinds() {
		Map<String, Class<?>> cachedKinds = getCachedKinds();
		Map<String, String> kindAndClasses = new LinkedHashMap<>();
		for (Map.Entry<String, Class<?>> entry : cachedKinds.entrySet()) {
			kindAndClasses.put(entry.getKey(), entry.getValue().getName());
		}
		return new JsonView(kindAndClasses);
	}

	public JsonView kind(String kind) {
		ObjectifyKind objectifyKind = getCachedKind(kind);
		return new JsonView(objectifyKind);
	}

	public JsonView query(String kind, List<QueryOperation> query, Integer limit, Integer offset) {
		limit = clamp(limit, 0, Integer.MAX_VALUE, 100);
		offset = clamp(offset, 0, Integer.MAX_VALUE, 0);

		Map<String, Class<?>> kinds = getCachedKinds();
		Class<?> type = kinds.get(kind);
		Logger.info("Query: %s", query);
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

		try {
			QueryResultIterable<?> iterable = ofyQuery.iterable();
			EList<?> results = Expressive.list(iterable);
			return new JsonView(results);
		} catch (DatastoreNeedIndexException e) {
			return new JsonView(map("reason", "needindex")).withStatusCode(StatusCode.BadRequest);
		}
	}

	private Map<String, Class<?>> getCachedKinds() {
		if (Expressive.isEmpty(this.kinds)) {
			synchronized (this) {
				this.kinds = queryEntities();
			}
		}
		return this.kinds;
	}

	private ObjectifyKind getCachedKind(String kind) {
		Map<String, Class<?>> cachedKinds = getCachedKinds();
		ObjectifyKind objectifyKind = this.entities.get(kind);
		if (objectifyKind == null) {
			Class<?> entityClass = cachedKinds.get(kind);
			Field[] fieldArr = ReflectUtil.getSupportedFields(entityClass);

			List<ObjectifyField> fields = new ArrayList<>();
			for (Field field : fieldArr) {
				String name = field.getName();
				Class<?> type = ClassUtils.primitiveToWrapper(field.getType());
				boolean isIndexed = field.isAnnotationPresent(Index.class) || field.isAnnotationPresent(Id.class);
				boolean isIgnored = field.isAnnotationPresent(Ignore.class);
				if (!isIgnored) {
					List<Object> options = checkForOptions(type);
					fields.add(new ObjectifyField(name, type.getName(), isIndexed, options));
				}
			}
			objectifyKind = new ObjectifyKind(kind, entityClass.getName(), fields);
			objectifyKind.possibleValues = getPossibleValues(kind, objectifyKind);
			this.entities.putIfAbsent(kind, objectifyKind);

		}

		return objectifyKind;
	}

	private Map<String, List<Object>> getPossibleValues(String kind, ObjectifyKind objectifyKind) {
		Map<String, List<Object>> existingFieldValues = new LinkedHashMap<>();

		Map<String, List<String>> indexData = getIndexData(kind);
		Map<String, List<Class<?>>> typedIndexData = typeIndexData(indexData);

		for (ObjectifyField field : objectifyKind.fields) {
			if (field.indexed) {
				Query query = new Query(kind).setDistinct(true);
				Class<?> type = Expressive.isEmpty(typedIndexData.get(field.name)) ? String.class : typedIndexData.get(field.name).get(0);
				query.addProjection(new PropertyProjection(field.name, type));
				List<Entity> results = datastoreService.prepare(query).asList(FetchOptions.Builder.withLimit(1000));
				List<Object> values = new ArrayList<>(results.size());
				for (Entity entity : results) {
					values.add(entity.getProperty(field.name));
				}
				existingFieldValues.put(field.name, values);
			}

		}

		return existingFieldValues;
	}

	private Map<String, List<Class<?>>> typeIndexData(Map<String, List<String>> indexData) {
		Map<String, List<Class<?>>> results = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> entry : indexData.entrySet()) {
			List<Class<?>> types = new ArrayList<>();
			for (String datastoreType : entry.getValue()) {
				Class<?> type = LowLevelTypeMap.get(datastoreType);
				if (type != null) {
					types.add(type);
				}
			}
			results.put(entry.getKey(), types);
		}
		return results;
	}

	private List<Object> checkForOptions(Class<?> type) {
		if (type.isEnum()) {
			Object[] enumConstants = type.getEnumConstants();
			return Arrays.asList(enumConstants);
		}
		if (Boolean.class == type) {
			return Arrays.<Object> asList(true, false, null);
		}
		if (boolean.class == type) {
			return Arrays.<Object> asList(true, false);
		}
		return Collections.emptyList();
	}

	private Map<String, Class<?>> queryEntities() {
		Query q = new Query(Entities.KIND_METADATA_KIND).setKeysOnly();

		Map<String, Class<?>> result = new LinkedHashMap<>();
		for (Entity entity : datastoreService.prepare(q).asIterable()) {
			String kind = entity.getKey().getName();

			EntityMetadata<Object> metadata = objectifyFactory.getMetadata(kind);
			if (metadata != null) {
				Class<Object> entityClass = metadata.getEntityClass();
				result.put(kind, entityClass);
			}
		}
		return result;
	}

	private Object parseValue(String kind, String fieldName, String value) {
		if (value == null) {
			return null;
		}
		ObjectifyKind cachedKind = getCachedKind(kind);
		ObjectifyField field = cachedKind.get(fieldName);
		try {
			Class<?> clazzType = Class.forName(field.type);
			if (value.contains(":") && !value.contains("{")) {
				// a special case for dates, but generally anything that contains a colon and not quotes that isn't an object
				value = "\"" + value + "\"";
			}
			return gsonBuilder.create().fromJson(value, clazzType);
		} catch (TypeConversionException e) {
			// ignore
		} catch (ClassNotFoundException e1) {
			// try basic types
			throw new BaseException("Field type '%s' is unknown", field.type);
		}
		return null;
	}

	private Map<String, List<String>> getIndexData(String kind) {
		Query q = new Query(Entities.PROPERTY_METADATA_KIND).setAncestor(Entities.createKindKey(kind));
		Map<String, List<String>> results = new LinkedHashMap<>();
		for (Entity entity : datastoreService.prepare(q).asIterable()) {
			Collection<String> types = (Collection<String>) entity.getProperty("property_representation");
			results.put(entity.getKey().getName(), Expressive.list(types));
		}
		return results;
	}

	public static class ObjectifyKind {
		public String name;
		public String className;
		public List<ObjectifyField> fields;
		public Map<String, List<Object>> possibleValues;

		public ObjectifyKind(String name, String className, List<ObjectifyField> fields) {
			super();
			this.name = name;
			this.className = className;
			this.fields = fields;
		}

		public ObjectifyField get(String fieldName) {
			for (ObjectifyField field : fields) {
				if (field.name.equals(fieldName)) {
					return field;
				}
			}
			return null;
		}
	}

	public static class QueryOperation {
		public String field;
		public String operation;
		public String value;
	}

	public static class ObjectifyField {
		public String name;
		public String type;
		public boolean indexed;
		public List<Object> options;
		public List<Object> projectedOptions;

		public ObjectifyField(String name, String type, boolean indexed, List<Object> options) {
			super();
			this.name = name;
			this.type = type;
			this.indexed = indexed;
			this.options = options;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (indexed ? 1231 : 1237);
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((type == null) ? 0 : type.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ObjectifyField other = (ObjectifyField) obj;
			if (indexed != other.indexed)
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (type == null) {
				if (other.type != null)
					return false;
			} else if (!type.equals(other.type))
				return false;
			return true;
		}
	}

	private Integer clamp(Integer value, int min, int max, int defaultValue) {
		value = value == null ? defaultValue : value;
		return Math.min(Math.max(0, value), max);
	}

	private Map<String, Class<?>> LowLevelTypeMap = Expressive.map(
			"INT64", java.lang.Long.class,
			"DOUBLE", java.lang.Double.class,
			"BOOLEAN", java.lang.Boolean.class,
			"STRING", java.lang.String.class,
			"POINT", com.google.appengine.api.datastore.GeoPt.class,
			"REFERENCE", com.google.appengine.api.datastore.Key.class);

}
