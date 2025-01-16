package com.github.cowwoc.digitalocean.resource;

import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

/**
 * Information about the droplet that the JVM is running in.
 */
public final class DropletMetadata
{
	private static final String URL_PREFIX = "http://169.254.169.254";

	/**
	 * Returns the ID of the droplet that the JVM is running on.
	 *
	 * @param client the client configuration
	 * @return null when running outside a droplet
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Integer getDropletId(DigitalOceanClient client)
		throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/metadata-api/#operation/getDropletId
		String value = getValue(client, URI.create(URL_PREFIX + "/metadata/v1/id"));
		if (value == null)
			return null;
		return Integer.parseInt(value);
	}

	/**
	 * Returns the hostname of the droplet that the JVM is running on.
	 *
	 * @param client the client configuration
	 * @return null when running outside a droplet
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static String getHostname(DigitalOceanClient client)
		throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/metadata-api/#operation/getHostname
		return getValue(client, URI.create(URL_PREFIX + "/metadata/v1/hostname"));
	}

	/**
	 * Returns the region of the droplet that the JVM is running on.
	 *
	 * @param client the client configuration
	 * @return null when running outside a droplet
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static String getRegion(DigitalOceanClient client)
		throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/metadata-api/#operation/getRegion
		return getValue(client, URI.create(URL_PREFIX + "/metadata/v1/region"));
	}

	/**
	 * Returns a metadata value.
	 *
	 * @param client the client configuration
	 * @param uri    the URI of the REST endpoint
	 * @return null when running outside a droplet
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code uri} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	private static String getValue(DigitalOceanClient client, URI uri)
		throws IOException, InterruptedException
	{
		ContentResponse serverResponse;
		try
		{
			// Reduce the timeout since the server is expected to respond quickly. Additionally, we want to timeout
			// swiftly if the service is unavailable when running outside a droplet.
			Request request = client.createRequest(uri).
				timeout(1, TimeUnit.SECONDS).
				method(GET);
			serverResponse = client.send(request);
		}
		catch (TimeoutException _)
		{
			return null;
		}
		if (serverResponse.getStatus() != OK_200)
			return null;
		return serverResponse.getContentAsString();
	}

	private DropletMetadata()
	{
	}
}