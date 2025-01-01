package com.github.cowwoc.digitalocean.resource.kubernetes;

import java.util.Locale;

/**
 * The version number of the Kubernetes software.
 */
public enum KubernetesVersion
{
	/**
	 * Version 1.29.9.
	 */
	V1_29_9,
	/**
	 * Version 1.30.5.
	 */
	V1_30_5,
	/**
	 * Version 1.31.1.
	 */
	V1_31_1;

	/**
	 * Looks up the version by its slug.
	 *
	 * @param slug the slug to look up
	 * @return the matching value
	 * @throws IllegalArgumentException if no match is found
	 */
	public static KubernetesVersion getBySlug(String slug)
	{
		// Example of a slug: 1.29.9-do.5
		if (!slug.endsWith("-do.5"))
			return valueOf(slug);
		return valueOf("V" + slug.substring(0, slug.length() - "-do.5".length()).
			replace('.', '_').toUpperCase(Locale.ROOT));
	}

	/**
	 * Returns the version's slug.
	 *
	 * @return the version's slug
	 */
	public String toSlug()
	{
		return name().substring(1).replace('_', '.') + "-do.5";
	}

	@Override
	public String toString()
	{
		return toSlug();
	}
}