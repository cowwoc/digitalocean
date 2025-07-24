package io.github.cowwoc.digitalocean.compute.internal.resource;

import io.github.cowwoc.digitalocean.compute.internal.util.Numbers;
import io.github.cowwoc.digitalocean.compute.resource.ComputeDropletType;
import io.github.cowwoc.digitalocean.core.id.ComputeDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;

import java.math.BigDecimal;
import java.util.Set;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

public final class DefaultComputeDropletType implements ComputeDropletType
{
	private final ComputeDropletTypeId id;
	private final int ramInMiB;
	private final int cpus;
	private final int diskInGiB;
	private final BigDecimal transferInGiB;
	private final BigDecimal costPerHour;
	private final BigDecimal costPerMonth;
	private final Set<RegionId> regionIds;
	private final boolean available;
	private final String description;
	private final Set<DiskConfiguration> diskConfiguration;
	private final GpuConfiguration gpuConfiguration;

	/**
	 * Creates a new instance.
	 *
	 * @param id                the type's ID
	 * @param ramInMiB          the amount of RAM allocated to this type, in MiB
	 * @param cpus              the number of virtual CPUs allocated to this type. Note that vCPUs are relative
	 *                          units specific to each vendor's infrastructure and hardware. A number of vCPUs
	 *                          is relative to other vCPUs by the same vendor, meaning that the allocation and
	 *                          performance are comparable within the same vendor's environment.
	 * @param diskInGiB         the amount of disk space allocated to this type, in GiB
	 * @param transferInGiB     the amount of outgoing network traffic allocated to this type, in GiB
	 * @param costPerHour       the hourly cost of this droplet type in US dollars. This cost is incurred as
	 *                          long as the droplet is active and has not been destroyed.
	 * @param costPerMonth      the monthly cost of this droplet type in US dollars. This cost is incurred as
	 *                          long as the droplet is active and has not been destroyed.
	 * @param regionIds         the regions where this type of droplet may be created
	 * @param available         {@code true} if droplets may be created with this type, regardless of the
	 *                          region
	 * @param description       a description of this type. For example, Basic, General Purpose, CPU-Optimized,
	 *                          Memory-Optimized, or Storage-Optimized.
	 * @param diskConfiguration describes the disks available to this type
	 * @param gpuConfiguration  (optional) describes the GPU available to this type, or {@code null} if absent
	 * @throws NullPointerException     if any of the mandatory arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code ramInMiB}, {@code vCpus} or {@code diskInGiB} are negative
	 *                                    or zero.</li>
	 *                                    <li>{@code transferInGiB}, {@code costPerHour} or
	 *                                    {@code costPerMonth} are negative.</li>
	 *                                    <li>{@code description} contains leading or trailing whitespace or
	 *                                    is empty.</li>
	 *                                  </ul>
	 */
	public DefaultComputeDropletType(ComputeDropletTypeId id, int ramInMiB, int cpus, int diskInGiB,
		BigDecimal transferInGiB,
		BigDecimal costPerHour, BigDecimal costPerMonth, Set<RegionId> regionIds, boolean available,
		String description, Set<DiskConfiguration> diskConfiguration, GpuConfiguration gpuConfiguration)
	{
		requireThat(id, "id").isNotNull();
		requireThat(ramInMiB, "ramInMiB").isPositive();
		requireThat(cpus, "vCpus").isPositive();
		requireThat(diskInGiB, "diskInGiB").isPositive();
		requireThat(transferInGiB, "transferInGiB").isNotNegative();
		requireThat(costPerHour, "costPerHour").isNotNegative();
		requireThat(costPerMonth, "costPerMonth").isNotNegative();
		requireThat(regionIds, "regionIds").isNotNull();
		requireThat(description, "description").isStripped().isNotEmpty();
		requireThat(diskConfiguration, "diskConfiguration").isNotNull();
		this.id = id;
		this.ramInMiB = ramInMiB;
		this.cpus = cpus;
		this.diskInGiB = diskInGiB;
		this.transferInGiB = Numbers.copyOf(transferInGiB);
		this.costPerHour = Numbers.copyOf(costPerHour);
		this.costPerMonth = Numbers.copyOf(costPerMonth);
		this.regionIds = Set.copyOf(regionIds);
		this.available = available;
		this.description = description;
		this.diskConfiguration = diskConfiguration;
		this.gpuConfiguration = gpuConfiguration;
	}

	@Override
	public ComputeDropletTypeId getId()
	{
		return id;
	}

	@Override
	public int getRamInMiB()
	{
		return ramInMiB;
	}

	@Override
	public int getCpus()
	{
		return cpus;
	}

	@Override
	public int getDiskInGiB()
	{
		return diskInGiB;
	}

	@Override
	public boolean isContracted()
	{
		return regionIds.isEmpty();
	}

	@Override
	public BigDecimal getTransferInGiB()
	{
		return transferInGiB;
	}

	@Override
	public BigDecimal getCostPerHour()
	{
		return costPerHour;
	}

	@Override
	public BigDecimal getCostPerMonth()
	{
		return costPerMonth;
	}

	@Override
	public Set<RegionId> getRegionIds()
	{
		return regionIds;
	}

	@Override
	public boolean isAvailable()
	{
		return available;
	}

	@Override
	public String getDescription()
	{
		return description;
	}

	@Override
	public Set<DiskConfiguration> getDiskConfiguration()
	{
		return diskConfiguration;
	}

	@Override
	public GpuConfiguration getGpuConfiguration()
	{
		return gpuConfiguration;
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof ComputeDropletType other && other.getId().equals(id);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DefaultComputeDropletType.class).
			add("id", id).
			add("ramInMiB", ramInMiB).
			add("cpus", cpus).
			add("diskInMiB", diskInGiB).
			add("transferInGiB", transferInGiB).
			add("pricePerHour", costPerHour).
			add("pricePerMonth", costPerMonth).
			add("regionIds", regionIds).
			add("available", available).
			add("description", description).
			add("diskConfiguration", diskConfiguration).
			add("gpuConfiguration", gpuConfiguration).
			toString();
	}
}