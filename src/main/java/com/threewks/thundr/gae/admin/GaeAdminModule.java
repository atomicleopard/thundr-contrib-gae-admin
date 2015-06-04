/*
 * This file is a component of com.threewks.thundr, a software library from 3wks.
 * Read more: http://www.3wks.com.au/com.threewks.thundr
 * Copyright (C) 2013 3wks, <com.threewks.thundr@3wks.com.au>
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
import java.util.Collection;
import java.util.HashSet;

import com.atomicleopard.expressive.collection.Triplets;
import com.threewks.thundr.gae.GaeModule;
import com.threewks.thundr.gae.objectify.repository.AbstractRepository;
import com.threewks.thundr.handlebars.HandlebarsModule;
import com.threewks.thundr.injection.BaseModule;
import com.threewks.thundr.injection.UpdatableInjectionContext;
import com.threewks.thundr.module.DependencyRegistry;
import com.threewks.thundr.route.Router;
import jodd.util.ReflectUtil;
import org.apache.commons.lang3.StringUtils;

public class GaeAdminModule extends BaseModule {

	private static Collection<Class<? extends AbstractRepository>> RepositoryClasses = new HashSet<>();
	private static String BaseUrl = "/admin/datastore";
	private static boolean AutoRegister = true;

	public static <T extends AbstractRepository> void register(Class<T> repoClass) {
		RepositoryClasses.add(repoClass);
	}

	public static void setBaseUrl(String baseUrl) {
		BaseUrl = baseUrl;
	}

	public static String baseUrl() {
		return BaseUrl;
	}

	public static void enableAutoRegister() {
		AutoRegister = true;
	}

	public static void disableAutoRegister() {
		AutoRegister = false;
	}

	@Override
	public void requires(DependencyRegistry dependencyRegistry) {
		dependencyRegistry.addDependency(GaeModule.class);
		dependencyRegistry.addDependency(HandlebarsModule.class);
	}

	@Override
	public void initialise(UpdatableInjectionContext injectionContext) {
		super.initialise(injectionContext);
		injectionContext.inject(RepositoryRegistry.class).as(RepositoryRegistry.class);
	}

	@Override
	public void start(UpdatableInjectionContext injectionContext) {
		super.configure(injectionContext);
		injectionContext.inject(GaeDatastoreService.class).as(GaeDatastoreService.class);

		if (AutoRegister) {
			autoRegister(injectionContext);
		}

		registerRepositories(injectionContext);

		Router router = injectionContext.get(Router.class);
		router.get(BaseUrl, DatastoreAdminController.class, "view", "GaeAdminDatastoreViewer");
		router.get(BaseUrl + "/kinds", DatastoreAdminController.class, "listKinds", "GaeAdminDatastoreKinds");
		router.get(BaseUrl + "/kinds/{kind}", DatastoreAdminController.class, "getKind", "GaeAdminDatastoreKind");
		router.post(BaseUrl + "/kinds/{kind}/query", DatastoreAdminController.class, "query", "GaeAdminDatastoreKindQuery");
		router.get(BaseUrl + "/kinds/{kind}/reindex", DatastoreAdminController.class, "regenerateIndex",  null);
	}

	private void registerRepositories(UpdatableInjectionContext injectionContext) {
		RepositoryRegistry registry = injectionContext.get(RepositoryRegistry.class);

		for (Class<? extends AbstractRepository> repositoryClass : RepositoryClasses) {
			AbstractRepository repo = injectionContext.get(repositoryClass);
			Class entityClass = ReflectUtil.getGenericSupertype(repo.getClass());
			registry.add(entityClass, repo);
		}

		injectionContext.inject(registry).as(RepositoryRegistry.class);
	}

	private void autoRegister(UpdatableInjectionContext injectionContext) {
		Field[] fields = ReflectUtil.getAccessibleFields(injectionContext.getClass());

		for (Field f : fields) {
			if (StringUtils.equals(f.getName(), "types")) {
				ReflectUtil.forceAccess(f);
				try {
					Triplets<Class<?>, String, Class> types = (Triplets<Class<?>, String, Class>) f.get(injectionContext);

					for (Class c : types.values()) {
						if (AbstractRepository.class.isAssignableFrom(c)) {
							GaeAdminModule.register(c);
						}
					}
				} catch (IllegalAccessException e) {
					e.printStackTrace(System.out);
				}
			}
		}
	}
}