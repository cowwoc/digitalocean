package com.github.cowwoc.digitalocean.internal.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Parses a JSON document into a Java object.
 *
 * @param <T> the type of value returned by this mapper
 */
public interface JsonMapper<T>
{
	/**
	 * Parses JSON into an Object.
	 *
	 * @param json the JSON document
	 * @return the Java object
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	T map(JsonNode json) throws IOException, TimeoutException, InterruptedException;
}