package com.threewks.thundr.gae.admin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class Property {

	private Field field;
	private List<Object> possibleValues = new ArrayList<>();

	public Property(Field field) {
		this.field = field;
	}

	public void addPossibleValues(List<Object> values) {
		this.possibleValues.addAll(values);
	}

	public Field field() {
		return field;
	}

	public List<Object> possibleValues() {
		return possibleValues;
	}
}
