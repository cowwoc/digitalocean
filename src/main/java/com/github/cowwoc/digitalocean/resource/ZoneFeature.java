package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Zone features.
 */
public enum ZoneFeature
{
	/**
	 * System-level backups at weekly or daily intervals.
	 */
	BACKUPS,
	/**
	 * Network communication over IPv6.
	 */
	IPV6,
	/**
	 * The metadata service, allowing droplets to query metadata from within the droplet itself.
	 */
	METADATA,
	/**
	 * Ability to install a metrics agent on Droplets.
	 */
	INSTALL_AGENT,
	/**
	 * Ability to use the Spaces and Block Storage Volume services.
	 */
	STORAGE,
	/**
	 * Ability to use custom Droplet images within this zone.
	 */
	IMAGE_TRANSFER;

	/**
	 * Looks up a value from its JSON representation.
	 *
	 * @param json the JSON representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public static ZoneFeature fromJson(JsonNode json)
	{
		String name = json.textValue();
		requireThat(name, "name").isStripped().isNotEmpty();
		return valueOf(name.toUpperCase(Locale.ROOT));
	}
}