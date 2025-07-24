package io.github.cowwoc.digitalocean.network.internal.resource;

import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.id.VpcId;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;
import io.github.cowwoc.digitalocean.network.resource.Vpc;

import java.util.Objects;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

public final class DefaultVpc implements Vpc
{
	private final VpcId id;
	private final RegionId regionId;

	/**
	 * Creates a new VPC.
	 *
	 * @param id       the ID of the VPC
	 * @param regionId the region that the VPC is in
	 * @throws NullPointerException if any of the arguments are null
	 */
	public DefaultVpc(VpcId id, RegionId regionId)
	{
		requireThat(id, "id").isNotNull();
		requireThat(regionId, "region").isNotNull();
		this.id = id;
		this.regionId = regionId;
	}

	@Override
	public VpcId getId()
	{
		return id;
	}

	@Override
	public RegionId getRegionId()
	{
		return regionId;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, regionId);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Vpc other && other.getId().equals(id) && other.getRegionId().equals(regionId);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DefaultVpc.class).
			add("id", id).
			add("regionId", regionId).
			toString();
	}
}