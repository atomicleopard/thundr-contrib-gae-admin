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

import static com.atomicleopard.expressive.Expressive.map;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;

import java.util.Collection;
import java.util.List;

import com.google.appengine.api.datastore.DatastoreNeedIndexException;
import com.google.common.collect.ImmutableMap;
import com.threewks.thundr.http.StatusCode;
import com.threewks.thundr.view.handlebars.HandlebarsView;
import com.threewks.thundr.view.json.JsonView;

public class DatastoreAdminController {

	private final GaeDatastoreService service;

	public DatastoreAdminController(GaeDatastoreService service) {
		this.service = service;
	}

	public HandlebarsView view() {
		return new HandlebarsView("/datastore.hbs", ImmutableMap.<String, Object>of("baseUrl", GaeAdminModule.baseUrl()));
	}

	public JsonView listKinds() {
		final Collection<Kind> kinds = service.listKinds(true);
		Collection<KindDto> dtos = transform(kinds, KindDto.ToEntityDto);
		return new JsonView(newArrayList(dtos));
	}

	public JsonView getKind(String kind) {
		final Kind k = service.getKind(kind, true);
		if (k == null) {
			return new JsonView("NotFound").withStatusCode(StatusCode.NotFound);
		}

		KindDto dto = KindDto.ToEntityDto.apply(k);
		return new JsonView(dto);
	}

	public JsonView query(String kind, List<QueryOperation> query, Integer limit, Integer offset) {
		try {
			List<?> results = service.listEntities(kind, query, limit, offset);
			return new JsonView(results);
		}
		catch (DatastoreNeedIndexException e) {
			return new JsonView(map("reason", "needindex")).withStatusCode(StatusCode.BadRequest);
		}
	}

	public JsonView regenerateIndex(String kind) {
		int count = service.regenerateIndex(kind);
		return new JsonView(count);
	}
}