package io.github.cowwoc.digitalocean.compute.resource;

import io.github.cowwoc.digitalocean.core.id.DropletImageId;
import io.github.cowwoc.digitalocean.core.id.RegionId;

import java.time.Instant;
import java.util.Set;

/**
 * The system image that was used to boot a droplet.
 */
public interface DropletImage
{
	/**
	 * Returns the ID of the image.
	 *
	 * @return the ID of the image
	 */
	DropletImageId getId();

	/**
	 * Returns the slug of the image.
	 *
	 * @return the slug of the image
	 */
	String getSlug();

	/**
	 * Returns the name of the image.
	 *
	 * @return the name
	 */
	String getName();

	/**
	 * Returns the OS distribution of the image.
	 *
	 * @return the OS distribution
	 */
	String getDistribution();

	/**
	 * Determines if the image is available for public use.
	 *
	 * @return {@code true} if the image is available for public use
	 */
	boolean isPublic();

	/**
	 * Returns the regions that the image is available in.
	 *
	 * @return the regions
	 */
	Set<RegionId> getRegionIds();

	/**
	 * Returns the type of the image.
	 *
	 * @return the type
	 */
	Type getType();

	/**
	 * Returns the minimum disk size in GiB required to use this image.
	 *
	 * @return the minimum disk size
	 */
	int getMinDiskSizeInGiB();

	/**
	 * Returns the size of the image in GiB.
	 *
	 * @return the size
	 */
	float getSizeInGiB();

	/**
	 * Returns a description of the image.
	 *
	 * @return the description
	 */
	String getDescription();

	/**
	 * Returns the tags that are associated with the image.
	 *
	 * @return the tags
	 */
	Set<String> getTags();

	/**
	 * Returns the status of the image.
	 *
	 * @return the status
	 */
	Status getStatus();

	/**
	 * Returns an explanation of why the image cannot be used.
	 *
	 * @return an explanation of the failure
	 */
	String getErrorMessage();

	/**
	 * Returns the time the image was created at.
	 *
	 * @return the time
	 */
	Instant getCreatedAt();

	/**
	 * The image of the image.
	 */
	enum Type
	{
		/**
		 * 1-click apps.
		 */
		APPLICATION,
		/**
		 * Base OS image.
		 */
		BASE,
		/**
		 * User-generated droplet snapshot.
		 */
		SNAPSHOT,
		/**
		 * Automatically created droplet backup.
		 */
		BACKUP,
		/**
		 * User-provided virtual machine image.
		 */
		CUSTOM,
		/**
		 * Image used by DigitalOcean managed resources (e.g. DOKS worker nodes).
		 */
		ADMIN
	}

	/**
	 * The status of the image.
	 */
	enum Status
	{
		// Per DigitalOcean tech support, the "pending" value is never returned.
		/**
		 * The image is being processed by DigitalOcean.
		 */
		NEW,
		/**
		 * The image is available for use.
		 */
		AVAILABLE,
		/**
		 * The image is no longer actively supported or maintained.
		 */
		RETIRED,
		/**
		 * The image has been deleted and is no longer available for use.
		 */
		DELETED
	}
}