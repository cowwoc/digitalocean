package io.github.cowwoc.digitalocean.test;

import io.github.cowwoc.digitalocean.compute.client.ComputeClient;
import io.github.cowwoc.digitalocean.compute.resource.ComputeDropletType;
import io.github.cowwoc.digitalocean.compute.resource.DropletImage;
import io.github.cowwoc.digitalocean.core.client.Client;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.util.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

/**
 * Common functionality shared by all integration tests.
 */
public abstract class AbstractIT
{
	protected final Configuration configuration;

	/**
	 * Creates a new AbstractIT.
	 *
	 * @throws IOException if an I/O error occurs while loading the test configuration
	 */
	protected AbstractIT() throws IOException
	{
		this.configuration = Configuration.fromPath(Path.of("test.properties"));
	}

	/**
	 * Sets the access token used for authentication.
	 */
	protected void login(Client client)
	{
		String accessToken = configuration.getString("ACCESS_TOKEN");
		client.login(accessToken);
	}

	protected DropletImage getSmallestImage(ComputeClient client) throws IOException, InterruptedException
	{
		return client.getDropletImages().stream().
			min(Comparator.comparing(DropletImage::getMinDiskSizeInGiB)).
			orElseThrow();
	}

	/**
	 * Returns the cheapest droplet type that is compatible with a DropletImage.
	 *
	 * @param client the client configuration
	 * @param image  the droplet image
	 * @return the droplet type
	 */
	protected ComputeDropletType getCheapestTypeCompatibleWithImage(ComputeClient client, DropletImage image)
		throws IOException, InterruptedException
	{
		return client.getDropletTypes().stream().filter(candidate ->
				!Collections.disjoint(candidate.getRegionIds(), image.getRegionIds()) &&
					candidate.getDiskInGiB() >= image.getMinDiskSizeInGiB()).
			min(Comparator.comparing(ComputeDropletType::getCostPerHour)).
			orElseThrow();
	}

	/**
	 * Returns the regions that are common to two sets.
	 *
	 * @param first  the first set of regions
	 * @param second the second set of regions
	 * @return the regions that are common to both the sets
	 */
	protected Set<RegionId> getCommonRegions(Collection<RegionId> first, Collection<RegionId> second)
	{
		Set<RegionId> regions = EnumSet.copyOf(first);
		regions.retainAll(second);
		return regions;
	}

	/**
	 * Returns the cheapest droplet type.
	 *
	 * @param client the client configuration
	 * @return the droplet type
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	protected ComputeDropletType getCheapestType(ComputeClient client)
		throws IOException, InterruptedException
	{
		return client.getDropletTypes().stream().
			filter(type -> !type.isContracted()).
			min(Comparator.comparing(ComputeDropletType::getCostPerHour)).
			orElseThrow();
	}

	/**
	 * Returns a droplet image that is too large to fit on the specified droplet type.
	 *
	 * @param client the client configuration
	 * @param type   a droplet type
	 * @return the droplet image
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	protected DropletImage getImageTooLarge(ComputeClient client, ComputeDropletType type)
		throws IOException, InterruptedException
	{
		return client.getDropletImages().stream().filter(candidate ->
				candidate.getMinDiskSizeInGiB() > type.getDiskInGiB()).
			findAny().orElseThrow();
	}
}