package io.github.cowwoc.digitalocean.test;

import io.github.cowwoc.digitalocean.compute.client.ComputeClient;
import io.github.cowwoc.digitalocean.compute.resource.ComputeDropletType;
import io.github.cowwoc.digitalocean.compute.resource.Droplet;
import io.github.cowwoc.digitalocean.compute.resource.DropletImage;
import io.github.cowwoc.digitalocean.core.util.Configuration;
import io.github.cowwoc.digitalocean.network.resource.Region;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;

@SuppressWarnings({"BusyWait", "PMD.SystemPrintln"})
public final class Readme
{
	public static void main(String[] args) throws IOException, InterruptedException
	{
		try (ComputeClient client = ComputeClient.build())
		{
			Configuration configuration = Configuration.fromPath(Path.of("example.properties"));
			String accessToken = configuration.getString("ACCESS_TOKEN");
			client.login(accessToken);

			DropletImage image = client.getDropletImage("debian-12-x64");
			Region region = client.getRegions(true).getFirst();

			// Get the least expensive droplet type with at least 2 GiB of memory
			ComputeDropletType dropletType = client.getDropletTypes().stream().filter(type ->
					type.getRegionIds().contains(region.getId()) && type.getRamInMiB() >= 2 * 1024).
				min(Comparator.comparing(ComputeDropletType::getCostPerHour)).orElseThrow();

			Droplet droplet = client.createDroplet("Node123", dropletType.getId(), image.getId()).apply();
			while (droplet.getAddresses().isEmpty())
			{
				Thread.sleep(1000);
				droplet = droplet.reload();
			}
			System.out.println("The droplet's address is: " + droplet.getAddresses().iterator().next());
		}
	}

	private Readme()
	{
	}
}