package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;

/**
 * A geographical region that contains one or more <a
 * href="https://docs.digitalocean.com/platform/regional-availability/">datacenters</a>.
 */
public enum Region
{
	/**
	 * New York, United States.
	 */
	NEW_YORK("nyc"),
	/**
	 * Amsterdam, Netherlands.
	 */
	AMSTERDAM("ams"),
	/**
	 * San Francisco, United States.
	 */
	SAN_FRANCISCO("sfo"),
	/**
	 * Singapore, Singapore.
	 */
	SINGAPORE("sgp"),
	/**
	 * London, United Kingdom.
	 */
	LONDON("lon"),
	/**
	 * Frankfurt, Germany.
	 */
	FRANCE("fra"),
	/**
	 * Toronto, Canada.
	 */
	TORONTO("tor"),
	/**
	 * Bangalore, India.
	 */
	BANGALORE("blr"),
	/**
	 * Sydney, Australia.
	 */
	SYDNEY("syd");

	private final String prefix;

	/**
	 * Creates a new region.
	 *
	 * @param prefix the prefix of zone IDs in this region
	 * @throws NullPointerException     if {@code prefix} is null
	 * @throws IllegalArgumentException if {@code prefix} contains leading or trailing whitespace or is empty
	 */
	Region(String prefix)
	{
		assert that(prefix, "prefix").isStripped().isNotEmpty().elseThrow();
		this.prefix = prefix;
	}

	/**
	 * Returns the zones in this region that are available for creating Droplets.
	 *
	 * @param client the client configuration
	 * @return an empty set if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Set<Zone> getZones(DigitalOceanClient client)
		throws IOException, InterruptedException, TimeoutException
	{
		return getZones(client, true);
	}

	/**
	 * Returns the zones in this region.
	 *
	 * @param client            the client configuration
	 * @param canCreateDroplets {@code true} if the returned types must be able to create Droplets
	 * @return an empty set if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Set<Zone> getZones(DigitalOceanClient client, boolean canCreateDroplets)
		throws IOException, InterruptedException, TimeoutException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/regions_list
		return client.getElements(REST_SERVER.resolve("v2/regions"), Map.of(), body ->
		{
			Set<Zone> zones = new HashSet<>();
			for (JsonNode zone : body.get("regions"))
			{
				Zone candidate = Zone.getByJson(client, zone);
				if (candidate.canCreateDroplets() || !canCreateDroplets)
					zones.add(candidate);
			}
			return zones;
		});
	}

	/**
	 * Determines if a region contains a zone.
	 *
	 * @param zone the zone
	 * @return {@code true} if the region contains the zone
	 * @throws NullPointerException if {@code zone} is null
	 */
	public boolean contains(Zone zone)
	{
		return zone.getId().getValue().startsWith(prefix);
	}
}