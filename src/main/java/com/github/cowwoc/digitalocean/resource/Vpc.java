package com.github.cowwoc.digitalocean.resource;

import com.github.cowwoc.digitalocean.internal.util.DigitalOceans;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import com.github.cowwoc.digitalocean.scope.DigitalOceanScope;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.util.DigitalOceans.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A private network, otherwise known as a virtual private cloud (VPC).
 */
public final class Vpc
{
	/**
	 * Looks up the default VPC of a zone.
	 *
	 * @param scope the client configuration
	 * @param zone  the zone
	 * @return null if no match is found
	 * @throws NullPointerException if any of the arguments are null
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public static Vpc getDefault(DigitalOceanScope scope, Zone zone)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(zone, "zone").isNotNull();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/vpcs_list
		String uri = REST_SERVER + "/v2/vpcs";
		return DigitalOceans.getElement(scope, uri, Map.of(), body ->
		{
			// https://docs.digitalocean.com/reference/api/intro/#links--pagination
			for (JsonNode vpc : body.get("vpcs"))
			{
				boolean isDefault = DigitalOceans.toBoolean(vpc.get("default"), "default");
				if (!isDefault)
					continue;
				String zoneAsSlug = vpc.get("region").textValue();
				Zone actualZone = Zone.getBySlug(zoneAsSlug);
				if (actualZone.equals(zone))
					return getByJson(vpc);
			}
			return null;
		});
	}

	/**
	 * Looks up a VPC by its id.
	 *
	 * @param scope the client configuration
	 * @param id    the ID of the VPC
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static Vpc getById(DigitalOceanScope scope, String id)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(id, "id").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/vpcs_get
		String uri = REST_SERVER + "/v2/vpcs/" + id;
		return DigitalOceans.getElement(scope, uri, Map.of(), body ->
		{
			JsonNode vpc = body.get("vpc");
			return getByJson(vpc);
		});
	}

	/**
	 * @param json the JSON representation of the VPC
	 * @return the VPC
	 * @throws NullPointerException if {@code json} is null
	 */
	private static Vpc getByJson(JsonNode json)
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/vpcs_get
		String id = json.get("id").textValue();
		String zoneAsString = json.get("region").textValue();
		Zone zone = Zone.getBySlug(zoneAsString);
		return new Vpc(id, zone);
	}

	private final String id;
	private final Zone zone;

	/**
	 * Creates a new VPC.
	 *
	 * @param id   the ID of the VPC
	 * @param zone the zone that the VPC is in
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace</li>
	 *                                    <li>any of the mandatory arguments are empty</li>
	 *                                  </ul>
	 */
	private Vpc(String id, Zone zone)
	{
		requireThat(id, "id").isStripped().isNotEmpty();
		requireThat(zone, "zone").isNotNull();
		this.id = id;
		this.zone = zone;
	}

	/**
	 * Returns the VPC's ID.
	 *
	 * @return the id
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the zone that the VPC is in.
	 *
	 * @return the zone that the VPC is in
	 */
	public Zone getZone()
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
}