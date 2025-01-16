package com.github.cowwoc.digitalocean.client;

import com.github.cowwoc.digitalocean.internal.client.InternalClient;
import com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient;
import com.github.cowwoc.digitalocean.resource.DropletMetadata;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A client of the DigitalOcean REST API.
 */
public interface DigitalOceanClient extends AutoCloseable, InternalClient
{
	/**
	 * Creates a new client.
	 *
	 * @param accessToken an API access token
	 * @return a new client
	 * @throws NullPointerException     if {@code accessToken} is null
	 * @throws IllegalArgumentException if {@code accessToken} contains leading or trailing whitespace or is
	 *                                  empty
	 */
	static DigitalOceanClient using(String accessToken)
	{
		requireThat(accessToken, "accessToken").isNotNull();
		return new MainDigitalOceanClient(accessToken);
	}

	/**
	 * Returns a client for APIs that do not require an access token, such as {@link DropletMetadata}.
	 *
	 * @return an anonymous client
	 */
	static DigitalOceanClient anonymous()
	{
		return new MainDigitalOceanClient("anonymous");
	}

	/**
	 * Determines if the client is closed.
	 *
	 * @return {@code true} if the client is closed
	 */
	boolean isClosed();

	/**
	 * Closes the client.
	 */
	@Override
	void close();
}