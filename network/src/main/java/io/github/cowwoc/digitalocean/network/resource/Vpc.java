package io.github.cowwoc.digitalocean.network.resource;

import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.id.VpcId;

/**
 * Represents a private network, also known as a Virtual Private Cloud (VPC).
 * <p>
 * VPCs are region-specific, meaning they cannot secure communication between droplets located in different
 * regions.
 *
 * @see <a href="https://youtu.be/nbo5HrmZjXo?si=5Gf4hbticjw6nwph&t=2140">Configure a VPN to Extend a VPC
 * 	Across Regions</a>
 * @see <a
 * 	href="https://www.digitalocean.com/community/developer-center/connect-digitalocean-droplets-across-regions">
 * 	Connect DigitalOcean Droplets Across Regions</a>
 * @see <a href="https://www.digitalocean.com/blog/vpc-peering-ga">VPC Peering</a>
 */
public interface Vpc
{
	/**
	 * Returns the VPC's ID.
	 *
	 * @return the id
	 */
	VpcId getId();

	/**
	 * Returns the region that the VPC is in.
	 *
	 * @return the region
	 */
	RegionId getRegionId();
}