package io.github.cowwoc.digitalocean.database.resource;

import io.github.cowwoc.digitalocean.core.id.DatabaseDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.RegionId;

import java.util.Set;

/**
 * Droplet types for database resources.
 *
 * @see <a href="https://www.digitalocean.com/pricing/managed-databases">pricing</a>
 */
public interface DatabaseDropletType
{
	/**
	 * Returns the type's ID.
	 *
	 * @return the ID
	 */
	DatabaseDropletTypeId getId();

	/**
	 * Returns the amount of RAM allocated to this type, in MiB.
	 *
	 * @return the amount of RAM
	 */
	int getRamInMiB();

	/**
	 * Returns the number of virtual CPUs allocated to this type. Note that vCPUs are relative units specific to
	 * each vendor's infrastructure and hardware. A number of vCPUs is relative to other vCPUs by the same
	 * vendor, meaning that the allocation and performance are comparable within the same vendor's environment.
	 *
	 * @return the number of virtual CPUs
	 */
	int getCpus();

	/**
	 * Returns the regions where this type of Droplet may be created.
	 *
	 * @return the regions
	 */
	Set<RegionId> getRegionIds();

	/**
	 * Returns a description of this type. For example, Basic, General Purpose, CPU-Optimized, Memory-Optimized,
	 * or Storage-Optimized.
	 *
	 * @return the description
	 */
	String getDescription();
}