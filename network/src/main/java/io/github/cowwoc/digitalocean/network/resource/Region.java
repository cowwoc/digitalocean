package io.github.cowwoc.digitalocean.network.resource;

import io.github.cowwoc.digitalocean.core.id.ComputeDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.RegionId;

import java.util.Set;

/**
 * A geographical region that contains one or more <a
 * href="https://docs.digitalocean.com/platform/regional-availability/">datacenters</a>.
 */
public interface Region
{
	/**
	 * Returns this region's ID.
	 *
	 * @return the ID
	 */
	RegionId getId();

	/**
	 * Returns the region's name.
	 *
	 * @return the name
	 */
	String getName();

	/**
	 * Returns the features that are available in this region.
	 *
	 * @return the features
	 */
	Set<Feature> getFeatures();

	/**
	 * Determines if new droplets can be created in this region.
	 *
	 * @return {@code true} if new droplets can be created
	 */
	boolean canCreateDroplets();

	/**
	 * Returns the droplet types that can be created in this region.
	 *
	 * @return the droplet types
	 */
	Set<ComputeDropletTypeId> getDropletTypeIds();

	/**
	 * Region features.
	 */
	enum Feature
	{
		/**
		 * System-level backups at weekly or daily intervals.
		 */
		BACKUPS,
		/**
		 * Network communication over IPv6.
		 */
		IPV6,
		/**
		 * The metadata service, allowing droplets to query metadata from within the droplet itself.
		 */
		METADATA,
		/**
		 * Ability to install a metrics agent on droplets.
		 */
		INSTALL_AGENT,
		/**
		 * Ability to use the Spaces and Block Storage Volume services.
		 */
		STORAGE,
		/**
		 * Ability to use custom droplet images within this region.
		 */
		IMAGE_TRANSFER
	}
}