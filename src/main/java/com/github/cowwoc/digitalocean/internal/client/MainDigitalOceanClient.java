package com.github.cowwoc.digitalocean.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import org.eclipse.jetty.client.HttpClient;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * The client used by the application.
 */
public class MainDigitalOceanClient implements DigitalOceanClient
{
	private final String accessToken;
	private final HttpClientFactory httpClient;
	private final ClientRequests clientRequests = new ClientRequests();
	private final ObjectMapper objectMapper = JsonMapper.builder().
		addModule(new JavaTimeModule()).
		disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).
		build();
	/**
	 * Indicates that the client has shut down.
	 */
	private boolean closed;

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
	public HttpClient getHttpClient()
	{
		ensureOpen();
		return httpClient.getValue();
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
	public ClientRequests getClientRequests()
	{
		ensureOpen();
		return clientRequests;
	}

	@Override
	public ObjectMapper getObjectMapper()
	{
		ensureOpen();
		return objectMapper;
	}

	@Override
	public String getAccessToken()
	{
		return accessToken;
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