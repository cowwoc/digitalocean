package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.id.StringId;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A datacenter.
 *
 * @see <a href="https://docs.digitalocean.com/platform/regional-availability/">Regional Availability</a>
 */
public final class Zone
{
	/**
	 * Creates a new ID.
	 *
	 * @param value the server-side identifier (slug)
	 * @return the type-safe identifier for the resource
	 * @throws IllegalArgumentException if {@code value} contains leading or trailing whitespace or is empty
	 */
	public static Id id(String value)
	{
		if (value == null)
			return null;
		return new Id(value);
	}

	/**
	 * Returns all the zones.
	 *
	 * @param client the client configuration
	 * @return an empty set if no match is found
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Set<Zone> getAll(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/regions_list
		return client.getElements(REST_SERVER.resolve("v2/regions"), Map.of(), body ->
		{
			Set<Zone> zones = new HashSet<>();
			for (JsonNode regionNode : body.get("regions"))
				zones.add(getByJson(client, regionNode));
			return zones;
		});
	}

	/**
	 * Returns the first VPC that matches a predicate.
	 *
	 * @param client    the client configuration
	 * @param predicate the predicate
	 * @return null if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Zone getByPredicate(DigitalOceanClient client, Predicate<Zone> predicate)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/regions_list
		return client.getElement(REST_SERVER.resolve("v2/regions"), Map.of(), body ->
		{
			for (JsonNode regionNode : body.get("regions"))
			{
				Zone candidate = getByJson(client, regionNode);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param client the client configuration
	 * @param json   the JSON representation
	 * @return the zone
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	static Zone getByJson(DigitalOceanClient client, JsonNode json)
		throws IOException, TimeoutException, InterruptedException
	{
		String name = json.get("name").textValue();
		String slug = json.get("slug").textValue();
		Set<ZoneFeature> features = client.getElements(json, "features", ZoneFeature::fromJson);

		boolean canCreateDroplets = client.getBoolean(json, "available");
		Set<DropletType.Id> dropletTypes = client.getElements(json, "sizes", node ->
			DropletType.id(node.textValue()));
		return new Zone(id(slug), name, features, canCreateDroplets, dropletTypes);
	}

	private final Id id;
	private final String name;
	private final Set<ZoneFeature> features;
	private final boolean canCreateDroplets;
	private final Set<DropletType.Id> dropletTypes;

	/**
	 * Creates a new instance.
	 *
	 * @param id                the zone's ID
	 * @param name              the name of this zone
	 * @param features          features that are available in this zone
	 * @param canCreateDroplets {@code true} if new Droplets can be created in this zone
	 * @param dropletTypes      the types of Droplets that can be created in this zone
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 */
	public Zone(Id id, String name, Set<ZoneFeature> features, boolean canCreateDroplets,
		Set<DropletType.Id> dropletTypes)
	{
		requireThat(id, "id").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(features, "features").isNotNull();
		requireThat(dropletTypes, "dropletTypes").isNotNull();

		this.id = id;
		this.name = name;
		this.features = Set.copyOf(features);
		this.canCreateDroplets = canCreateDroplets;
		this.dropletTypes = Set.copyOf(dropletTypes);
	}

	/**
	 * Returns the zone's ID.
	 *
	 * @return the ID
	 */
	public Id getId()
	{
		return id;
	}

	/**
	 * Returns the zone's name.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the features that are available in this zone.
	 *
	 * @return the features
	 */
	public Set<ZoneFeature> getFeatures()
	{
		return features;
	}

	/**
	 * Determines if new Droplets can be created in this zone.
	 *
	 * @return {@code true} if new Droplets can be created
	 */
	public boolean canCreateDroplets()
	{
		return canCreateDroplets;
	}

	/**
	 * Returns the type of droplets that can be created in this zone.
	 *
	 * @return the droplet types
	 */
	public Set<DropletType.Id> getDropletTypes()
	{
		return dropletTypes;
	}

	/**
	 * Returns the region that the zone is in.
	 *
	 * @return the region
	 */
	public Region getRegion()
	{
		for (Region region : Region.values())
			if (region.contains(this))
				return region;
		throw new AssertionError("No match found for " + this);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DropletType.class).
			add("id", id).
			add("name", name).
			add("features", features).
			add("available", canCreateDroplets).
			add("dropletTypes", dropletTypes).
			toString();
	}

	/**
	 * A type-safe identifier for this type of resource.
	 * <p>
	 * This adds type-safety to API methods by ensuring that IDs specific to one class cannot be used in place
	 * of IDs belonging to another class.
	 */
	public static final class Id extends StringId
	{
		/**
		 * @param value a server-side identifier
		 * @throws NullPointerException     if {@code value} is null
		 * @throws IllegalArgumentException if {@code value} contains leading or trailing whitespace or is empty
		 */
		private Id(String value)
		{
			super(value);
		}
	}
}