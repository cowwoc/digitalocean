package com.github.cowwoc.digitalocean.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import org.eclipse.jetty.client.HttpClient;

/**
 * The internals of a {@code DigitalOceanClient}.
 */
public interface InternalClient
{
	/**
	 * Returns the HTTP client.
	 *
	 * @return the HTTP client
	 * @throws IllegalStateException if the client is closed
	 */
	HttpClient getHttpClient();

	/**
	 * Returns utility methods for HTTP requests and responses.
	 *
	 * @return utility methods for HTTP requests and responses
	 * @throws IllegalStateException if the client is closed
	 */
	ClientRequests getClientRequests();

	/**
	 * Returns the JSON configuration.
	 *
	 * @return the JSON configuration
	 * @throws IllegalStateException if the client is closed
	 */
	ObjectMapper getObjectMapper();

	/**
	 * Returns the DigitalOcean access token.
	 *
	 * @return the DigitalOcean access token
	 * @throws IllegalStateException if the client is closed
	 */
	String getAccessToken();
}