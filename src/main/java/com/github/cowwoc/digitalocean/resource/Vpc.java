package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.id.StringId;
import com.github.cowwoc.digitalocean.internal.util.Strings;
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
 * Represents a private network, also known as a Virtual Private Cloud (VPC).
 * <p>
 * VPCs are zone-specific, meaning they cannot secure communication between Droplets located in different
 * zones.
 *
 * @see <a href="https://youtu.be/nbo5HrmZjXo?si=5Gf4hbticjw6nwph&t=2140">Configure a VPN to Extend a VPC
 * 	Across Zones</a>
 * @see <a
 * 	href="https://www.digitalocean.com/community/developer-center/connect-digitalocean-droplets-across-regions">
 * 	Connect DigitalOcean Droplets Across Regions</a>
 * @see <a href="https://www.digitalocean.com/blog/vpc-peering-ga">VPC Peering</a>
 */
public final class Vpc
{
	/**
	 * Returns all the VPCs.
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
	public static Set<Vpc> getAll(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/vpcs_list
		return client.getElement(REST_SERVER.resolve("v2/vpcs"), Map.of(), body ->
		{
			Set<Vpc> vpcs = new HashSet<>();
			for (JsonNode sshKey : body.get("vpcs"))
				vpcs.add(getByJson(sshKey));
			return vpcs;
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
	public static Vpc getByPredicate(DigitalOceanClient client, Predicate<Vpc> predicate)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/vpcs_list
		return client.getElement(REST_SERVER.resolve("v2/vpcs"), Map.of(), body ->
		{
			for (JsonNode projectNode : body.get("vpcs"))
			{
				Vpc candidate = getByJson(projectNode);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	/**
	 * Looks up the default VPC of a zone.
	 *
	 * @param client the client configuration
	 * @param zone   the zone
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
	public static Vpc getDefault(DigitalOceanClient client, Zone.Id zone)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(zone, "zone").isNotNull();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/vpcs_list
		return client.getElement(REST_SERVER.resolve("v2/vpcs"), Map.of(), body ->
		{
			for (JsonNode vpc : body.get("vpcs"))
			{
				boolean isDefault = client.getBoolean(vpc.get("default"), "default");
				if (!isDefault)
					continue;
				String zoneSlug = vpc.get("region").textValue();
				if (zoneSlug.equals(zone.getValue()))
					return getByJson(vpc);
			}
			return null;
		});
	}

	/**
	 * Looks up a VPC by its id.
	 *
	 * @param client the client configuration
	 * @param id     the ID of the VPC
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} is not a valid UUID per RFC 9562
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static Vpc getById(DigitalOceanClient client, String id)
		throws IOException, TimeoutException, InterruptedException
	{
		// regex taken from https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_create_cluster
		requireThat(id, "id").matches(Strings.UUID);

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/vpcs_get
		return client.getResource(REST_SERVER.resolve("v2/vpcs/" + id), body ->
		{
			JsonNode droplet = body.get("vpc");
			return getByJson(droplet);
		});
	}

	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param json the JSON representation
	 * @return the VPC
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private static Vpc getByJson(JsonNode json)
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/vpcs_get
		Id id = id(json.get("id").textValue());
		String zoneSlug = json.get("region").textValue();
		Zone.Id zone = Zone.id(zoneSlug);
		return new Vpc(id, zone);
	}

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

	private final Id id;
	private final Zone.Id zone;

	/**
	 * Creates a new VPC.
	 *
	 * @param id   the ID of the VPC
	 * @param zone the zone that the VPC is in
	 * @throws NullPointerException if any of the arguments are null
	 */
	private Vpc(Id id, Zone.Id zone)
	{
		requireThat(id, "id").isNotNull();
		requireThat(zone, "zone").isNotNull();
		this.id = id;
		this.zone = zone;
	}

	/**
	 * Returns the VPC's ID.
	 *
	 * @return the id
	 */
	public Id getId()
	{
		return id;
	}

	/**
	 * Returns the zone that the VPC is in.
	 *
	 * @return the zone
	 */
	public Zone.Id getZone()
	{
		return zone;
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Vpc other && other.id.equals(id);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Vpc.class).
			add("id", id).
			add("zone", zone).
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