package io.github.cowwoc.digitalocean.database.internal.client;

import io.github.cowwoc.digitalocean.core.id.DatabaseDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;
import io.github.cowwoc.digitalocean.database.resource.DatabaseDropletType;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

public final class DefaultDatabaseDropletType implements DatabaseDropletType
{
	private final DatabaseDropletTypeId id;
	private final int ramInMiB;
	private final int cpus;
	private final Set<RegionId> regionIds;
	private final String description;

	/**
	 * Creates a DefaultDatabaseDropletType.
	 *
	 * @param id the ID
	 */
	public DefaultDatabaseDropletType(DatabaseDropletTypeId id, Set<RegionId> regionIds)
	{
		assert id != null;
		assert regionIds != null;
		this.id = id;
		this.regionIds = regionIds;

		Iterator<String> idComponents = Arrays.asList(id.getValue().split("-")).iterator();
		// Length of 3: gd-2vcpu-8gb
		// Length of 4: db-s-1vcpu-1gb
		this.description = switch (idComponents.next())
		{
			case "db" -> switch (idComponents.next())
			{
				case "amd" -> "Basic Premium AMD";
				case "intel" -> "Basic Premium Intel";
				case "s" -> "Basic Regular (shared CPU)";
				default -> throw new AssertionError("Unexpected response: " + id);
			};
			case "gd" -> "General Purpose (dedicated CPU)";
			case "so1_5" -> "Storage Optimized";
			default -> throw new AssertionError("Unexpected response: " + id);
		};

		String vcpuComponent = idComponents.next();
		assert vcpuComponent.length() > "vcpu".length() : id;
		String vcpuAsString = vcpuComponent.substring(0, vcpuComponent.length() - "vcpu".length());
		this.cpus = Integer.parseInt(vcpuAsString);

		String ramComponent = idComponents.next();
		assert ramComponent.length() > "gb".length() : id;
		String ramAsString = ramComponent.substring(0, ramComponent.length() - "gb".length());
		this.ramInMiB = Integer.parseInt(ramAsString) * 1024;

		assert !idComponents.hasNext() : id;
	}

	@Override
	public DatabaseDropletTypeId getId()
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
	public Set<RegionId> getRegionIds()
	{
		return regionIds;
	}

	@Override
	public String getDescription()
	{
		return description;
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DefaultDatabaseDropletType.class).
			add("id", id).
			add("ramInMiB", ramInMiB).
			add("cpus", cpus).
			add("regionIds", regionIds).
			add("description", description).
			toString();
	}
}