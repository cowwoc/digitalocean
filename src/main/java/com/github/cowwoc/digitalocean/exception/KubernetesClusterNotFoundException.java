package com.github.cowwoc.digitalocean.exception;

import java.io.Serial;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Thrown if a referenced Kubernetes cluster could not be found.
 */
public class KubernetesClusterNotFoundException extends Exception
{
	@Serial
	private static final long serialVersionUID = 0L;
	/**
	 * The ID of the cluster that could not be found.
	 */
	private final String clusterId;

	/**
	 * Creates a new instance.
	 *
	 * @param clusterId the ID of the cluster that could not be found
	 * @throws NullPointerException     if {@code clusterId} is null
	 * @throws IllegalArgumentException if {@code clusterId} contains leading or trailing whitespace or is
	 *                                  empty
	 */
	public KubernetesClusterNotFoundException(String clusterId)
	{
		super(getMessage(clusterId));
		this.clusterId = clusterId;
	}

	/**
	 * Returns the exception message.
	 *
	 * @param clusterId the ID of the cluster that could not be found
	 * @throws NullPointerException     if {@code clusterId} is null
	 * @throws IllegalArgumentException if {@code clusterId} contains leading or trailing whitespace or is
	 *                                  empty
	 */
	private static String getMessage(String clusterId)
	{
		requireThat(clusterId, "clusterId").isStripped().isNotEmpty();
		return "Cluster " + clusterId + " not found";
	}

	/**
	 * Returns the ID of the cluster that could not be found.
	 *
	 * @return the ID of the cluster
	 */
	public String getClusterId()
	{
		return clusterId;
	}
}