package io.github.cowwoc.digitalocean.registry.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cowwoc.digitalocean.core.internal.client.AbstractInternalClient;
import io.github.cowwoc.digitalocean.registry.client.RegistryClient;
import io.github.cowwoc.digitalocean.registry.internal.parser.RegistryParser;
import io.github.cowwoc.digitalocean.registry.internal.resource.DefaultRepository;
import io.github.cowwoc.digitalocean.registry.resource.Registry;
import io.github.cowwoc.digitalocean.registry.resource.Repository;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.http.HttpStatus.UNAUTHORIZED_401;

public class DefaultRegistryClient extends AbstractInternalClient
	implements RegistryClient
{
	private final RegistryParser parser = new RegistryParser(this);

	/**
	 * Creates a new DefaultRegistryClient.
	 */
	public DefaultRegistryClient()
	{
	}

	/**
	 * Returns the parser.
	 *
	 * @return the parser
	 */
	public RegistryParser getParser()
	{
		return parser;
	}

	@Override
	public Registry getRegistry() throws IOException, InterruptedException, AccessDeniedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Container-Registry/operation/registry_get
		Request request = createRequest(REST_SERVER.resolve("v2/registry")).
			method(GET);
		Response serverResponse = send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			case UNAUTHORIZED_401 ->
			{
				ContentResponse contentResponse = (ContentResponse) serverResponse;
				JsonNode json = getResponseBody(contentResponse);
				throw new AccessDeniedException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + toString(serverResponse) + "\n" +
				"Request: " + toString(request));
		}
		ContentResponse contentResponse = (ContentResponse) serverResponse;
		JsonNode body = getJsonMapper().readTree(contentResponse.getContentAsString());
		JsonNode registryNode = body.get("registry");
		return parser.getRegistry(registryNode);
	}

	@Override
	public List<Repository> getRepositories(Registry registry) throws IOException, InterruptedException
	{
		return getRepositories(registry, _ -> true);
	}

	@Override
	public List<Repository> getRepositories(Registry registry, Predicate<Repository> predicate)
		throws IOException, InterruptedException
	{
		requireThat(registry, "registry").isNotNull();
		requireThat(predicate, "predicate").isNotNull();

		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Container-Registry/operation/registry_list_repositoriesV2
		URI uri = REST_SERVER.resolve("v2/registry/" + registry.getName() + "/repositoriesV2");
		return getElements(uri, Map.of(), body ->
		{
			List<Repository> repositories = new ArrayList<>();
			for (JsonNode repository : body.get("repositories"))
			{
				String name = repository.get("name").textValue();
				Repository candidate = new DefaultRepository(this, registry, name);
				if (predicate.test(candidate))
					repositories.add(parser.getRepository(registry, repository));
			}
			return repositories;
		});
	}

	@Override
	public Repository getRepository(Registry registry, String name) throws IOException, InterruptedException
	{
		requireThat(registry, "registry").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Container-Registry/operation/registry_list_repositoriesV2
		URI uri = REST_SERVER.resolve("v2/registry/" + registry.getName() + "/repositoriesV2");
		return getElement(uri, Map.of(), body ->
		{
			for (JsonNode repository : body.get("repositories"))
			{
				String actualName = repository.get("name").textValue();
				if (actualName.equals(name))
					return parser.getRepository(registry, repository);
			}
			return null;
		});
	}

	@Override
	public Repository getRepository(Registry registry, Predicate<Repository> predicate)
		throws IOException, InterruptedException
	{
		requireThat(registry, "registry").isNotNull();

		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Container-Registry/operation/registry_list_repositoriesV2
		URI uri = REST_SERVER.resolve("v2/registry/" + registry.getName() + "/repositoriesV2");
		return getElement(uri, Map.of(), body ->
		{
			for (JsonNode repository : body.get("repositories"))
			{
				String actualName = repository.get("name").textValue();
				Repository candidate = parser.getRepository(registry, repository);
				if (actualName.equals(candidate.getName()))
					return candidate;
			}
			return null;
		});
	}
}