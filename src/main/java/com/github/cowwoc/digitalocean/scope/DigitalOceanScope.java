package com.github.cowwoc.digitalocean.scope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import org.eclipse.jetty.client.HttpClient;

/**
 * The configuration of a DigitalOcean client.
 * <p>
 * Implementations must be thread-safe.
 */
public interface DigitalOceanScope extends JvmScope
{
	/**
	 * Returns the HTTP client.
	 *
	 * @return the HTTP client
	 * @throws IllegalStateException if the scope is closed
	 */
	HttpClient getHttpClient();

	/**
	 * Returns utility methods for HTTP requests and responses.
	 *
	 * @return utility methods for HTTP requests and responses
	 * @throws IllegalStateException if the scope is closed
	 */
	@SuppressWarnings("ClassEscapesDefinedScope")
	ClientRequests getClientRequests();

	/**
	 * Returns the JSON configuration.
	 *
	 * @return the JSON configuration
	 * @throws IllegalStateException if the scope is closed
	 */
	ObjectMapper getObjectMapper();

	/**
	 * Returns the DigitalOcean authentication token.
	 *
	 * @return the DigitalOcean authentication token
	 * @throws IllegalStateException if the scope is closed
	 */
	String getDigitalOceanToken();
}