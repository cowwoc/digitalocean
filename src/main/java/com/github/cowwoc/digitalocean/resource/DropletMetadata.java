package com.github.cowwoc.digitalocean.resource;

import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;

import java.io.IOException;
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
	 * @return null if the JVM is unable to detect the droplet that it is running on
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Integer getDropletId(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/metadata-api/#operation/getDropletId
		String value = getValue(client, URL_PREFIX + "/metadata/v1/id");
		if (value == null)
			return null;
		return Integer.parseInt(value);
	}

	/**
	 * Returns the hostname of the droplet that the JVM is running on.
	 *
	 * @param client the client configuration
	 * @return null if the JVM is unable to detect the droplet that it is running on
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static String getHostname(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		return getValue(client, URL_PREFIX + "/metadata/v1/hostname");
	}

	/**
	 * Returns the region of the droplet that the JVM is running on.
	 *
	 * @param client the client configuration
	 * @return null if the JVM is unable to detect the droplet that it is running on
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static String getRegion(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		return getValue(client, URL_PREFIX + "/metadata/v1/region");
	}

	/**
	 * Returns a metadata value.
	 *
	 * @param client the client configuration
	 * @param uri    the URI of the REST endpoint
	 * @return if the JVM is unable to detect the droplet that it is running on
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code uri} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	private static String getValue(DigitalOceanClient client, String uri)
		throws IOException, TimeoutException, InterruptedException
	{
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		ClientRequests clientRequests = client.getClientRequests();
		ContentResponse serverResponse = clientRequests.send(httpClient.newRequest(uri).
			method(GET).
			headers(headers -> headers.put("Content-Type", "application/json").
				put("Authorization", "Bearer " + client.getAccessToken())));
		if (serverResponse.getStatus() != OK_200)
			return null;
		return serverResponse.getContentAsString();
	}

	private DropletMetadata()
	{
	}
}