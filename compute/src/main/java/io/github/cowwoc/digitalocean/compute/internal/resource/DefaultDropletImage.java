package io.github.cowwoc.digitalocean.compute.internal.resource;

import io.github.cowwoc.digitalocean.compute.resource.DropletImage;
import io.github.cowwoc.digitalocean.core.id.DropletImageId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;

import java.time.Instant;
import java.util.Set;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

public final class DefaultDropletImage implements DropletImage
{
	private final DropletImageId id;
	private final String slug;
	private final String name;
	private final String distribution;
	private final boolean isPublic;
	private final Set<RegionId> regionIds;
	private final Type type;
	private final int minDiskSizeInGiB;
	private final float sizeInGiB;
	private final String description;
	private final Set<String> tags;
	private final Status status;
	private final String errorMessage;
	private final Instant createdAt;

	/**
	 * Creates a new instance.
	 *
	 * @param id               the ID of the image
	 * @param slug             the human-readable ID
	 * @param name             the name of the image
	 * @param distribution     the OS distribution
	 * @param isPublic         {@code true} if the image is available for public use
	 * @param regionIds        the regions that the image is available in
	 * @param type             the type of the image
	 * @param minDiskSizeInGiB the minimum disk size in GiB required to use this image
	 * @param sizeInGiB        the size of the image in GiB
	 * @param description      a description of the image
	 * @param tags             the tags that are associated with the image
	 * @param status           the status of the image
	 * @param errorMessage     an explanation of why importing a custom image failed
	 * @param createdAt        the time the image was created
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace or
	 *                                    are empty.</li>
	 *                                    <li>{@code minDiskSizeInGiB} is negative.</li>
	 *                                    <li>{@code sizeInGiB} is negative or zero.</li>
	 *                                  </ul>
	 */
	public DefaultDropletImage(DropletImageId id, String slug, String name, String distribution,
		boolean isPublic,
		Set<RegionId> regionIds, Type type, int minDiskSizeInGiB, float sizeInGiB, String description,
		Set<String> tags, Status status, String errorMessage, Instant createdAt)
	{
		requireThat(id, "id").isNotNull();
		requireThat(slug, "slug").isStripped().isNotEmpty();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(distribution, "distribution").isStripped().isNotEmpty();
		requireThat(regionIds, "regionsIds").isNotNull();
		requireThat(type, "type").isNotNull();
		requireThat(minDiskSizeInGiB, "minDiskSizeInGiB").isNotNegative();
		requireThat(sizeInGiB, "sizeInGiB").isPositive();
		requireThat(description, "description").isNotNull();
		requireThat(tags, "tags").isNotNull();
		requireThat(status, "status").isNotNull();
		requireThat(errorMessage, "errorMessage").isStripped();
		requireThat(createdAt, "createdAt").isNotNull();

		this.id = id;
		this.slug = slug;
		this.name = name;
		this.distribution = distribution;
		this.isPublic = isPublic;
		this.regionIds = regionIds;
		this.type = type;
		this.minDiskSizeInGiB = minDiskSizeInGiB;
		this.sizeInGiB = sizeInGiB;
		this.description = description;
		this.tags = tags;
		this.status = status;
		this.errorMessage = errorMessage;
		this.createdAt = createdAt;
	}

	@Override
	public DropletImageId getId()
	{
		return id;
	}

	@Override
	public String getSlug()
	{
		return slug;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public String getDistribution()
	{
		return distribution;
	}

	@Override
	public boolean isPublic()
	{
		return isPublic;
	}

	@Override
	public Set<RegionId> getRegionIds()
	{
		return regionIds;
	}

	@Override
	public Type getType()
	{
		return type;
	}

	@Override
	public int getMinDiskSizeInGiB()
	{
		return minDiskSizeInGiB;
	}

	@Override
	public float getSizeInGiB()
	{
		return sizeInGiB;
	}

	@Override
	public String getDescription()
	{
		return description;
	}

	@Override
	public Set<String> getTags()
	{
		return tags;
	}

	@Override
	public Status getStatus()
	{
		return status;
	}

	@Override
	public String getErrorMessage()
	{
		return errorMessage;
	}

	@Override
	public Instant getCreatedAt()
	{
		return createdAt;
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof DropletImage other && other.getId() == id;
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DefaultDropletImage.class).
			add("id", id).
			add("slug", slug).
			add("name", name).
			add("distribution", distribution).
			add("isPublic", isPublic).
			add("regionIds", regionIds).
			add("type", type).
			add("minDiskSizeInGiB", minDiskSizeInGiB).
			add("sizeInGiB", sizeInGiB).
			add("description", description).
			add("tags", tags).
			add("status", status).
			add("errorMessage", errorMessage).
			add("createdAt", createdAt).
			toString();
	}
}