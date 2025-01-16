package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.id.IntegerId;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * The system image that was used to boot a droplet.
 */
public final class DropletImage
{
	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param client the client configuration
	 * @param json   the JSON representation
	 * @return the database
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	static DropletImage getByJson(DigitalOceanClient client, JsonNode json)
		throws IOException, InterruptedException, TimeoutException
	{
		Id id = id(client.getInt(json, "id"));
		String name = json.get("name").textValue();
		String distribution = json.get("distribution").textValue();
		String slug = json.get("slug").textValue();
		boolean isPublic = client.getBoolean(json, "public");
		Set<Zone.Id> zones = client.getElements(json, "regions", element -> Zone.id(element.textValue()));
		Type type = Type.fromJson(json.get("type"));
		int minDiskSizeInGiB = json.get("min_disk_size").intValue();
		int sizeInGiB = json.get("size_gigabytes").intValue();
		String description = json.get("description").textValue();
		Set<String> tags = client.getElements(json, "tags", JsonNode::textValue);
		Status status = Status.getByJson(json.get("status"));
		String errorMessage = json.get("error_message").textValue();
		Instant createdAt = Instant.parse(json.get("created_at").textValue());
		return new DropletImage(id, slug, name, distribution, isPublic, zones, type, minDiskSizeInGiB, sizeInGiB,
			description, tags, status, errorMessage, createdAt);
	}

	/**
	 * Creates a new ID.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 */
	public static Id id(int value)
	{
		return new Id(value);
	}

	private final Id id;
	private final String slug;
	private final String name;
	private final String distribution;
	private final boolean isPublic;
	private final Set<Zone.Id> zones;
	private final Type type;
	private final int minDiskSizeInGiB;
	private final int sizeInGiB;
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
	 * @param zones            the zones that the image is available in
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
	private DropletImage(Id id, String slug, String name, String distribution, boolean isPublic,
		Set<Zone.Id> zones, Type type, int minDiskSizeInGiB, int sizeInGiB, String description, Set<String> tags,
		Status status, String errorMessage, Instant createdAt)
	{
		requireThat(id, "id").isNotNull();
		requireThat(slug, "slug").isStripped().isNotEmpty();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(distribution, "distribution").isStripped().isNotEmpty();
		requireThat(zones, "zones").isNotNull();
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
		this.zones = zones;
		this.type = type;
		this.minDiskSizeInGiB = minDiskSizeInGiB;
		this.sizeInGiB = sizeInGiB;
		this.description = description;
		this.tags = tags;
		this.status = status;
		this.errorMessage = errorMessage;
		this.createdAt = createdAt;
	}

	/**
	 * Returns the ID of the image.
	 *
	 * @return the ID of the image
	 */
	public Id getId()
	{
		return id;
	}

	/**
	 * Returns the slug of the image.
	 *
	 * @return the slug of the image
	 */
	public String getSlug()
	{
		return slug;
	}

	/**
	 * Returns the name of the image.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the OS distribution of the image.
	 *
	 * @return the OS distribution
	 */
	public String getDistribution()
	{
		return distribution;
	}

	/**
	 * Determines if the image is available for public use.
	 *
	 * @return {@code true} if the image is available for public use
	 */
	public boolean isPublic()
	{
		return isPublic;
	}

	/**
	 * Returns the zones that the image is available in.
	 *
	 * @return the zones
	 */
	public Set<Zone.Id> getZones()
	{
		return zones;
	}

	/**
	 * Returns the type of the image.
	 *
	 * @return the type
	 */
	public Type getType()
	{
		return type;
	}

	/**
	 * Returns the minimum disk size in GiB required to use this image.
	 *
	 * @return the minimum disk size
	 */
	public int getMinDiskSizeInGiB()
	{
		return minDiskSizeInGiB;
	}

	/**
	 * Returns the size of the image in GiB.
	 *
	 * @return the size
	 */
	public int getSizeInGiB()
	{
		return sizeInGiB;
	}

	/**
	 * Returns a description of the image.
	 *
	 * @return the description
	 */
	public String getDescription()
	{
		return description;
	}

	/**
	 * Returns the tags that are associated with the image.
	 *
	 * @return the tags
	 */
	public Set<String> getTags()
	{
		return tags;
	}

	/**
	 * Returns the status of the image.
	 *
	 * @return the status
	 */
	public Status getStatus()
	{
		return status;
	}

	/**
	 * Returns an explanation of why the image cannot be used.
	 *
	 * @return an explanation of the failure
	 */
	public String getErrorMessage()
	{
		return errorMessage;
	}

	/**
	 * Returns the time the image was created at.
	 *
	 * @return the time
	 */
	public Instant getCreatedAt()
	{
		return createdAt;
	}

	/**
	 * Looks up an image by its slug.
	 *
	 * @param client the client configuration
	 * @param slug   a slug
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code slug} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static DropletImage getBySlug(DigitalOceanClient client, String slug)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(slug, "slug").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/images_get
		return client.getResource(REST_SERVER.resolve("v2/images/" + slug), body ->
		{
			JsonNode image = body.get("image");
			return getByJson(client, image);
		});
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof DropletImage other && other.id == id && other.slug.equals(slug);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, slug);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DropletImage.class).
			add("id", id).
			add("slug", slug).
			add("name", name).
			add("distribution", distribution).
			add("isPublic", isPublic).
			add("zones", zones).
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

	/**
	 * A type-safe identifier for this type of resource.
	 * <p>
	 * This adds type-safety to API methods by ensuring that IDs specific to one class cannot be used in place
	 * of IDs belonging to another class.
	 */
	public static final class Id extends IntegerId
	{
		/**
		 * @param value a server-side identifier
		 */
		private Id(int value)
		{
			super(value);
		}
	}

	/**
	 * The image of the image.
	 */
	public enum Type
	{
		/**
		 * Base OS image.
		 */
		BASE,
		/**
		 * User-generated Droplet snapshot.
		 */
		SNAPSHOT,
		/**
		 * Automatically created Droplet backup.
		 */
		BACKUP,
		/**
		 * User-provided virtual machine image.
		 */
		CUSTOM,
		/**
		 * Image used by DigitalOcean managed resources (e.g. DOKS worker nodes).
		 */
		ADMIN;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static Type fromJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
	}

	/**
	 * The status of the image.
	 */
	public enum Status
	{
		// Per DigitalOcean tech support, the "pending" is never returned.
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
		DELETED;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static Status getByJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
	}
}