package com.github.cowwoc.digitalocean.resource.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import com.github.cowwoc.digitalocean.internal.util.DigitalOceans;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import com.github.cowwoc.digitalocean.resource.Vpc;
import com.github.cowwoc.digitalocean.resource.Zone;
import com.github.cowwoc.digitalocean.resource.kubernetes.Cluster.MaintenanceWindow;
import com.github.cowwoc.digitalocean.resource.kubernetes.Cluster.NodePool;
import com.github.cowwoc.digitalocean.resource.kubernetes.Cluster.NodePoolConfiguration;
import com.github.cowwoc.digitalocean.util.CreateResult;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.util.DigitalOceans.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.UNPROCESSABLE_ENTITY_422;

/**
 * The desired state of a Kubernetes cluster.
 */
public final class ClusterConfiguration
{
	private final DigitalOceanClient client;
	private final String name;
	private final Zone zone;
	private final KubernetesVersion version;
	private String clusterSubnet = "";
	private String serviceSubnet = "";
	private Vpc vpc;
	private final Set<String> tags = new LinkedHashSet<>();
	private final List<NodePoolConfiguration> nodePools;
	private MaintenanceWindow maintenanceWindow;
	private boolean autoUpgrade;
	private boolean surgeUpgrade;
	private boolean highAvailability;
	private boolean registryEnabled;

	/**
	 * Creates a new cluster configuration.
	 *
	 * @param client    the client configuration
	 * @param name      the name of the cluster. Names are case-insensitive.
	 * @param zone      the zone to deploy the cluster into
	 * @param version   the version of Kubernetes software to deploy
	 * @param nodePools the node pools to deploy into the cluster
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace or
	 *                                    are empty.</li>
	 *                                    <li>{@code nodePools} is empty.</li>
	 *                                  </ul>
	 */
	// WORKAROUND: https://github.com/checkstyle/checkstyle/issues/15683
	@SuppressWarnings("checkstyle:javadocmethod")
	ClusterConfiguration(DigitalOceanClient client, String name, Zone zone, KubernetesVersion version,
		List<NodePoolConfiguration> nodePools)
	{
		requireThat(client, "client").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(zone, "zone").isNotNull();
		requireThat(version, "version").isNotNull();
		requireThat(nodePools, "nodePools").isNotEmpty();
		this.client = client;
		this.name = name;
		this.zone = zone;
		this.version = version;
		this.nodePools = new ArrayList<>(nodePools);
	}

	/**
	 * Returns the name of the cluster.
	 *
	 * @return the name of the cluster
	 */
	public String name()
	{
		return name;
	}

	/**
	 * Returns the zone to deploy the cluster into.
	 *
	 * @return the zone
	 */
	public Zone zone()
	{
		return zone;
	}

	/**
	 * Returns the kubernetes software version to deploy.
	 *
	 * @return the kubernetes software version
	 */
	public KubernetesVersion version()
	{
		return version;
	}

	/**
	 * Sets the range of IP addresses for the overlay network of the Kubernetes cluster, in CIDR notation.
	 *
	 * @param clusterSubnet the range of IP addresses in CIDR notation
	 * @return this
	 * @throws NullPointerException     if {@code clusterSubnet} is null
	 * @throws IllegalArgumentException if {@code clusterSubnet} contains leading or trailing whitespace or is
	 *                                  empty
	 */
	public ClusterConfiguration clusterSubnet(String clusterSubnet)
	{
		requireThat(clusterSubnet, "clusterSubnet").isStripped().isNotEmpty();
		this.clusterSubnet = clusterSubnet;
		return this;
	}

	/**
	 * Returns the range of IP addresses for the overlay network of the Kubernetes cluster, in CIDR notation.
	 *
	 * @return an empty string if unspecified
	 */
	public String clusterSubnet()
	{
		return clusterSubnet;
	}

	/**
	 * Sets the range of IP addresses for services running in the Kubernetes cluster, in CIDR notation.
	 *
	 * @param serviceSubnet the range of IP addresses in CIDR notation
	 * @return this
	 * @throws NullPointerException     if {@code serviceSubnet} is null
	 * @throws IllegalArgumentException if {@code serviceSubnet} contains leading or trailing whitespace or is
	 *                                  empty
	 */
	public ClusterConfiguration serviceSubnet(String serviceSubnet)
	{
		requireThat(serviceSubnet, "serviceSubnet").isStripped().isNotEmpty();
		this.serviceSubnet = serviceSubnet;
		return this;
	}

	/**
	 * Returns the range of IP addresses for the services running in the Kubernetes cluster, in CIDR notation.
	 *
	 * @return an empty string if unspecified
	 */
	public String serviceSubnet()
	{
		return serviceSubnet;
	}

