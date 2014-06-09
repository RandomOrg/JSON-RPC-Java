package org.random.api;

import java.util.concurrent.Callable;

import com.google.gson.JsonObject;

/** Allow an input JsonObject to be set for a Callable. */
public class JsonObjectInputCallable<T> implements Callable<T> {
	
	protected JsonObject input;
	
	public void setInput(JsonObject input) {
		this.input = input;
	}

	@Override
	public T call() throws Exception {
		return null;
	}
}