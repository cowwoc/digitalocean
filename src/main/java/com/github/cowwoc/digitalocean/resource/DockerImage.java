package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.DELETE;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.PRECONDITION_FAILED_412;
import static org.eclipse.jetty.http.HttpStatus.UNAUTHORIZED_401;

/**
 * A docker image.
 */
public final class DockerImage
{
	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param client     the client configuration
	 * @param repository the enclosing docker repository
	 * @param json       the JSON representation
	 * @return the docker image
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	static DockerImage getByJson(DigitalOceanClient client, DockerRepository repository, JsonNode json)
	{
		String digest = json.get("digest").textValue();
		Set<String> tags = new HashSet<>();
		for (JsonNode tag : json.get("tags"))
			tags.add(tag.textValue());

		Set<Layer> layers = new HashSet<>();
		for (JsonNode node : json.get("blobs"))
		{
			String layerDigest = node.get("digest").textValue();
			layers.add(new Layer(layerDigest));
		}
		return new DockerImage(client, repository, digest, tags, layers);
	}

	private final DigitalOceanClient client;
	private final DockerRepository repository;
	private final String digest;
	private final Set<String> tags;
	private final Set<Layer> layers;

	/**
	 * Creates a snapshot of the docker image's state.
	 *
	 * @param client     the client configuration
	 * @param repository the enclosing docker repository
	 * @param digest     a value that uniquely identifies the image
	 * @param tags       the tags that are associated with the image
	 * @param layers     the layers that the image consists of
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	public DockerImage(DigitalOceanClient client, DockerRepository repository, String digest, Set<String> tags,
		Set<Layer> layers)
	{
		requireThat(client, "client").isNotNull();
		requireThat(repository, "repository").isNotNull();
		requireThat(digest, "digest").isStripped().isNotEmpty();
		requireThat(tags, "tags").isNotNull();
		requireThat(layers, "layers").isNotNull();
		this.client = client;
		this.repository = repository;
		this.digest = digest;
		this.tags = Set.copyOf(tags);
		this.layers = Set.copyOf(layers);
	}

	/**
	 * Returns the image's digest.
	 *
	 * @return the digest
	 */
	public String getDigest()
	{
		return digest;
	}

	/**
	 * Returns the image's tags.
	 *
	 * @return the tags
	 */
	public Set<String> getTags()
	{
		return tags;
	}

	/**
	 * Returns the layers that are used by the image.
	 *
	 * @return the layers
	 */
	public Set<Layer> getLayers()
	{
		return layers;
	}

	/**
	 * Destroys the image.
	 *
	 * @param repository the image repository to remove the image from
	 * @throws NullPointerException  if {@code repository} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public void destroy(DockerRepository repository) throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_delete_repositoryManifest
		DockerRegistry registry = repository.getRegistry();
		URI uri = REST_SERVER.resolve("v2/registry/" + registry.getName() + "/repositories/" +
			repository.getName() + "/digests/" + digest);
		Request request = client.createRequest(uri).
			method(DELETE);
		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case NO_CONTENT_204, NOT_FOUND_404 ->
			{
				// success
			}
			case UNAUTHORIZED_401 ->
			{
				JsonNode json = client.getResponseBody(serverResponse);
				throw new HttpResponseException(json.get("message").textValue(), serverResponse);
			}
			case PRECONDITION_FAILED_412 ->
			{
				// Example: "manifest is referenced by one or more other manifests" or
				// "delete operations are not available while garbage collection is running"
				JsonNode json = client.getResponseBody(serverResponse);
				throw new IllegalArgumentException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
	}

	/**
	 * Returns the enclosing docker repository.
	 *
	 * @return the docker repository
	 */
	public DockerRepository getRepository()
	{
		return repository;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(repository, digest);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof DockerImage other && other.repository.equals(repository) &&
			other.digest.equals(digest);
	}

	/**
	 * A layer of the image.
	 *
	 * @param digest the digest of the layer
	 */
	public record Layer(String digest)
	{
		/**
		 * Creates a new Layer.
		 *
		 * @param digest the digest of the layer
		 * @throws NullPointerException     if {@code digest} is null
		 * @throws IllegalArgumentException if {@code digest} contains leading or trailing whitespace or is empty
		 */
		public Layer
		{
			requireThat(digest, "digest").isStripped().isNotEmpty();
		}
	}
}