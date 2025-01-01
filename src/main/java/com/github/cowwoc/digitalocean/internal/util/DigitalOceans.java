package com.github.cowwoc.digitalocean.internal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.http.HttpHeader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.jackson.DefaultJacksonValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

/**
 * DigitalOcean helper functions and constants.
 */
public final class DigitalOceans
{
	/**
	 * The protocol, hostname, and port the REST API server.
	 */
	public static final String REST_SERVER = "https://api.digitalocean.com";
	/**
	 * The maximum number of entries that a <a
	 * href="https://docs.digitalocean.com/reference/api/intro/#links--pagination">paginated request</a> may
	 * return.
	 */
	public static final String MAX_ENTRIES_PER_PAGE = "200";

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
	public static int toInt(JsonNode parent, String name)
	{
		JsonNode child = requireThat(parent, "parent").property(name).getValue();
		requireThat(child, name).isIntegralNumber();
		requireThat(child.canConvertToInt(), name + ".canConvertToInt()").isTrue();
		return child.intValue();
	}

	/**
	 * Returns the {@code boolean} value of a JSON node.
	 *
	 * @param parent a JSON node
	 * @param name   the name of the child node
	 * @return the {@code boolean} value
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the child's value is not a boolean
	 */
	public static boolean toBoolean(JsonNode parent, String name)
	{
		JsonNode child = requireThat(parent, "parent").property(name).getValue();
		requireThat(child, name).isBoolean();
		return child.booleanValue();
	}

	/**
	 * Returns all elements from a paginated list.
	 *
	 * @param <T>        the type of elements in the list
	 * @param client     the client configuration
	 * @param uri        the URI to send a request to
	 * @param parameters the parameters to add to the request
	 * @param mapper     a function that transforms the server response into a list of elements
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
	public static <T> List<T> getElements(DigitalOceanClient client, String uri,
		Map<String, Collection<String>> parameters, JsonMapper<List<T>> mapper)
		throws IOException, TimeoutException, InterruptedException
	{
		List<T> elements = new ArrayList<>();
		do
		{
			JsonNode body = sendRequest(client, uri, parameters);
			// https://docs.digitalocean.com/reference/api/intro/#links--pagination
			elements.addAll(mapper.map(body));
			uri = getNextPage(body);
		}
		while (uri != null);
		return elements;
	}

	/**
	 * Sends a client request.
	 *
	 * @param client     the client configuration
	 * @param uri        the URI to send the request to
	 * @param parameters the query parameters of the request
	 * @return the server response
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	private static JsonNode sendRequest(DigitalOceanClient client, String uri,
		Map<String, Collection<String>> parameters) throws IOException, TimeoutException, InterruptedException
	{
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		Request request = httpClient.newRequest(uri);
		for (Map.Entry<String, Collection<String>> entry : parameters.entrySet())
			for (String value : entry.getValue())
				request.param(entry.getKey(), value);
		ClientRequests clientRequests = client.getClientRequests();
		ContentResponse serverResponse = clientRequests.send(request.
			param("per_page", MAX_ENTRIES_PER_PAGE).
			headers(headers -> headers.put("Content-Type", "application/json").
				put("Authorization", "Bearer " + client.getAccessToken())).
			method(GET));
		if (serverResponse.getStatus() != OK_200)
		{
			throw new AssertionError("Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
				"Request: " + clientRequests.toString(request));
		}
		return getResponseBody(client, serverResponse);
	}

	/**
	 * Returns the URI for the next set of results.
	 *
	 * @param response a server response
	 * @return null if there are no more pages
	 * @throws NullPointerException if {@code response} is null
	 */
	private static String getNextPage(JsonNode response)
	{
		JsonNode linksNode = response.get("links");
		if (linksNode == null)
			return null;
		JsonNode pages = linksNode.get("pages");
		if (pages == null)
			return null;
		JsonNode nextPageNode = pages.get("next");
		if (nextPageNode == null)
			return null;
		return nextPageNode.textValue();
	}

	/**
	 * Returns an element from a paginated list.
	 *
	 * @param <T>        the type of elements in the list
	 * @param client     the client configuration
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
	public static <T> T getElement(DigitalOceanClient client, String uri,
		Map<String, Collection<String>> parameters, JsonMapper<T> mapper)
		throws IOException, TimeoutException, InterruptedException
	{
		do
		{
			@SuppressWarnings("PMD.CloseResource")
			HttpClient httpClient = client.getHttpClient();
			Request request = httpClient.newRequest(uri);
			for (Map.Entry<String, Collection<String>> entry : parameters.entrySet())
				for (String value : entry.getValue())
					request.param(entry.getKey(), value);
			ClientRequests clientRequests = client.getClientRequests();
			ContentResponse serverResponse = clientRequests.send(request.
				param("per_page", MAX_ENTRIES_PER_PAGE).
				headers(headers -> headers.put("Content-Type", "application/json").
					put("Authorization", "Bearer " + client.getAccessToken())).
				method(GET));
			JsonNode body = switch (serverResponse.getStatus())
			{
				case OK_200 -> getResponseBody(client, serverResponse);
				default -> throw new AssertionError(
					"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
						"Request: " + clientRequests.toString(request));
			};
			// https://docs.digitalocean.com/reference/api/intro/#links--pagination
			T match = mapper.map(body);
			if (match != null)
				return match;
			uri = getNextPage(body);
		}
		while (uri != null);
		return null;
	}

	/**
	 * Creates a request without a body.
	 *
	 * @param client the client configuration
	 * @param uri    the URI the send a request to
	 * @return the request
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 */
	public static Request createRequest(DigitalOceanClient client, String uri)
	{
		return client.getHttpClient().newRequest(uri).
			headers(headers -> headers.
				put(HttpHeader.CONTENT_TYPE, "application/json").
				put("Authorization", "Bearer " + client.getAccessToken()));
	}

	/**
	 * Creates a request with a body.
	 *
	 * @param client      the client configuration
	 * @param uri         the URI the send a request to
	 * @param requestBody the request body
	 * @return the request
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 */
	public static Request createRequest(DigitalOceanClient client, String uri, JsonNode requestBody)
	{
		String requestBodyAsString;
		try
		{
			requestBodyAsString = client.getObjectMapper().writeValueAsString(requestBody);
		}
		catch (JsonProcessingException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
		return client.getHttpClient().newRequest(uri).
			headers(headers -> headers.put("Authorization", "Bearer " + client.getAccessToken())).
			body(new StringRequestContent("application/json", requestBodyAsString));
	}

	/**
	 * Returns the JSON representation of the server response.
	 *
	 * @param client         the client configuration
	 * @param serverResponse the server response
	 * @return the JSON representation of the response
	 * @throws NullPointerException    if any of the arguments are null
	 * @throws IllegalStateException   if the client is closed
	 * @throws WrappedCheckedException if the server response could not be parsed
	 */
	public static JsonNode getResponseBody(DigitalOceanClient client, ContentResponse serverResponse)
	{
		try
		{
			return client.getObjectMapper().readTree(serverResponse.getContentAsString());
		}
		catch (JsonProcessingException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}

	private DigitalOceans()
	{
	}
}