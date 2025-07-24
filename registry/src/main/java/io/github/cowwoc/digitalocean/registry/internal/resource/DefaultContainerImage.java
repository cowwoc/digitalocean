package io.github.cowwoc.digitalocean.registry.internal.resource;

import io.github.cowwoc.digitalocean.core.id.ContainerImageId;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;
import io.github.cowwoc.digitalocean.registry.resource.ContainerImage;
import io.github.cowwoc.digitalocean.registry.resource.Repository;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

public final class DefaultContainerImage implements ContainerImage
{
	private final Repository repository;
	private final ContainerImageId id;
	private final Set<String> tags;
	private final Set<Layer> layers;

	/**
	 * Creates a snapshot of the docker image's state.
	 *
	 * @param repository the docker repository that the image is in
	 * @param id         a value that uniquely identifies the image in the repository
	 * @param tags       the tags that are associated with the image
	 * @param layers     the layers that the image consists of
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} contains leading or trailing whitespace or is empty
	 */
	public DefaultContainerImage(Repository repository, ContainerImageId id, Set<String> tags,
		Set<Layer> layers)
	{
		requireThat(repository, "repository").isNotNull();
		requireThat(id, "id").isNotNull();

		this.repository = repository;
		this.id = id;
		this.tags = Set.copyOf(tags);
		this.layers = Set.copyOf(layers);
	}

	@Override
	public Repository getRepository()
	{
		return repository;
	}

	@Override
	public ContainerImageId getId()
	{
		return id;
	}

	@Override
	public Set<String> getTags()
	{
		return tags;
	}

	@Override
	public Set<Layer> getLayers()
	{
		return layers;
	}

	@Override
	public ContainerImage reload() throws IOException, InterruptedException
	{
		return repository.getImage(image -> image.getId().equals(id));
	}

	@Override
	public void destroy() throws IOException, InterruptedException
	{
		repository.destroyImage(id);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(repository, id, tags, layers);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof ContainerImage other && other.getRepository().equals(repository) &&
			other.getId().equals(id) && other.getTags().equals(tags) && other.getLayers().equals(layers);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DefaultContainerImage.class).
			add("repository", repository).
			add("id", id).
			add("tags", tags).
			add("layers", layers).
			toString();
	}
}