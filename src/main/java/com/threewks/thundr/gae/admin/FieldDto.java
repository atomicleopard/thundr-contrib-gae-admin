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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Function;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import org.apache.commons.lang3.ClassUtils;

public class FieldDto {
	public String name;
	public String type;
	public boolean indexed;
	public List<Object> options;
	public List<Object> projectedOptions;

	public FieldDto(String name, String type, boolean indexed, List<Object> options) {
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
		FieldDto other = (FieldDto) obj;
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

	public static Function<Field, FieldDto> ToFieldDto = new Function<Field, FieldDto>() {
		@Override
		public FieldDto apply(Field field) {
			Class<?> type = ClassUtils.primitiveToWrapper(field.getType());
			boolean isIndexed = field.isAnnotationPresent(Index.class) || field.isAnnotationPresent(Id.class);
			List<Object> options = checkForOptions(type);
			return new FieldDto(field.getName(), type.getName(), isIndexed, options);
		}
	};

	private static List<Object> checkForOptions(Class<?> type) {
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

}
