package io.github.cowwoc.digitalocean.registry.internal.parser;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cowwoc.digitalocean.registry.internal.client.DefaultRegistryClient;
import io.github.cowwoc.digitalocean.registry.internal.resource.DefaultContainerImage;
import io.github.cowwoc.digitalocean.registry.internal.resource.DefaultRegistry;
import io.github.cowwoc.digitalocean.registry.internal.resource.DefaultRepository;
import io.github.cowwoc.digitalocean.registry.resource.ContainerImage;
import io.github.cowwoc.digitalocean.registry.resource.ContainerImage.Id;
import io.github.cowwoc.digitalocean.registry.resource.ContainerImage.Layer;
import io.github.cowwoc.digitalocean.registry.resource.Registry;
import io.github.cowwoc.digitalocean.registry.resource.Repository;

import java.util.HashSet;
import java.util.Set;

/**
 * Parses server responses.
 */
public final class RegistryParser
{
	private final DefaultRegistryClient client;

	/**
	 * Creates a parser.
	 *
	 * @param client the client configuration
	 */
	public RegistryParser(DefaultRegistryClient client)
	{
		assert client != null;
		this.client = client;
	}

	/**
	 * Parses the JSON representation of a registry.
	 *
	 * @param json the JSON representation
	 * @return the container registry
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public Registry getRegistry(JsonNode json)
	{
		String name = json.get("name").textValue();
		return new DefaultRegistry(client, name);
	}

	/**
	 * Parses the JSON representation of a repository.
	 *
	 * @param registry the enclosing registry
	 * @param json     the JSON representation
	 * @return the container repository
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public Repository getRepository(Registry registry, JsonNode json)
	{
		String name = json.get("name").textValue();
		return new DefaultRepository(client, registry, name);
	}

	/**
	 * Parses the JSON representation of an image.
	 *
	 * @param repository the enclosing docker repository
	 * @param json       the JSON representation
	 * @return the docker image
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public ContainerImage getImage(Repository repository, JsonNode json)
	{
		Id id = ContainerImage.id(json.get("id").textValue());
		Set<String> tags = new HashSet<>();
		for (JsonNode tag : json.get("tags"))
			tags.add(tag.textValue());

		Set<Layer> layers = new HashSet<>();
		for (JsonNode node : json.get("blobs"))
		{
			Id layerDigest = ContainerImage.id(node.get("id").textValue());
			layers.add(new Layer(layerDigest));
		}
		return new DefaultContainerImage(repository, id, tags, layers);
	}
}