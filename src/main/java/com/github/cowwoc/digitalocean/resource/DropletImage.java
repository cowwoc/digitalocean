package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.internal.util.DigitalOceans;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpMethod;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.util.DigitalOceans.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * The system image that was used to boot droplets.
 */
public final class DropletImage
{
	private final int id;
	private final String slug;

	/**
	 * Creates a new image.
	 *
	 * @param id   the id of the image
	 * @param slug the slug of the image
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	DropletImage(int id, String slug)
	{
		requireThat(slug, "slug").isStripped().isNotEmpty();
		this.id = id;
		this.slug = slug;
	}

	/**
	 * Returns the ID of the image.
	 *
	 * @return the ID of the image
	 */
	public int getId()
	{
		return id;
	}

	/**
	 * Returns the slug of the image.
	 *
	 * @return the slug of the image
	 */
	public String getSlug()
	{
		return slug;
	}

	/**
	 * Looks up an image by its slug.
	 *
	 * @param client the client configuration
	 * @param slug   a slug
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code slug} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static DropletImage getBySlug(DigitalOceanClient client, String slug)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(slug, "slug").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/images_get
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		String uri = REST_SERVER + "/v2/images/" + slug;
		ContentResponse serverResponse = client.getClientRequests().send(httpClient.newRequest(uri).
			method(HttpMethod.GET).
			headers(headers -> headers.put("Content-Type", "application/json").
				put("Authorization", "Bearer " + client.getAccessToken())));
		JsonNode body = DigitalOceans.getResponseBody(client, serverResponse);
		JsonNode image = body.get("image");
		int id = DigitalOceans.toInt(image, "id");
		return new DropletImage(id, slug);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof DropletImage other && other.id == id && other.slug.equals(slug);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, slug);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DropletImage.class).
			add("id", id).
			add("slug", slug).
			toString();
	}
}