	/**
	 * Returns the VPC that the cluster will use.
	 *
	 * @param vpc the VPC
	 * @return this
	 * @throws NullPointerException if {@code vpc} is null
	 * @see Vpc#getDefault(DigitalOceanClient, Zone)
	 */
	public ClusterConfiguration vpc(Vpc vpc)
	{
		requireThat(vpc, "vpc").isNotNull();
		this.vpc = vpc;
		return this;
	}

	/**
	 * Returns the VPC that the cluster will use.
	 *
	 * @return null if the region's default VPC will be used
	 */
	public Vpc vpc()
	{
		return vpc;
	}

	/**
	 * Adds a tag to apply to the cluster.
	 *
	 * @param tag the tag
	 * @return this
	 * @throws NullPointerException     if {@code tag} is null
	 * @throws IllegalArgumentException if {@code tag} contains leading or trailing whitespace or is empty
	 */
	public ClusterConfiguration tag(String tag)
	{
		requireThat(tag, "tag").isStripped().isNotEmpty();
		tags.add(tag);
		return this;
	}

	/**
	 * Sets the tags that are associated with the cluster.
	 *
	 * @param tags the tags
	 * @return this
	 * @throws NullPointerException     if {@code tags} is null
	 * @throws IllegalArgumentException if any of the tags contains leading or trailing whitespace or are empty
	 */
	public ClusterConfiguration tags(Set<String> tags)
	{
		requireThat(tags, "tags").isNotNull();
		this.tags.clear();
		for (String tag : tags)
		{
			requireThat(tag, "tag").withContext(tags, "tags").isStripped().isNotEmpty();
			this.tags.add(tag);
		}
		return this;
	}

	/**
	 * Returns the tags that are associated with the cluster.
	 *
	 * @return the tags that
	 */
	public Set<String> tags()
	{
		return tags;
	}

	/**
	 * Sets the node pools to deploy into the cluster.
	 *
	 * @param nodePools the node pools
	 * @return this
	 * @throws NullPointerException if {@code nodePools} is null
	 */
	public ClusterConfiguration nodePools(List<NodePoolConfiguration> nodePools)
	{
		requireThat(nodePools, "nodePools").isNotNull();
		this.nodePools.clear();
		this.nodePools.addAll(nodePools);
		return this;
	}

	/**
	 * Returns the node pools to deploy into the cluster.
	 *
	 * @return the node pools
	 */
	public List<NodePoolConfiguration> nodePools()
	{
		return nodePools;
	}

	/**
	 * Sets the schedule of the cluster's maintenance window.
	 *
	 * @param maintenanceWindow the maintenance window
	 * @return this
	 * @throws NullPointerException if {@code maintenanceWindow} is null
	 */
	public ClusterConfiguration maintenanceWindow(MaintenanceWindow maintenanceWindow)
	{
		requireThat(maintenanceWindow, "maintenanceWindow").isNotNull();
		this.maintenanceWindow = maintenanceWindow;
		return this;
	}

	/**
	 * Returns the schedule of the cluster's maintenance window.
	 *
	 * @return the maintenance window
	 */
	public MaintenanceWindow maintenanceWindow()
	{
		return maintenanceWindow;
	}

	/**
	 * Determines the cluster's auto-upgrade policy. By default, this property is {@code false}.
	 *
	 * @param autoUpgrade {@code true} if the cluster will be automatically upgraded to new patch releases
	 *                    during its maintenance window.
	 * @return this
	 */
	public ClusterConfiguration autoUpgrade(boolean autoUpgrade)
	{
		this.autoUpgrade = autoUpgrade;
		return this;
	}

	/**
	 * Determines if the cluster will be automatically upgraded to new patch releases during its maintenance
	 * window.
	 *
	 * @return {@code true} if the cluster will be automatically upgraded to new patch releases during its
	 * 	maintenance window.
	 */
	public boolean autoUpgrade()
	{
		return autoUpgrade;
	}

	/**
	 * Determines if new nodes should be deployed before destroying the outdated nodes. This speeds up cluster
	 * upgrades and improves their reliability. By default, this property is {@code false}.
	 *
	 * @param surgeUpgrade {@code true} if new nodes should be deployed before destroying the outdated nodes
	 * @return this
	 */
	public ClusterConfiguration surgeUpgrade(boolean surgeUpgrade)
	{
		this.surgeUpgrade = surgeUpgrade;
		return this;
	}

	/**
	 * Determines if new nodes should be deployed before destroying the outdated nodes. This speeds up cluster
	 * upgrades and improves their reliability. By default, this property is {@code false}.
	 *
	 * @return {@code true} if new nodes should be deployed before destroying the outdated nodes
	 */
	public boolean surgeUpgrade()
	{
		return surgeUpgrade;
	}

	/**
	 * Determines if the control plane should run in a highly available configuration. This creates multiple
	 * backup replicas of each control plane component and provides extra reliability for critical workloads.
	 * Highly available control planes incur less downtime. Once enabled, this feature cannot be disabled.
	 *
	 * @param highAvailability {@code true} if the control plane should run in a highly available configuration
	 * @return this
	 */
	public ClusterConfiguration highAvailability(boolean highAvailability)
	{
		this.highAvailability = highAvailability;
		return this;
	}

