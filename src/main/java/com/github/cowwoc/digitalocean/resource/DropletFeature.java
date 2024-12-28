package com.github.cowwoc.digitalocean.resource;

import java.util.Locale;

/**
 * Droplet features that are disabled by default.
 */
public enum DropletFeature
{
	/**
	 * Enables system-level backups at weekly or daily intervals.
	 */
	BACKUPS,
	/**
	 * Network communication is isolated within the account or team where they were created.
	 */
	PRIVATE_NETWORKING,
	/**
	 * Enable IPv6 communication with the droplet.
	 */
	IPV6,
	/**
	 * A service that gathers basic metrics about Droplet-level resource utilization (cpu usage, memory usage,
	 * disk I/O, network bandwidth).
	 */
	MONITORING,
	/**
	 * A service that gathers additional metrics about Droplet-level resource utilization (load average,
	 * additional granularity for cpu usage, memory usage, disk I/O, network bandwidth).
	 */
	METRICS_AGENT;

	/**
	 * Returns the JSON representation of this feature.
	 *
	 * @return the JSON representation of this feature
	 */
	public String getJsonName()
	{
		return name().toLowerCase(Locale.ROOT);
	}
}