package io.github.cowwoc.digitalocean.compute.resource;

import io.github.cowwoc.digitalocean.core.id.ComputeDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.RegionId;

import java.math.BigDecimal;
import java.util.Set;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

/**
 * Droplet types for compute resources.
 * <p>
 * Contracted instances are custom droplet types provisioned under private agreements with DigitalOcean. They
 * are not publicly available and do not follow standard pricing or region availability rules.
 * <p>
 * As such, their {@code regions} field is empty, and the {@code transferInGiB}, {@code costPerHour}, and
 * {@code costPerMonth} fields are set to zero as placeholders to indicate that standard billing does not
 * apply.
 *
 * @see <a href="https://www.digitalocean.com/pricing/droplets#basic-droplets">pricing</a>
 */
public interface ComputeDropletType
{
	/**
	 * Returns the type's ID.
	 *
	 * @return the ID
	 */
	ComputeDropletTypeId getId();

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
	 * Returns the amount of disk space allocated to this type, in GiB.
	 *
	 * @return the amount of disk space
	 */
	int getDiskInGiB();

	/**
	 * Determines if the droplet type requires a private agreements with DigitalOcean.
	 *
	 * @return {@code true} if the type is contracted
	 */
	boolean isContracted();

	/**
	 * Returns the amount of outgoing network traffic allocated to this type, in GiB.
	 *
	 * @return zero for contracted types
	 * @see #isContracted()
	 */
	BigDecimal getTransferInGiB();

	/**
	 * Returns the hourly cost of this Droplet type in US dollars. This cost is incurred as long as the Droplet
	 * is active and has not been destroyed.
	 *
	 * @return zero for contracted types
	 * @see #isContracted()
	 */
	BigDecimal getCostPerHour();

	/**
	 * Returns the monthly cost of this Droplet type in US dollars. This cost is incurred as long as the Droplet
	 * is active and has not been destroyed.
	 *
	 * @return zero for contracted types
	 * @see #isContracted()
	 */
	BigDecimal getCostPerMonth();

	/**
	 * Returns the regions where this type of Droplet may be created.
	 *
	 * @return an empty set for contracted types
	 * @see #isContracted()
	 */
	Set<RegionId> getRegionIds();

	/**
	 * Determines if Droplets may be created with this type, regardless of the region.
	 *
	 * @return {@code true} if Droplets may be created
	 */
	boolean isAvailable();

	/**
	 * Returns a description of this type. For example, Basic, General Purpose, CPU-Optimized, Memory-Optimized,
	 * or Storage-Optimized.
	 *
	 * @return the description
	 */
	String getDescription();

	/**
	 * Returns the disks available to this type.
	 *
	 * @return the details
	 */
	Set<DiskConfiguration> getDiskConfiguration();

	/**
	 * Returns the GPU available to this type.
	 *
	 * @return {@code null} if absent
	 */
	GpuConfiguration getGpuConfiguration();

	/**
	 * Describes the disk that is allocated to a droplet.
	 *
	 * @param persistent {@code true} if the disk is persistent
	 * @param sizeInMiB  the amount of space allocated to this disk, in MiB
	 */
	record DiskConfiguration(boolean persistent, int sizeInMiB)
	{
		/**
		 * Creates a new DiskConfiguration.
		 *
		 * @param persistent {@code true} if the disk is persistent
		 * @param sizeInMiB  the amount of space allocated to this disk, in MiB
		 * @throws IllegalArgumentException if {@code sizeInMiB} is negative or zero
		 */
		public DiskConfiguration
		{
			requireThat(sizeInMiB, "sizeInMiB").isPositive();
		}
	}

	/**
	 * Describes the GPU that is allocated to a droplet type.
	 *
	 * @param count    the number of GPUs allocated to this type of droplet
	 * @param model    the model of the GPU
	 * @param ramInMiB the size of the GPU's RAM, in MiB
	 */
	record GpuConfiguration(int count, String model, int ramInMiB)
	{
		/**
		 * Creates a new instance.
		 *
		 * @param count    the number of GPUs allocated to this type of droplet
		 * @param model    the model of the GPU
		 * @param ramInMiB the size of the GPU's RAM, in MiB
		 * @throws NullPointerException     if {@code model} is null
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                  <li>{@code count} is negative or zero.</li>
		 *                                  <li>{@code model} contains leading or trailing whitespace or is empty.</li>
		 *                                  <li>{@code ramInMb} is negative or zero.</li>
		 *                                  </ul>
		 */
		public GpuConfiguration
		{
			requireThat(count, "count").isPositive();
			requireThat(model, "model").isStripped().isNotEmpty();
			requireThat(ramInMiB, "ramInMb").isPositive();
		}
	}
}