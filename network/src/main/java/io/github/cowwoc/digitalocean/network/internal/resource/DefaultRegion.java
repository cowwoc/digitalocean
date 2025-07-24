package io.github.cowwoc.digitalocean.network.internal.resource;

import io.github.cowwoc.digitalocean.core.id.ComputeDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;
import io.github.cowwoc.digitalocean.network.resource.Region;

import java.util.Set;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

public final class DefaultRegion implements Region
{
	private final RegionId id;
	private final String name;
	private final Set<Feature> features;
	private final boolean canCreateDroplets;
	private final Set<ComputeDropletTypeId> dropletTypeIds;

	/**
	 * Creates a Region.
	 *
	 * @param id                the region's ID
	 * @param name              the name of this region
	 * @param features          features that are available in this region
	 * @param canCreateDroplets {@code true} if new droplets can be created in this region
	 * @param dropletTypeIds    the types of droplets that can be created in this region
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 */
	public DefaultRegion(RegionId id, String name, Set<Feature> features, boolean canCreateDroplets,
		Set<ComputeDropletTypeId> dropletTypeIds)
	{
		requireThat(id, "id").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(features, "features").isNotNull();
		requireThat(dropletTypeIds, "dropletTypes").isNotNull();

		this.id = id;
		this.name = name;
		this.features = Set.copyOf(features);
		this.canCreateDroplets = canCreateDroplets;
		this.dropletTypeIds = Set.copyOf(dropletTypeIds);
	}

	@Override
	public RegionId getId()
	{
		return id;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public Set<Feature> getFeatures()
	{
		return features;
	}

	@Override
	public boolean canCreateDroplets()
	{
		return canCreateDroplets;
	}

	@Override
	public Set<ComputeDropletTypeId> getDropletTypeIds()
	{
		return dropletTypeIds;
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Region other && other.getId().equals(id);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DefaultRegion.class).
			add("id", id).
			add("name", name).
			add("features", features).
			add("canCreateDroplets", canCreateDroplets).
			add("dropletTypeIds", dropletTypeIds).
			toString();
	}
}