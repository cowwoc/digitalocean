package io.github.cowwoc.digitalocean.test;

import io.github.cowwoc.digitalocean.compute.client.ComputeClient;
import io.github.cowwoc.digitalocean.compute.resource.ComputeDropletType;
import io.github.cowwoc.digitalocean.compute.resource.Droplet;
import io.github.cowwoc.digitalocean.compute.resource.DropletImage;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.test.util.Tests;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;

import java.io.IOException;

public final class ComputeIT extends AbstractIT
{
	/**
	 * Creates a new ComputeIT.
	 *
	 * @throws IOException if an I/O error occurs while loading the test configuration
	 */
	public ComputeIT() throws IOException
	{
	}

	@Test
	public void createDroplet() throws IOException, InterruptedException
	{
		try (ComputeClient client = ComputeClient.build())
		{
			login(client);

			DropletImage image = getSmallestImage(client);
			ComputeDropletType type = getCheapestTypeCompatibleWithImage(client, image);
			RegionId regionId = getCommonRegions(image.getRegionIds(), type.getRegionIds()).iterator().next();
			Droplet droplet = client.createDroplet(Tests.getCallerName(), type.getId(), image.getId()).
				regionId(regionId).
				apply();
			droplet.destroy();
		}
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void createDropletDiskSmallerThanImage() throws IOException, InterruptedException
	{
		try (ComputeClient client = ComputeClient.build())
		{
			login(client);

			ComputeDropletType type = getCheapestType(client);
			DropletImage image = getImageTooLarge(client, type);
			RegionId regionId = getCommonRegions(image.getRegionIds(), type.getRegionIds()).iterator().next();
			Droplet droplet = client.createDroplet(Tests.getCallerName(), type.getId(), image.getId()).
				regionId(regionId).
				apply();
			droplet.destroy();
		}
	}

	@Test
	public void createDuplicateDroplet() throws IOException, InterruptedException
	{
		try (ComputeClient client = ComputeClient.build())
		{
			login(client);

			DropletImage image = getSmallestImage(client);
			ComputeDropletType type = getCheapestTypeCompatibleWithImage(client, image);
			RegionId regionId = getCommonRegions(image.getRegionIds(), type.getRegionIds()).iterator().next();
			Droplet droplet1 = client.createDroplet(Tests.getCallerName(), type.getId(), image.getId()).
				regionId(regionId).
				apply();
			try
			{
				Droplet droplet2 = client.createDroplet(Tests.getCallerName(), type.getId(), image.getId()).
					regionId(regionId).
					apply();
				droplet2.destroy();
			}
			finally
			{
				droplet1.destroy();
			}
		}
	}

	@AfterSuite
	public void destroyDroplets() throws IOException, InterruptedException
	{
		try (ComputeClient client = ComputeClient.build())
		{
			login(client);

			for (Droplet droplet : client.getDroplets())
				droplet.destroy();
		}
	}
}