	/**
	 * Determines if the control plane should run in a highly available configuration. This creates multiple
	 * backup replicas of each control plane component and provides extra reliability for critical workloads.
	 * Highly available control planes incur less downtime. Once enabled, this feature cannot be disabled.
	 *
	 * @return {@code true} if the control plane should run in a highly available configuration
	 */
	public boolean highAvailability()
	{
		return highAvailability;
	}

	/**
	 * Determines if a container registry is integrated with the cluster. By default, this property is
	 * {@code false}.
	 *
	 * @param registryEnabled {@code true} if a container registry is integrated with the cluster
	 * @return this
	 */
	public ClusterConfiguration registryEnabled(boolean registryEnabled)
	{
		this.registryEnabled = registryEnabled;
		return this;
	}

	/**
	 * Determines if the control plane should run in a highly available configuration. This creates multiple
	 * backup replicas of each control plane component and provides extra reliability for critical workloads.
	 * Highly available control planes incur less downtime. Once enabled, this feature cannot be disabled.
	 *
	 * @return {@code true} if the control plane should run in a highly available configuration
	 */
	public boolean registryEnabled()
	{
		return registryEnabled;
	}

	/**
	 * Creates a cluster based on this configuration.
	 *
	 * @return the new or existing cluster
	 * @throws IllegalArgumentException if a cluster with this name already exists
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public CreateResult<Cluster> create()
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_create_cluster
		ObjectMapper om = client.getObjectMapper();
		ObjectNode requestBody = om.createObjectNode().
			put("name", name).
			put("region", zone.toSlug()).
			put("version", version.toSlug());
		ArrayNode nodePoolsNode = requestBody.putArray("node_pools");
		for (NodePoolConfiguration pool : nodePools)
			nodePoolsNode.add(pool.toJson());
		if (!clusterSubnet.isEmpty())
			requestBody.put("clusterSubnet", clusterSubnet);
		if (!serviceSubnet.isEmpty())
			requestBody.put("serviceSubnet", serviceSubnet);
		if (vpc != null)
			requestBody.put("vpc_uuid", vpc.getId());
		if (maintenanceWindow != null)
			requestBody.set("maintenance_policy", maintenanceWindow.toJson());
		if (autoUpgrade)
			requestBody.put("auto_upgrade", true);
		if (surgeUpgrade)
			requestBody.put("surge_upgrade", true);
		if (highAvailability)
			requestBody.put("ha", true);
		if (registryEnabled)
			requestBody.put("registry_enavbled", true);
		String uri = REST_SERVER + "/v2/kubernetes/clusters";
		Request request = DigitalOceans.createRequest(client, uri, requestBody).
			method(POST);
		ClientRequests clientRequests = client.getClientRequests();
		ContentResponse serverResponse = clientRequests.send(request);
		return switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				JsonNode body = DigitalOceans.getResponseBody(client, serverResponse);
				yield CreateResult.created(Cluster.getByJson(client, body.get("kubernetes_cluster")));
			}
			case UNPROCESSABLE_ENTITY_422 ->
			{
				// Example: "a cluster with this name already exists"
				JsonNode json = DigitalOceans.getResponseBody(client, serverResponse);
				String message = json.get("message").textValue();
				if (message.equals("a cluster with this name already exists"))
					yield CreateResult.conflictedWith(Cluster.getByName(client, name));
				throw new AssertionError(
					"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
						"Request: " + clientRequests.toString(request));
			}
			default -> throw new AssertionError(
				"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
					"Request: " + clientRequests.toString(request));
		};
	}

	/**
	 * Copies unchangeable properties from an existing cluster into this configuration. Certain properties
	 * cannot be altered once the cluster is created, and this method ensures these properties are retained.
	 *
	 * @param existingCluster the existing cluster
	 * @throws NullPointerException if {@code existingCluster} is null
	 */
	public void copyUnchangeablePropertiesFrom(Cluster existingCluster)
	{
		clusterSubnet(existingCluster.getClusterSubnet());
		serviceSubnet(existingCluster.getServiceSubnet());
		vpc(existingCluster.getVpc());
		nodePools(existingCluster.getNodePools().stream().map(NodePool::toConfiguration).toList());
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ClusterConfiguration.class).
			add("name", name).
			add("zone", zone).
			add("version", version).
			add("clusterSubnet", clusterSubnet).
			add("serviceSubnet", serviceSubnet).
			add("vpc", vpc).
			add("tags", tags).
			add("nodePools", nodePools).
			add("maintenanceWindow", maintenanceWindow).
			add("autoUpgrade", autoUpgrade).
			add("surgeUpgrade", surgeUpgrade).
			add("highAvailability", highAvailability).
			add("registryEnabled", registryEnabled).
			toString();
	}
}