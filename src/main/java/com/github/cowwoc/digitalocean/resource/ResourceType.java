package com.github.cowwoc.digitalocean.resource;

/**
 * Types of resources.
 */
public enum ResourceType
{
	/**
	 * A droplet.
	 */
	DROPLET,
	/**
	 * A Kubernetes cluster.
	 */
	KUBERNETES,
	/**
	 * An IP address.
	 */
	IP_ADDRESS,
	/**
	 * A resource tag.
	 */
	TAG,
	/**
	 * An application (app platform).
	 */
	APPLICATION
}