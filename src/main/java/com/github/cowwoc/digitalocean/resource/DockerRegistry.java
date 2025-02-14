package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.exception.AccessDeniedException;
import com.github.cowwoc.digitalocean.internal.util.RetryDelay;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.http.HttpStatus.UNAUTHORIZED_401;

/**
 * A registry of docker repositories.
 */
public final class DockerRegistry
{
	/**
	 * Returns the account's docker registry.
	 *
	 * @param client the client configuration
	 * @return null if the account does not have a container registry
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 * @throws AccessDeniedException if the client does not have access to the registry
	 */
	public static DockerRegistry get(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException, AccessDeniedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_get
		Request request = client.createRequest(REST_SERVER.resolve("v2/registry")).
			method(GET);
		Response serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			case UNAUTHORIZED_401 ->
			{
				ContentResponse contentResponse = (ContentResponse) serverResponse;
				JsonNode json = client.getResponseBody(contentResponse);
				throw new AccessDeniedException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		ContentResponse contentResponse = (ContentResponse) serverResponse;
		JsonNode body = client.getJsonMapper().readTree(contentResponse.getContentAsString());
		JsonNode registryNode = body.get("registry");
		return getByJson(client, registryNode);
	}

	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param client the client configuration
	 * @param json   the JSON representation
	 * @return the registry
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private static DockerRegistry getByJson(DigitalOceanClient client, JsonNode json)
	{
		String name = json.get("name").textValue();
		return new DockerRegistry(client, name);
	}

	final DigitalOceanClient client;
	private final String name;

	/**
	 * Creates a snapshot of the container registry's state.
	 *
	 * @param client the client configuration
	 * @param name   the name of the registry
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 */
	private DockerRegistry(DigitalOceanClient client, String name)
	{
		requireThat(client, "client").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		this.client = client;
		this.name = name;
	}

	/**
	 * Returns the hostname of the registry. For example, given {@code registry.digitalocean.com/nasa}, this
	 * method will return {@code registry.digitalocean.com}.
	 *
	 * @return the hostname of the registry
	 */
	public String getHostname()
	{
		return "registry.digitalocean.com";
	}

	/**
	 * Returns the name of the registry. For example, given {@code registry.digitalocean.com/nasa}, this method
	 * will return {@code nasa}.
	 *
	 * @return the name of the registry
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the fully qualified name of the repository (e.g., {@code registry.digitalocean.com/nasa}).
	 *
	 * @return the fully qualified name
	 */
	public String getFullyQualifiedName()
	{
		return getHostname() + "/" + getName();
	}

	/**
	 * Deletes unused image layers from the registry.
	 *
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public void deleteUnusedLayers() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_run_garbageCollection
		URI uri = REST_SERVER.resolve("v2/registry/" + name + "/garbage-collection");
		Request request = client.createRequest(uri).
			method(POST);
		Response serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		ContentResponse contentResponse = (ContentResponse) serverResponse;
		JsonNode body = client.getResponseBody(contentResponse);
		JsonNode garbageCollection = body.get("garbage_collection");
		String expectedUuid = garbageCollection.get("uuid").textValue();

		uri = REST_SERVER.resolve("v2/registry/" + name + "/garbage-collection");
		RetryDelay retryDelay = new RetryDelay(Duration.ofSeconds(3), Duration.ofSeconds(30), 2);
		String actualUuid;
		do
		{
			// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_get_garbageCollection
			retryDelay.sleep();
			request = client.createRequest(uri).
				method(GET);
			serverResponse = client.send(request);
			actualUuid = switch (serverResponse.getStatus())
			{
				case OK_200 ->
				{
					contentResponse = (ContentResponse) serverResponse;
					body = client.getResponseBody(contentResponse);
					garbageCollection = body.get("garbage_collection");
					yield garbageCollection.get("uuid").textValue();
				}
				case NOT_FOUND_404 ->
				{
					// Operation complete
					yield "";
				}
				default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) +
					"\n" +
					"Request: " + client.toString(request));
			};
		}
		while (actualUuid.equals(expectedUuid));
	}

	/**
	 * Returns docker credentials for this registry.
	 *
	 * @param writeAccess {@code true} to grant write access. By default, credentials only grant read access.
	 * @param duration    the duration that the returned credentials will be valid for
	 * @return docker credentials for this registry
	 * @throws NullPointerException     if {@code duration} is null
	 * @throws IllegalArgumentException if {@code duration} is negative or zero
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public DockerCredentials getCredentials(boolean writeAccess, Duration duration)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(duration, "duration").isGreaterThan(Duration.ZERO);

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_get_dockerCredentials
		Request request = client.createRequest(REST_SERVER.resolve("v2/registry/docker-credentials")).
			param("expiry_seconds", String.valueOf(duration.toSeconds())).
			param("read_write", String.valueOf(writeAccess)).
			method(GET);
		Response serverResponse = client.send(request);
		if (serverResponse.getStatus() != OK_200)
		{
			throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		ContentResponse contentResponse = (ContentResponse) serverResponse;
		JsonNode body = client.getResponseBody(contentResponse);
		JsonNode authsNode = body.get("auths");
		JsonNode registryNode = authsNode.get(getHostname());
		String auth = registryNode.get("auth").textValue();
		String decoded = new String(Base64.getDecoder().decode(auth));
		String[] credentialTokens = decoded.split(":");
		return new DockerCredentials(credentialTokens[0], credentialTokens[1]);
	}

	/**
	 * Returns all the repositories in this registry.
	 *
	 * @return an empty set if no match is found
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Set<DockerRepository> getRepositories()
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_list_repositoriesV2
		URI uri = REST_SERVER.resolve("v2/registry/" + name + "/repositoriesV2");
		return client.getElements(uri, Map.of(), body ->
		{
			Set<DockerRepository> repositories = new HashSet<>();
			for (JsonNode repository : body.get("repositories"))
			{
				String actualName = repository.get("name").textValue();
				if (actualName.equals(name))
					repositories.add(DockerRepository.getByJson(client, this, repository));
			}
			return repositories;
		});
	}

	/**
	 * Returns the first repository that matches a predicate.
	 *
	 * @param predicate the predicate
	 * @return null if no match is found
	 * @throws NullPointerException  if {@code predicate} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public DockerRepository getRepositoryByPredicate(Predicate<DockerRepository> predicate)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_list_repositoriesV2
		URI uri = REST_SERVER.resolve("v2/registry/" + name + "/repositoriesV2");
		return client.getElement(uri, Map.of(), body ->
		{
			for (JsonNode repository : body.get("repositories"))
			{
				DockerRepository candidate = DockerRepository.getByJson(client, this, repository);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	@Override
	public int hashCode()
	{
		return name.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof DockerRegistry other && other.name.equals(name);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DockerRegistry.class).
			add("hostname", getHostname()).
			add("name", getName()).
			toString();
	}
}