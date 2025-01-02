[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.cowwoc.digitalocean/digitalocean/badge.svg)](https://search.maven.org/search?q=g:com.github.cowwoc.digitalocean)
[![build-status](../../workflows/Build/badge.svg)](../../actions?query=workflow%3Abuild)

# <img src="docs/logo.svg" width=64 height=64 alt="logo"> DigitalOcean Java Client

[![API](https://img.shields.io/badge/api_docs-5B45D5.svg)](https://cowwoc.github.io/digitalocean/0.7/)
[![Changelog](https://img.shields.io/badge/changelog-A345D5.svg)](docs/changelog.md)

A Java client for the [DigitalOcean](https://www.digitalocean.com/) cloud platform.

To get started, add this Maven dependency:

```xml

<dependency>
  <groupId>com.github.cowwoc.digitalocean</groupId>
  <artifactId>digitalocean</artifactId>
  <version>0.7</version>
</dependency>
```

## Example

```java
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.exception.PermissionDeniedException;
import com.github.cowwoc.digitalocean.resource.Droplet;
import com.github.cowwoc.digitalocean.resource.DropletImage;
import com.github.cowwoc.digitalocean.resource.DropletType;
import com.github.cowwoc.digitalocean.resource.Vpc;
import com.github.cowwoc.digitalocean.resource.Zone;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

class Example
{
	public static void main(String[] args)
		throws PermissionDeniedException, IOException, TimeoutException, InterruptedException
	{
		try (DigitalOceanClient client = DigitalOceanClient.using("MY_ACCESS_TOKEN"))
		{
			DropletImage image = DropletImage.getBySlug(client, "debian-12-x64");
			DropletType type = DropletType.BASIC_1_VCPU_2GB_RAM_50GB_DISK;
			Vpc vpc = Vpc.getDefault(client, Zone.NYC3);
			Droplet droplet = Droplet.creator(client, "Node123", type, image, vpc).create();
			while (droplet.getAddresses().isEmpty())
				Thread.sleep(1000);
			System.out.println("The droplet's address is: " + droplet.getAddresses().getFirst());
		}
	}
}
```

## Getting Started

See the [API documentation](https://cowwoc.github.io/digitalocean/0.7/) for more details.

## Licenses

* This library is licensed under the [Apache License, Version 2.0](LICENSE)
* See [Third party licenses](LICENSE-3RD-PARTY.md) for the licenses of the dependencies