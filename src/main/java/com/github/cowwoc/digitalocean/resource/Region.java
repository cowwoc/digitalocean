package com.github.cowwoc.digitalocean.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A geographical region that contains one or more <a
 * href="https://docs.digitalocean.com/platform/regional-availability/">datacenters</a>.
 */
public enum Region
{
	/**
	 * New York, United States.
	 */
	NYC,
	/**
	 * Amsterdam, Netherlands.
	 */
	AMS,
	/**
	 * San Francisco, United States.
	 */
	SFO,
	/**
	 * Singapore, Singapore.
	 */
	SGP,
	/**
	 * London, United Kingdom.
	 */
	LON,
	/**
	 * Frankfurt, Germany.
	 */
	FRA,
	/**
	 * Toronto, Canada.
	 */
	TOR,
	/**
	 * Bangalore, India.
	 */
	BLR,
	/**
	 * Sydney, Australia.
	 */
	SYD;

	/**
	 * A cached mapping from each region to its zones.
	 */
	private static final Map<Region, List<Zone>> REGION_TO_ZONES;

	static
	{
		Region[] regions = values();
		Map<Region, List<Zone>> builder = HashMap.newHashMap(regions.length);
		for (Region region : regions)
		{
			List<Zone> zones = new ArrayList<>();
			for (Zone zone : Zone.values())
				if (zone.getRegion() == region)
					zones.add(zone);
			builder.put(region, zones);
		}
		// Make the map thread-safe
		REGION_TO_ZONES = Map.copyOf(builder);
	}

	/**
	 * Looks up the region by its slug.
	 *
	 * @param slug the slug to look up
	 * @return the matching value
	 * @throws IllegalArgumentException if no match is found
	 */
	public static Region getBySlug(String slug)
	{
		return valueOf(slug.toUpperCase(Locale.ROOT));
	}

	/**
	 * Returns the region's slug.
	 *
	 * @return the region's slug
	 */
	public String toSlug()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Returns the zones in this region.
	 *
	 * @return the zones in this region
	 */
	public List<Zone> getZones()
	{
		return REGION_TO_ZONES.get(this);
	}

	@Override
	public String toString()
	{
		return toSlug();
	}
}