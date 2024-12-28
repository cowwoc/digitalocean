package com.github.cowwoc.digitalocean.resource;

import java.util.Locale;

/**
 * A
 * <a href="https://docs.digitalocean.com/platform/regional-availability/">datacenter</a>.
 */
public enum Zone
{
	/**
	 * New York, United States, zone 1.
	 */
	NYC1,
	/**
	 * New York, United States, zone 2.
	 */
	NYC2,
	/**
	 * New York, United States, zone 3.
	 */
	NYC3,
	/**
	 * Amsterdam, Netherlands, zone 3.
	 */
	AMS3,
	/**
	 * San Francisco, United States, zone 2.
	 */
	SFO2,
	/**
	 * San Francisco, United States, zone 3.
	 */
	SFO3,
	/**
	 * Singapore, Singapore, zone 1.
	 */
	SGP1,
	/**
	 * London, United Kingdom, zone 1.
	 */
	LON1,
	/**
	 * Frankfurt, Germany, zone 1.
	 */
	FRA1,
	/**
	 * Toronto, Canada, zone 1.
	 */
	TOR1,
	/**
	 * Bangalore, India, zone 1.
	 */
	BLR1,
	/**
	 * Sydney, Australia, zone 1.
	 */
	SYD1;

	/**
	 * Looks up the zone by its slug.
	 *
	 * @param slug the slug to look up
	 * @return the matching value
	 * @throws IllegalArgumentException if no match is found
	 */
	public static Zone getBySlug(String slug)
	{
		return valueOf(slug.toUpperCase(Locale.ROOT));
	}

	/**
	 * Returns the zone's slug.
	 *
	 * @return the zone's slug
	 */
	public String toSlug()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Returns the region that the zone is in.
	 *
	 * @return the region that the zone is in
	 */
	public Region getRegion()
	{
		return switch (this)
		{
			case NYC1, NYC2, NYC3 -> Region.NYC;
			case AMS3 -> Region.AMS;
			case SFO2, SFO3 -> Region.SFO;
			case SGP1 -> Region.SGP;
			case LON1 -> Region.LON;
			case FRA1 -> Region.FRA;
			case TOR1 -> Region.TOR;
			case BLR1 -> Region.BLR;
			case SYD1 -> Region.SYD;
		};
	}

	@Override
	public String toString()
	{
		return toSlug();
	}
}