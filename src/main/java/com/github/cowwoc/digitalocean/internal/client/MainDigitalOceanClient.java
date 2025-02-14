package com.github.cowwoc.digitalocean.internal.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.internal.util.Exceptions;
import com.github.cowwoc.digitalocean.internal.util.JsonToObject;
import com.github.cowwoc.digitalocean.internal.util.RunMode;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Request.Content;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content.Source;
import org.eclipse.jetty.io.content.ByteBufferContentSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.jackson.DefaultJacksonValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.DELETE;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

/**
 * The client used by the application.
 */
public class MainDigitalOceanClient implements DigitalOceanClient
{
	private static final RunMode RUN_MODE = RunMode.RELEASE;
	/**
	 * Content-types that are known to be textual.
	 */
	private static final Set<String> TEXTUAL_CONTENT_TYPES = Set.of(
		"text/plain",
		"text/html",
		"text/css",
		"application/javascript",
		"text/javascript",
		"application/json",
		"application/xml",
		"text/xml",
		"text/csv",
		"text/markdown",
		"application/x-yaml",
		"application/rtf",
		"application/pdf",
		"text/sgml",
		"application/xhtml+xml",
		"application/ld+json");
	/**
	 * The protocol, hostname, and port the REST API server.
	 */
	public static final URI REST_SERVER = URI.create("https://api.digitalocean.com");
	/**
	 * The order of magnitude between a KiB and MiB, or a MiB and GiB, and so on.
	 *
	 * @see <a href="https://en.wikipedia.org/wiki/Byte#Multiple-byte_units">SI units</a>
	 */
	public static final BigDecimal TIB_TO_GIB = BigDecimal.valueOf(1024);
	/**
	 * Defines the frequency at which it is acceptable to log the same message to indicate that the thread is
	 * still active. This helps in monitoring the progress and ensuring the thread has not become unresponsive.
	 */
	public static final Duration PROGRESS_FREQUENCY = Duration.ofSeconds(2);
	/**
	 * The maximum number of entries that a <a
	 * href="https://docs.digitalocean.com/reference/api/intro/#links--pagination">paginated request</a> may
	 * return.
	 */
	private static final String MAX_ENTRIES_PER_PAGE = "200";
	private final String accessToken;
	private final HttpClientFactory httpClient;
	private final JsonMapper jsonMapper = JsonMapper.builder().
		addModule(new JavaTimeModule()).
		disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).
		build();
	/**
	 * Indicates that the client has shut down.
	 */
	private boolean closed;
	private final Logger log = LoggerFactory.getLogger(MainDigitalOceanClient.class);

	/**
	 * Creates a new instance.
	 *
	 * @param accessToken the DigitalOcean access token
	 * @throws NullPointerException     if {@code accessToken} is null
	 * @throws IllegalArgumentException if {@code accessToken} contains leading or trailing whitespace or is
	 *                                  empty
	 */
	public MainDigitalOceanClient(String accessToken)
	{
		requireThat(accessToken, "accessToken").isStripped().isNotEmpty();
		this.accessToken = accessToken;
		this.httpClient = new HttpClientFactory();
	}

	@Override
	public JsonMapper getJsonMapper()
	{
		ensureOpen();
		return jsonMapper;
	}

	@Override
	public Request createRequest(URI uri)
	{
		ensureOpen();
		return getHttpClient().newRequest(uri).
			headers(headers -> headers.
				put(HttpHeader.AUTHORIZATION, "Bearer " + accessToken));
	}

	/**
	 * Returns the HTTP client.
	 *
	 * @return the HTTP client
	 * @throws IllegalStateException if the client is closed
	 */
	public HttpClient getHttpClient()
	{
		ensureOpen();
		return httpClient.getValue();
	}

	@Override
	public Request createRequest(URI uri, JsonNode body)
	{
		ensureOpen();
		String requestBodyAsString;
		try
		{
			requestBodyAsString = getJsonMapper().writeValueAsString(body);
		}
		catch (JsonProcessingException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
		return createRequest(uri).
			body(new StringRequestContent("application/json", requestBodyAsString));
	}

	@Override
	public JsonNode getResponseBody(ContentResponse serverResponse)
	{
		ensureOpen();
		try
		{
			return getJsonMapper().readTree(serverResponse.getContentAsString());
		}
		catch (JsonProcessingException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}

	@Override
	public int getInt(JsonNode parent, String name)
	{
		ensureOpen();
		JsonNode child = requireThat(parent, "parent").property(name).getValue();
		requireThat(child, name).isIntegralNumber();
		requireThat(child.canConvertToInt(), name + ".canConvertToInt()").withContext(child, "child").isTrue();
		return child.intValue();
	}

	@Override
	public int stringToInt(JsonNode parent, String name)
	{
		ensureOpen();
		JsonNode child = requireThat(parent, "parent").property(name).getValue();
		requireThat(child, name).isString();
		try
		{
			return Integer.parseInt(child.textValue());
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException(name + " cannot be converted to an integer", e);
		}
	}

	@Override
	public boolean getBoolean(JsonNode parent, String name)
	{
		ensureOpen();
		JsonNode child = requireThat(parent, "parent").property(name).getValue();
		requireThat(child, name).isBoolean();
		return child.booleanValue();
	}

	@Override
	public <T> List<T> toList(JsonNode parent, String name, JsonToObject<T> mapper)
		throws IOException, TimeoutException, InterruptedException
	{
		ensureOpen();
		JsonNode arrayNode = parent.get(name);
		if (arrayNode == null)
			return List.of();
		List<T> list = new ArrayList<>(arrayNode.size());
		try
		{
			for (JsonNode element : arrayNode)
				list.add(mapper.map(element));
		}
		catch (RuntimeException e)
		{
			log.warn("Response body: {}", arrayNode.toPrettyString(), e);
			throw e;
		}
		return list;
	}

	@Override
	public <E> Set<E> getElements(JsonNode parent, String name, JsonToObject<E> mapper)
		throws IOException, TimeoutException, InterruptedException
	{
		ensureOpen();
		JsonNode arrayNode = parent.get(name);
		if (arrayNode == null)
			return Set.of();
		Set<E> set = HashSet.newHashSet(arrayNode.size());
		try
		{
			for (JsonNode element : arrayNode)
				set.add(mapper.map(element));
		}
		catch (RuntimeException e)
		{
			log.warn("Response body: {}", arrayNode.toPrettyString(), e);
			throw e;
		}
		return set;
	}

	@Override
	public <T> Set<T> getElements(URI uri, Map<String, Collection<String>> parameters,
		JsonToObject<Set<T>> mapper) throws IOException, TimeoutException, InterruptedException
	{
		ensureOpen();
		Set<T> elements = new HashSet<>();
		do
		{
			JsonNode body = requestSinglePage(uri, parameters);
			try
			{
				elements.addAll(mapper.map(body));
			}
			catch (RuntimeException e)
			{
				log.warn("Response body: {}", body.toPrettyString(), e);
				throw e;
			}
			uri = getNextPage(body);
		}
		while (uri != null);
		return elements;
	}

	/**
	 * Requests a single page of results.
	 *
	 * @param uri        the URI to send a request to
	 * @param parameters the parameters to add to the request
	 * @return the response body
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	private JsonNode requestSinglePage(URI uri, Map<String, Collection<String>> parameters)
		throws IOException, InterruptedException, TimeoutException
	{
		Request request = createRequest(uri);
		request.param("per_page", MAX_ENTRIES_PER_PAGE);
		for (Entry<String, Collection<String>> entry : parameters.entrySet())
		{
			String key = entry.getKey();
			for (String value : entry.getValue())
				request.param(key, value);
		}
		request.method(GET);
		Response serverResponse = send(request);
		if (serverResponse.getStatus() != OK_200)
		{
			throw new AssertionError("Unexpected response: " + toString(serverResponse) + "\n" +
				"Request: " + toString(request));
		}
		return getResponseBody((ContentResponse) serverResponse);
	}

	/**
	 * Returns the URI for the next set of results.
	 *
	 * @param response a server response
	 * @return null if there are no more pages
	 * @throws NullPointerException if {@code response} is null
	 */
	private static URI getNextPage(JsonNode response)
	{
		// https://docs.digitalocean.com/reference/api/intro/#links--pagination
		JsonNode linksNode = response.get("links");
		if (linksNode == null)
			return null;
		JsonNode pages = linksNode.get("pages");
		if (pages == null)
			return null;
		JsonNode nextPageNode = pages.get("next");
		if (nextPageNode == null)
			return null;
		return URI.create(nextPageNode.textValue());
	}

	@Override
	public <T> T getElement(URI uri, Map<String, Collection<String>> parameters, JsonToObject<T> mapper)
		throws IOException, TimeoutException, InterruptedException
	{
		ensureOpen();
		do
		{
			JsonNode body = requestSinglePage(uri, parameters);
			T match;
			try
			{
				match = mapper.map(body);
			}
			catch (RuntimeException e)
			{
				log.warn("Response body: {}", body.toPrettyString(), e);
				throw e;
			}
			if (match != null)
				return match;
			uri = getNextPage(body);
		}
		while (uri != null);
		return null;
	}

	@Override
	public <T> T getResource(URI uri, JsonToObject<T> mapper)
		throws IOException, TimeoutException, InterruptedException
	{
		ensureOpen();
		Request request = createRequest(uri).
			method(GET);
		Response serverResponse = send(request);
		if (serverResponse.getStatus() != OK_200)
		{
			throw new AssertionError("Unexpected response: " + toString(serverResponse) + "\n" +
				"Request: " + toString(request));
		}
		ContentResponse contentResponse = (ContentResponse) serverResponse;
		JsonNode body = getResponseBody(contentResponse);
		try
		{
			return mapper.map(body);
		}
		catch (RuntimeException e)
		{
			log.warn("Response body: {}", body.toPrettyString(), e);
			throw e;
		}
	}

	@Override
	public void destroyResource(URI uri) throws IOException, TimeoutException, InterruptedException
	{
		ensureOpen();
		Request request = createRequest(uri).
			method(DELETE);
		Response serverResponse = send(request);
		if (serverResponse.getStatus() != NO_CONTENT_204)
		{
			throw new AssertionError("Unexpected response: " + toString(serverResponse) + "\n" +
				"Request: " + toString(request));
		}
	}

	@Override
	public String toString(Request request)
	{
		ensureOpen();
		StringBuilder result = new StringBuilder("< HTTP ");
		result.append(request.getMethod()).append(' ').
			append(request.getURI()).append('\n');

		HttpFields requestHeaders = request.getHeaders();
		if (requestHeaders.size() > 0)
		{
			result.append("<\n");
			for (HttpField header : requestHeaders)
			{
				StringJoiner values = new StringJoiner(",");
				for (String value : header.getValues())
					values.add(value);
				result.append("< ").append(header.getName()).append(": ").append(values).append('\n');
			}
		}
		try
		{
			String body = getBodyAsString(request);
			if (!body.isEmpty())
			{
				if (!result.isEmpty())
					result.append("<\n");
				body = "< " + body.replaceAll("\n", "\n< ");
				result.append(body);
				if (!body.endsWith("\n"))
					result.append('\n');
			}
		}
		catch (IOException e)
		{
			result.append(Exceptions.toString(e));
		}
		return result.toString();
	}

	/**
	 * @param request a client request
	 * @return the response body (an empty string if empty)
	 * @throws IOException if an I/O error occurs while reading the request body
	 */
	private String getBodyAsString(Request request) throws IOException
	{
		ensureOpen();
		Content body = request.getBody();
		if (body == null || body.getLength() == 0)
			return "";
		// Per https://datatracker.ietf.org/doc/html/rfc9110#name-field-values values consist of ASCII
		// characters, so it's safe to convert them to lowercase.
		String contentType = body.getContentType();
		if (contentType != null)
		{
			contentType = contentType.toLowerCase(Locale.ROOT);
			if (!TEXTUAL_CONTENT_TYPES.contains(contentType))
				return "[" + body.getLength() + " bytes]";
		}
		convertToRewindableContent(request);
		if (!body.rewind())
			throw new AssertionError("Unable to rewind body: " + body);
		return Source.asString(body);
	}

	@Override
	public Response send(Request request) throws IOException, TimeoutException, InterruptedException
	{
		ensureOpen();
		if (RUN_MODE == RunMode.DEBUG)
			convertToRewindableContent(request);
		try
		{
			return request.send();
		}
		catch (ExecutionException e)
		{
			Throwable cause = e.getCause();
			if (cause instanceof IOException ioe)
				throw ioe;
			if (cause instanceof HttpResponseException hre)
				return hre.getResponse();
			throw new AssertionError(toString(request), e);
		}
	}

	/**
	 * Converts the request body to a format that is rewindable.
	 *
	 * @param request the client request
	 * @throws NullPointerException if {@code request} is null
	 * @throws IOException          if an error occurs while reading the body
	 */
	private void convertToRewindableContent(Request request) throws IOException
	{
		Content body = request.getBody();
		if (body == null || body instanceof ByteBufferContentSource)
			return;
		byte[] byteArray;
		try (InputStream in = Source.asInputStream(body))
		{
			byteArray = in.readAllBytes();
		}
		request.body(new BytesRequestContent(body.getContentType(), byteArray));
	}

	/**
	 * @param response the server response
	 * @return the response HTTP version, status code and reason phrase
	 */
	private String getStatusLine(Response response)
	{
		String reason = response.getReason();
		if (reason == null)
		{
			// HTTP "reason" was removed in HTTP/2. See: https://github.com/jetty/jetty.project/issues/11593
			reason = HttpStatus.getCode(response.getStatus()).getMessage();
		}
		return response.getVersion() + " " + response.getStatus() + " (\"" + reason + "\")";
	}

	/**
	 * @param response the server response
	 * @return the string representation of the response's headers
	 */
	private String getHeadersAsString(Response response)
	{
		StringJoiner result = new StringJoiner("\n");
		for (HttpField header : response.getHeaders())
			result.add(header.toString());
		return result.toString();
	}

	@Override
	public String toString(Response response)
	{
		ensureOpen();
		requireThat(response, "response").isNotNull();
		StringJoiner resultAsString = new StringJoiner("\n");
		resultAsString.add(getStatusLine(response)).
			add(getHeadersAsString(response));
		if (response instanceof ContentResponse contentResponse)
		{
			String responseBody = contentResponse.getContentAsString();
			if (!responseBody.isEmpty())
			{
				resultAsString.add("");
				resultAsString.add(responseBody);
			}
		}
		return resultAsString.toString();
	}

	/**
	 * Ensures that the client is open.
	 *
	 * @throws IllegalStateException if the client is closed
	 */
	private void ensureOpen()
	{
		if (isClosed())
			throw new IllegalStateException("client was closed");
	}

	@Override
	public boolean isClosed()
	{
		return closed;
	}

	@Override
	public void close()
	{
		if (closed)
			return;
		httpClient.close();
		closed = true;
	}
}