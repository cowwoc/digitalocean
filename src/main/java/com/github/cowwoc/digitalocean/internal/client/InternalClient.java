package com.github.cowwoc.digitalocean.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.cowwoc.digitalocean.internal.util.JsonToObject;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * The internals of a {@code DigitalOceanClient}.
 */
public interface InternalClient
{
	/**
	 * Returns a JSON mapper.
	 *
	 * @return the JSON mapper
	 * @throws IllegalStateException if the client is closed
	 */
	JsonMapper getJsonMapper();

	/**
	 * Creates a request without a body and sets the {@code Authorization} header.
	 *
	 * @param uri the URI the send a request to
	 * @return the request
	 * @throws NullPointerException  if {@code uri} is null
	 * @throws IllegalStateException if the client is closed
	 */
	Request createRequest(URI uri);

	/**
	 * Creates a request with a body and sets the {@code Authorization} header.
	 *
	 * @param uri  the URI the send a request to
	 * @param body the request body
	 * @return the request
	 * @throws NullPointerException  if {@code uri} is null
	 * @throws IllegalStateException if the client is closed
	 */
	Request createRequest(URI uri, JsonNode body);

	/**
	 * Returns the JSON representation of the server response.
	 *
	 * @param serverResponse the server response
	 * @return the JSON representation of the response
	 * @throws NullPointerException    if {@code serverRespoonse} is null
	 * @throws IllegalStateException   if the client is closed
	 * @throws WrappedCheckedException if the server response could not be parsed
	 */
	JsonNode getResponseBody(ContentResponse serverResponse);

	/**
	 * Returns the {@code int} value of a JSON node.
	 *
	 * @param parent a JSON node
	 * @param name   the name of the child node
	 * @return the {@code int} value of the child node
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the child's value is not an integer or does not fit in an
	 *                                  {@code int}
	 */
	int getInt(JsonNode parent, String name);

	/**
	 * Returns the {@code int} representation of a JSON string node.
	 *
	 * @param parent a JSON node
	 * @param name   the name of the child node
	 * @return the {@code int} representation of the child node
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the child's value is not a String or does not fit in an {@code int}
	 */
	int stringToInt(JsonNode parent, String name);

	/**
	 * Returns the {@code boolean} value of a JSON node.
	 *
	 * @param parent a JSON node
	 * @param name   the name of the child node
	 * @return the {@code boolean} value
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the child's value is not a boolean
	 */
	boolean getBoolean(JsonNode parent, String name);

	/**
	 * Returns the {@code List<T>} representation of a JSON array.
	 *
	 * @param <T>    the type of elements in the array
	 * @param parent a JSON node
	 * @param name   the name of the child node
	 * @param mapper a function that transforms the server response into a list of elements
	 * @return the {@code List<String>} value
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the child's value is not a {@code List<String>}
	 */
	<T> List<T> toList(JsonNode parent, String name, JsonToObject<T> mapper)
		throws IOException, TimeoutException, InterruptedException;

	/**
	 * Returns the elements of a JSON array.
	 *
	 * @param <E>    the type to convert the elements into
	 * @param parent a JSON node
	 * @param name   the name of the child node
	 * @param mapper a function that transforms the server response into a set of elements
	 * @return the {@code Set<E>} value
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	<E> Set<E> getElements(JsonNode parent, String name, JsonToObject<E> mapper)
		throws IOException, TimeoutException, InterruptedException;

	/**
	 * Returns all the elements from a paginated list.
	 *
	 * @param <T>        the type of elements in the Set
	 * @param uri        the URI to send a request to
	 * @param parameters the parameters to add to the request
	 * @param mapper     a function that transforms the server response into a set of elements
	 * @return the elements
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	<T> Set<T> getElements(URI uri, Map<String, Collection<String>> parameters, JsonToObject<Set<T>> mapper)
		throws IOException, TimeoutException, InterruptedException;

	/**
	 * Returns an element from a paginated list.
	 *
	 * @param <T>        the type of elements in the list
	 * @param uri        the URI to send a request to
	 * @param parameters the parameters to add to the request
	 * @param mapper     a function that transforms the server response into a non-null element if a match is
	 *                   found
	 * @return null if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	<T> T getElement(URI uri, Map<String, Collection<String>> parameters, JsonToObject<T> mapper)
		throws IOException, TimeoutException, InterruptedException;

	/**
	 * Looks up a resource.
	 *
	 * @param <T>    the type of the resource
	 * @param uri    the URI of the resource
	 * @param mapper a function that transforms the server response into a resource if a match is found
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	<T> T getResource(URI uri, JsonToObject<T> mapper)
		throws IOException, TimeoutException, InterruptedException;

	/**
	 * Destroys a resource.
	 *
	 * @param uri the URI of the resource
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	void destroyResource(URI uri) throws IOException, TimeoutException, InterruptedException;

	/**
	 * Sends a request.
	 *
	 * @param request the client request
	 * @return the server response
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	Response send(Request request) throws IOException, TimeoutException, InterruptedException;

	/**
	 * Returns the String representation of the response
	 *
	 * @param response the server response
	 * @return the String representation
	 */
	String toString(Response response);

	/**
	 * Returns the String representation of the request.
	 *
	 * @param request a client request
	 * @return the String representation
	 */
	String toString(Request request);
}