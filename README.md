[![Maven Central](https://maven-badges.sml.io/maven-central/io.github.cowwoc.digitalocean/digitalocean/badge.svg)](https://search.maven.org/search?q=g:io.github.cowwoc.digitalocean)
[![build-status](https://github.com/cowwoc/digitalocean/workflows/Build/badge.svg)](https://github.com/cowwoc/digitalocean/actions/?query=workflow%3Abuild)

# <img src="docs/logo.svg" width=64 height=64 alt="logo"> DigitalOcean Java Client

[![API](https://img.shields.io/badge/api_docs-5B45D5.svg)](https://cowwoc.github.io/digitalocean/0.13/)
[![Changelog](https://img.shields.io/badge/changelog-A345D5.svg)](docs/changelog.md)

A Java client for the [DigitalOcean](https://www.digitalocean.com/) cloud platform.

To get started, add this Maven dependency:

```xml

<dependency>
  <groupId>io.github.cowwoc.digitalocean</groupId>
  <artifactId>digitalocean</artifactId>
  <version>0.13</version>
</dependency>
```

## Example

```java
import io.github.cowwoc.digitalocean.compute.client.ComputeClient;
import io.github.cowwoc.digitalocean.compute.resource.Droplet;
import io.github.cowwoc.digitalocean.compute.resource.DropletImage;
import io.github.cowwoc.digitalocean.compute.resource.DropletType;
import io.github.cowwoc.digitalocean.core.util.Configuration;
import io.github.cowwoc.digitalocean.network.resource.Region;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;

class Example
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
}
```

## Getting Started

See the [API documentation](https://cowwoc.github.io/digitalocean/0.13/) for more details.

## Licenses

* This library is distributed under the terms of the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
* See [Third party licenses](docs/3rd-party-licenses.md) for the licenses of dependencies