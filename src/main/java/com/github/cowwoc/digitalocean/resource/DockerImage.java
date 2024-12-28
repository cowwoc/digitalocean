package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import com.github.cowwoc.digitalocean.internal.util.DigitalOceans;
import com.github.cowwoc.digitalocean.scope.DigitalOceanScope;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

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
	 * @param scope      the client configuration
	 * @param repository the enclosing docker repository
	 * @param json       the JSON representation of the image
	 * @return the image
	 * @throws NullPointerException if any of the arguments are null
	 */
	static DockerImage getByJson(DigitalOceanScope scope, DockerRepository repository, JsonNode json)
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
		return new DockerImage(scope, repository, digest, tags, layers);
	}

	private final DigitalOceanScope scope;
	private final DockerRepository repository;
	private final String digest;
	private final Set<String> tags;
	private final Set<Layer> layers;

	/**
	 * Creates a snapshot of the docker image's state.
	 *
	 * @param scope      the client configuration
	 * @param repository the enclosing docker repository
	 * @param digest     a value that uniquely identifies the image
	 * @param tags       the tags that are associated with the image
	 * @param layers     the layers that the image consists of
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 */
	public DockerImage(DigitalOceanScope scope, DockerRepository repository, String digest, Set<String> tags,
		Set<Layer> layers)
	{
		requireThat(scope, "scope").isNotNull();
		requireThat(repository, "repository").isNotNull();
		requireThat(digest, "digest").isStripped().isNotEmpty();
		requireThat(tags, "tags").isNotNull();
		requireThat(layers, "layers").isNotNull();
		this.scope = scope;
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
	 * @throws NullPointerException if {@code repository} is null
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public void destroy(DockerRepository repository) throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_delete_repositoryManifest
		@SuppressWarnings("PMD.CloseResource")
		HttpClient client = scope.getHttpClient();
		ClientRequests clientRequests = scope.getClientRequests();
		DockerRegistry registry = repository.getRegistry();
		String uri = DigitalOceans.REST_SERVER + "/v2/registry/" + registry.getName() + "/repositories/" +
			repository.getName() + "/digests/" + digest;
		Request request = client.newRequest(uri).
			method(DELETE).
			headers(headers -> headers.put("Content-Type", "application/json").
				put("Authorization", "Bearer " + scope.getDigitalOceanToken()));
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case NO_CONTENT_204, NOT_FOUND_404 ->
			{
				// success
			}
			case UNAUTHORIZED_401 ->
			{
				JsonNode json = DigitalOceans.getResponseBody(scope, serverResponse);
				throw new HttpResponseException(json.get("message").textValue(), serverResponse);
			}
			case PRECONDITION_FAILED_412 ->
			{
				// Example: "manifest is referenced by one or more other manifests" or
				// "delete operations are not available while garbage collection is running"
				JsonNode json = DigitalOceans.getResponseBody(scope, serverResponse);
				throw new IllegalArgumentException(json.get("message").textValue());
			}
			default -> throw new AssertionError(
				"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
					"Request: " + clientRequests.toString(request));
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