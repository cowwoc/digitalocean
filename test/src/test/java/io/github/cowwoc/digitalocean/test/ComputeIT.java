package io.github.cowwoc.digitalocean.test;

import io.github.cowwoc.digitalocean.compute.client.ComputeClient;
import io.github.cowwoc.digitalocean.compute.resource.Droplet;
import io.github.cowwoc.digitalocean.compute.resource.DropletImage;
import io.github.cowwoc.digitalocean.compute.resource.DropletType;
import io.github.cowwoc.digitalocean.core.util.Configuration;
import io.github.cowwoc.digitalocean.network.resource.Region;
import io.github.cowwoc.digitalocean.network.resource.Region.Id;
import io.github.cowwoc.digitalocean.test.util.Tests;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

public final class ComputeIT
{
	private final Configuration configuration;

	/**
	 * Creates a new ComputeIT.
	 *
	 * @throws IOException if an I/O error occurs while loading the test configuration
	 */
	public ComputeIT() throws IOException
	{
		this.configuration = Configuration.fromPath(Path.of("test.properties"));
	}

	/**
	 * Sets the access token used for authentication.
	 */
	private void login(ComputeClient client)
	{
		String accessToken = configuration.getString("ACCESS_TOKEN");
		client.login(accessToken);
	}

	@Test
	public void createDroplet() throws IOException, InterruptedException
	{
		try (ComputeClient client = ComputeClient.build())
		{
			login(client);

			DropletImage image = getSmallestImage(client);
			DropletType type = getCheapestTypeCompatibleWithImage(client, image);
			Region.Id region = getCommonRegion(image.getRegionIds(), type.getRegionIds());
			Droplet droplet = client.createDroplet(Tests.getCallerName(), type.getId(), image.getId()).
				regionId(region).
				apply();
			droplet.destroy();
		}
	}

	private DropletImage getSmallestImage(ComputeClient client) throws IOException, InterruptedException
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
	private DropletType getCheapestTypeCompatibleWithImage(ComputeClient client, DropletImage image)
		throws IOException, InterruptedException
	{
		return client.getDropletTypes().stream().filter(candidate ->
				!Collections.disjoint(candidate.getRegionIds(), image.getRegionIds()) &&
					candidate.getDiskInGiB() >= image.getMinDiskSizeInGiB()).
			min(Comparator.comparing(DropletType::getCostPerHour)).
			orElseThrow();
	}

	private Region.Id getCommonRegion(Set<Region.Id> first, Set<Region.Id> second)
	{
		Set<Id> regions = EnumSet.copyOf(first);
		regions.retainAll(second);
		return regions.iterator().next();
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void createDropletDiskSmallerThanImage() throws IOException, InterruptedException
	{
		try (ComputeClient client = ComputeClient.build())
		{
			login(client);

			DropletType type = getCheapestType(client);
			DropletImage image = getImageTooLarge(client, type);
			Region.Id region = getCommonRegion(image.getRegionIds(), type.getRegionIds());
			Droplet droplet = client.createDroplet(Tests.getCallerName(), type.getId(), image.getId()).
				regionId(region).
				apply();
			droplet.destroy();
		}
	}

	/**
	 * Returns the cheapest droplet type.
	 *
	 * @param client the client configuration
	 * @return the droplet type
	 */
	private DropletType getCheapestType(ComputeClient client)
		throws IOException, InterruptedException
	{
		return client.getDropletTypes().stream().
			min(Comparator.comparing(DropletType::getCostPerHour)).
			orElseThrow();
	}

	/**
	 * Returns a droplet image that is too large to fit on the specified droplet type.
	 *
	 * @param client the client configuration
	 * @param type   a droplet type
	 * @return the droplet image
	 */
	private DropletImage getImageTooLarge(ComputeClient client, DropletType type)
		throws IOException, InterruptedException
	{
		return client.getDropletImages().stream().filter(candidate ->
				candidate.getMinDiskSizeInGiB() > type.getDiskInGiB()).
			findAny().orElseThrow();
	}
}