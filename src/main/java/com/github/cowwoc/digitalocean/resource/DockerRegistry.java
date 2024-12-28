package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import com.github.cowwoc.digitalocean.internal.util.DigitalOceans;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import com.github.cowwoc.digitalocean.scope.DigitalOceanScope;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.util.DigitalOceans.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

/**
 * A registry of docker repositories.
 */
public final class DockerRegistry
{
	/**
	 * Returns the account's docker registry.
	 *
	 * @param scope the client configuration
	 * @return null if the account does not have a container registry
	 * @throws NullPointerException if {@code scope} is null
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public static DockerRegistry get(
		DigitalOceanScope scope) throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_get
		@SuppressWarnings("PMD.CloseResource")
		HttpClient client = scope.getHttpClient();
		ClientRequests clientRequests = scope.getClientRequests();
		String uri = REST_SERVER + "/v2/registry";
		ContentResponse serverResponse = clientRequests.send(client.newRequest(uri).
			headers(headers -> headers.put("Content-Type", "application/json").
				put("Authorization", "Bearer " + scope.getDigitalOceanToken())).
			method(GET));
		requireThat(serverResponse.getStatus(), "responseCode").isEqualTo(OK_200);
		JsonNode body = scope.getObjectMapper().readTree(serverResponse.getContentAsString());
		JsonNode registryNode = body.get("registry");
		return getByJson(scope, registryNode);
	}

	/**
	 * Returns the docker registry.
	 *
	 * @param scope the client configuration
	 * @param json  the JSON representation of the registry
	 * @return the registry
	 * @throws NullPointerException if any of the arguments are null
	 * @see DockerRegistry#get(DigitalOceanScope)
	 */
	private static DockerRegistry getByJson(DigitalOceanScope scope, JsonNode json)
	{
		String name = json.get("name").textValue();
		return new DockerRegistry(scope, name);
	}

	private final DigitalOceanScope scope;
	private final String name;

	/**
	 * Creates a snapshot of the container registry's state.
	 *
	 * @param scope the client configuration
	 * @param name  the name of the registry
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 */
	private DockerRegistry(DigitalOceanScope scope, String name)
	{
		requireThat(scope, "scope").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		this.scope = scope;
		this.name = name;
	}

	/**
	 * Returns the image repositories in the registry.
	 *
	 * @return the repositories
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public List<DockerRepository> getRepositories() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_list_repositoriesV2
		String uri = REST_SERVER + "/v2/registry/" + name + "/repositoriesV2";
		return DigitalOceans.getElements(scope, uri, Map.of(), body ->
		{
			List<DockerRepository> repositories = new ArrayList<>();
			for (JsonNode repository : body.get("repositories"))
			{
				String actualName = repository.get("name").textValue();
				if (actualName.equals(name))
					repositories.add(DockerRepository.getByJson(scope, this, repository));
			}
			return repositories;
		});
	}

	/**
	 * Looks up an image repository by its name.
	 *
	 * @param name the name of the repository
	 * @return null if the repository was not found
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public DockerRepository getRepositoryByName(String name)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_list_repositoriesV2
		String uri = REST_SERVER + "/v2/registry/" + this.name + "/repositoriesV2";
		return DigitalOceans.getElement(scope, uri, Map.of(), body ->
		{
			for (JsonNode repository : body.get("repositories"))
			{
				String actualName = repository.get("name").textValue();
				if (actualName.equals(name))
					return DockerRepository.getByJson(scope, this, repository);
			}
			return null;
		});
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
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public void deleteUnusedLayers() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_run_garbageCollection
		@SuppressWarnings("PMD.CloseResource")
		HttpClient client = scope.getHttpClient();
		ClientRequests clientRequests = scope.getClientRequests();
		String uri = REST_SERVER + "/v2/registry/" + name + "/garbage-collection";
		Request request = client.newRequest(uri).
			headers(headers -> headers.put("Content-Type", "application/json").
				put("Authorization", "Bearer " + scope.getDigitalOceanToken())).
			method(POST);
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				// success
			}
			default -> throw new AssertionError(
				"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
					"Request: " + clientRequests.toString(request));
		}
		JsonNode body = DigitalOceans.getResponseBody(scope, serverResponse);
		JsonNode garbageCollection = body.get("garbage_collection");
		String expectedUuid = garbageCollection.get("uuid").textValue();

		String actualUuid;
		do
		{
			// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_get_garbageCollection
			Thread.sleep(5000);
			uri = REST_SERVER + "/v2/registry/" + name + "/garbage-collection";
			request = client.newRequest(uri).
				headers(headers -> headers.put("Content-Type", "application/json").
					put("Authorization", "Bearer " + scope.getDigitalOceanToken())).
				method(GET);
			serverResponse = clientRequests.send(request);
			actualUuid = switch (serverResponse.getStatus())
			{
				case OK_200 ->
				{
					body = DigitalOceans.getResponseBody(scope, serverResponse);
					garbageCollection = body.get("garbage_collection");
					yield garbageCollection.get("uuid").textValue();
				}
				case NOT_FOUND_404 ->
				{
					// Operation complete
					yield "";
				}
				default -> throw new AssertionError(
					"Unexpected response: " + clientRequests.toString(serverResponse) +
						"\n" +
						"Request: " + clientRequests.toString(request));
			};
		}
		while (actualUuid.equals(expectedUuid));
	}

	/**
	 * Returns docker credentials for this registry.
	 *
	 * @param write    {@code true} to grant write access. By default, credentials only grant read access.
	 * @param duration the duration that the returned credentials will be valid for
	 * @return docker credentials for this registry
	 * @throws NullPointerException     if {@code duration} is null
	 * @throws IllegalArgumentException if {@code duration} is negative or zero
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public DockerCredentials getCredentials(boolean write, Duration duration)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(duration, "duration").isGreaterThan(Duration.ZERO);

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_get_dockerCredentials
		@SuppressWarnings("PMD.CloseResource")
		HttpClient client = scope.getHttpClient();
		ClientRequests clientRequests = scope.getClientRequests();
		String uri = REST_SERVER + "/v2/registry/docker-credentials";
		Request request = client.newRequest(uri).
			param("expiry_seconds", String.valueOf(Duration.ofMinutes(5).toSeconds())).
			param("read_write", String.valueOf(write)).
			headers(headers -> headers.put("Content-Type", "application/json").
				put("Authorization", "Bearer " + scope.getDigitalOceanToken())).
			method(GET);
		ContentResponse serverResponse = clientRequests.send(request);
		if (serverResponse.getStatus() != OK_200)
		{
			throw new AssertionError("Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
				"Request: " + clientRequests.toString(request));
		}
		JsonNode body = DigitalOceans.getResponseBody(scope, serverResponse);
		JsonNode authsNode = body.get("auths");
		JsonNode registryNode = authsNode.get(getHostname());
		String auth = registryNode.get("auth").textValue();
		String decoded = new String(Base64.getDecoder().decode(auth));
		String[] credentialTokens = decoded.split(":");
		return new DockerCredentials(credentialTokens[0], credentialTokens[1]);
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