package com.github.cowwoc.digitalocean.resource;

import com.github.cowwoc.digitalocean.internal.util.DigitalOceans;
import com.github.cowwoc.digitalocean.resource.DockerImage.Layer;
import com.github.cowwoc.digitalocean.scope.DigitalOceanScope;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.util.DigitalOceans.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A repository of docker images.
 */
public final class DockerRepository
{
	/**
	 * @param scope    the client configuration
	 * @param registry the enclosing registry
	 * @param json     the JSON representation of the repository
	 * @return the repository
	 * @throws NullPointerException if any of the arguments are null
	 */
	static DockerRepository getByJson(DigitalOceanScope scope, DockerRegistry registry, JsonNode json)
	{
		String name = json.get("name").textValue();
		return new DockerRepository(scope, registry, name);
	}

	private final DigitalOceanScope scope;
	private final DockerRegistry registry;
	private final String name;

	/**
	 * Creates a snapshot of a container repository's state.
	 *
	 * @param scope    the client configuration
	 * @param name     the name of the repository
	 * @param registry the enclosing registry
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 */
	private DockerRepository(DigitalOceanScope scope, DockerRegistry registry, String name)
	{
		requireThat(scope, "scope").isNotNull();
		requireThat(registry, "registry").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		this.scope = scope;
		this.registry = registry;
		this.name = name;
	}

	/**
	 * Returns the images in the repository.
	 *
	 * @return the images
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public List<DockerImage> getImages() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_list_repositoryManifests
		String uri = REST_SERVER + "/v2/registry/" + registry.getName() + "/repositories/" + name + "/digests";
		return DigitalOceans.getElements(scope, uri, Map.of(), body ->
		{
			List<DockerImage> images = new ArrayList<>();
			for (JsonNode manifest : body.get("manifests"))
				images.add(DockerImage.getByJson(scope, this, manifest));
			return images;
		});
	}

	/**
	 * Deletes all dangling (untagged) images in the registry.
	 *
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public void deleteDanglingImages() throws IOException, TimeoutException, InterruptedException
	{
		List<DockerImage> imagesToDelete = getImages();
		Deque<Layer> layersToKeep = new ArrayDeque<>();
		for (DockerImage image : imagesToDelete)
		{
			if (!image.getTags().isEmpty())
				layersToKeep.addAll(image.getLayers());
		}

		// Keep all the layers that tagged images transitively depend on
		Set<Layer> processedLayers = new HashSet<>();
		while (true)
		{
			Layer layer = layersToKeep.poll();
			if (layer == null)
				break;
			if (processedLayers.add(layer))
			{
				for (DockerImage image : new ArrayList<>(imagesToDelete))
				{
					if (image.getDigest().equals(layer.digest()))
					{
						imagesToDelete.remove(image);
						layersToKeep.addAll(image.getLayers());
					}
				}
			}
		}

		for (DockerImage image : imagesToDelete)
			image.destroy(this);
		// This is a slow operation. Commenting it out for now.
//		registry.deleteUnusedLayers();
	}

	/**
	 * Returns the enclosing docker registry.
	 *
	 * @return the docker registry
	 */
	public DockerRegistry getRegistry()
	{
		return registry;
	}

	/**
	 * Returns the repository's name.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the fully qualified name of the repository (e.g.,
	 * {@code registry.digitalocean.com/nasa/rocket-ship}).
	 *
	 * @return the fully qualified name
	 */
	public String getFullyQualifiedName()
	{
		return registry.getFullyQualifiedName() + "/" + name;
	}

	/**
	 * Looks up a docker image by its tag.
	 *
	 * @param tags the image's tags
	 * @return null if no match was found
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public DockerImage getImageByTags(Set<String> tags)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/registry_list_repositoryManifests
		String uri = REST_SERVER + "/v2/registry/" + registry.getName() + "/repositories/" + name + "/digests";
		return DigitalOceans.getElement(scope, uri, Map.of(), body ->
		{
			for (JsonNode manifest : body.get("manifests"))
			{
				Set<String> actualTags = new HashSet<>();
				for (JsonNode tag : manifest.get("tags"))
					actualTags.add(tag.textValue());
				if (actualTags.equals(tags))
					return DockerImage.getByJson(scope, this, manifest);
			}
			return null;
		});
	}
}