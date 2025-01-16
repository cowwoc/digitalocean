package com.github.cowwoc.digitalocean.exception;

import com.github.cowwoc.digitalocean.resource.Kubernetes;

import java.io.Serial;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Thrown if a referenced Kubernetes cluster could not be found.
 */
public final class KubernetesNotFoundException extends ResourceNotFoundException
{
	@Serial
	private static final long serialVersionUID = 0L;
	/**
	 * The ID of the cluster that could not be found.
	 */
	private transient final Kubernetes.Id clusterId;

	/**
	 * Creates a new instance.
	 *
	 * @param clusterId the ID of the cluster that could not be found
	 * @throws NullPointerException if {@code clusterId} is null
	 */
	public KubernetesNotFoundException(Kubernetes.Id clusterId)
	{
		super(getMessage(clusterId));
		this.clusterId = clusterId;
	}

	/**
	 * Returns the exception message.
	 *
	 * @param clusterId the ID of the cluster that could not be found
	 * @throws NullPointerException if {@code clusterId} is null
	 */
	private static String getMessage(Kubernetes.Id clusterId)
	{
		requireThat(clusterId, "clusterId").isNotNull();
		return "Cluster " + clusterId + " not found";
	}

	/**
	 * Returns the ID of the cluster that could not be found.
	 *
	 * @return the ID of the cluster
	 */
	public Kubernetes.Id getClusterId()
	{
		return clusterId;
	}
}