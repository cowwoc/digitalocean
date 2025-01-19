package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import com.github.cowwoc.digitalocean.resource.Kubernetes.MaintenanceSchedule;
import com.github.cowwoc.digitalocean.util.CreateResult;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.UNPROCESSABLE_ENTITY_422;

/**
 * The desired state of a Kubernetes cluster.
 */
public final class KubernetesCreator
{
	private final DigitalOceanClient client;
	private final String name;
	private final Zone.Id zone;
	private final KubernetesVersion version;
	private String clusterSubnet = "";
	private String serviceSubnet = "";
	private Vpc.Id vpc;
	private final Set<String> tags = new LinkedHashSet<>();
	private final Set<NodePool> nodePools;
	private MaintenanceSchedule maintenanceSchedule;
	private boolean autoUpgrade;
	private boolean surgeUpgrade;
	private boolean highAvailability;

	/**
	 * Creates a new instance.
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
	KubernetesCreator(DigitalOceanClient client, String name, Zone.Id zone, KubernetesVersion version,
		Set<NodePool> nodePools)
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
		this.nodePools = new HashSet<>(nodePools);
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
	public Zone.Id zone()
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
	public KubernetesCreator clusterSubnet(String clusterSubnet)
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
	public KubernetesCreator serviceSubnet(String serviceSubnet)
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
	 * @param vpc null to use the zone's default VPC
	 * @return this
	 * @see Vpc#getDefault(DigitalOceanClient, Zone.Id)
	 */
	public KubernetesCreator vpc(Vpc.Id vpc)
	{
		this.vpc = vpc;
		return this;
	}

	/**
	 * Returns the VPC that the cluster will use.
	 *
	 * @return null if the zone's default VPC will be used
	 */
	public Vpc.Id vpc()
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
	public KubernetesCreator tag(String tag)
	{
		requireThat(tag, "tag").isStripped().isNotEmpty();
		tags.add(tag);
		return this;
	}

	/**
	 * Sets the tags of the cluster.
	 *
	 * @param tags the tags
	 * @return this
	 * @throws NullPointerException     if {@code tags} is null
	 * @throws IllegalArgumentException if any of the tags contains leading or trailing whitespace or are empty
	 */
	public KubernetesCreator tags(Set<String> tags)
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
	 * Returns the tags of the cluster.
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
	public KubernetesCreator nodePools(Set<NodePool> nodePools)
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
	public Set<NodePool> nodePools()
	{
		return nodePools;
	}

	/**
	 * Sets the schedule of the cluster's maintenance schedule.
	 *
	 * @param maintenanceSchedule the maintenance schedule
	 * @return this
	 * @throws NullPointerException if {@code maintenanceSchedule} is null
	 */
	public KubernetesCreator maintenanceSchedule(MaintenanceSchedule maintenanceSchedule)
	{
		requireThat(maintenanceSchedule, "maintenanceSchedule").isNotNull();
		this.maintenanceSchedule = maintenanceSchedule;
		return this;
	}

	/**
	 * Returns the schedule of the cluster's maintenance schedule.
	 *
	 * @return the maintenance schedule
	 */
	public MaintenanceSchedule maintenanceSchedule()
	{
		return maintenanceSchedule;
	}

	/**
	 * Determines the cluster's auto-upgrade policy. By default, this property is {@code false}.
	 *
	 * @param autoUpgrade {@code true} if the cluster will be automatically upgraded to new patch releases
	 *                    during its maintenance schedule.
	 * @return this
	 */
	public KubernetesCreator autoUpgrade(boolean autoUpgrade)
	{
		this.autoUpgrade = autoUpgrade;
		return this;
	}

	/**
	 * Determines if the cluster will be automatically upgraded to new patch releases during its maintenance
	 * schedule.
	 *
	 * @return {@code true} if the cluster will be automatically upgraded to new patch releases during its
	 * 	maintenance schedule.
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
	public KubernetesCreator surgeUpgrade(boolean surgeUpgrade)
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
	public KubernetesCreator highAvailability(boolean highAvailability)
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
	 * Creates a new cluster.
	 *
	 * @return the new or conflicting cluster
	 * @throws IllegalArgumentException if a cluster with this name already exists
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public CreateResult<Kubernetes> create()
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_create_cluster
		JsonMapper jm = client.getJsonMapper();
		ObjectNode requestBody = jm.createObjectNode().
			put("name", name).
			put("region", zone.getValue()).
			put("version", version.toJson());
		ArrayNode nodePoolsNode = requestBody.putArray("node_pools");
		for (NodePool pool : nodePools)
			nodePoolsNode.add(pool.toJson());
		if (!clusterSubnet.isEmpty())
			requestBody.put("clusterSubnet", clusterSubnet);
		if (!serviceSubnet.isEmpty())
			requestBody.put("serviceSubnet", serviceSubnet);
		if (vpc != null)
			requestBody.put("vpc_uuid", vpc.getValue());
		if (maintenanceSchedule != null)
			requestBody.set("maintenance_policy", maintenanceSchedule.toJson());
		if (autoUpgrade)
			requestBody.put("auto_upgrade", true);
		if (surgeUpgrade)
			requestBody.put("surge_upgrade", true);
		if (highAvailability)
			requestBody.put("ha", true);
		Request request = client.createRequest(REST_SERVER.resolve("v2/kubernetes/clusters"), requestBody).
			method(POST);
		ContentResponse serverResponse = client.send(request);
		return switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				JsonNode body = client.getResponseBody(serverResponse);
				yield CreateResult.created(Kubernetes.getByJson(client, body.get("kubernetes_cluster")));
			}
			case UNPROCESSABLE_ENTITY_422 ->
			{
				// Example: "a cluster with this name already exists"
				JsonNode json = client.getResponseBody(serverResponse);
				String message = json.get("message").textValue();
				if (message.equals("a cluster with this name already exists"))
				{
					Kubernetes conflict = Kubernetes.getByPredicate(client, cluster -> cluster.getName().equals(name));
					if (conflict != null)
						yield CreateResult.conflictedWith(conflict);
				}
				throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
					"Request: " + client.toString(request));
			}
			default -> throw new AssertionError(
				"Unexpected response: " + client.toString(serverResponse) + "\n" +
					"Request: " + client.toString(request));
		};
	}

	/**
	 * Copies unchangeable properties from an existing cluster into this configuration. Certain properties
	 * cannot be altered once the cluster is created, and this method ensures these properties are retained.
	 *
	 * @param existingCluster the existing cluster
	 * @throws NullPointerException if {@code existingCluster} is null
	 */
	public void copyUnchangeablePropertiesFrom(Kubernetes existingCluster)
	{
		clusterSubnet(existingCluster.getClusterSubnet());
		serviceSubnet(existingCluster.getServiceSubnet());
		vpc(existingCluster.getVpc());
		nodePools(existingCluster.getNodePools().stream().map(Kubernetes.NodePool::forCreator).
			collect(Collectors.toSet()));
		// Add auto-generated tags
		tag("k8s");
		tag("k8s:" + existingCluster.getId().getValue());
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(KubernetesCreator.class).
			add("name", name).
			add("zone", zone).
			add("version", version).
			add("clusterSubnet", clusterSubnet).
			add("serviceSubnet", serviceSubnet).
			add("vpc", vpc).
			add("tags", tags).
			add("nodePools", nodePools).
			add("maintenanceSchedule", maintenanceSchedule).
			add("autoUpgrade", autoUpgrade).
			add("surgeUpgrade", surgeUpgrade).
			add("highAvailability", highAvailability).
			toString();
	}

	/**
	 * A pool of worker nodes.
	 */
	public static final class NodePool
	{
		private final DigitalOceanClient client;
		private final String name;
		private final DropletType.Id dropletType;
		private final int initialNumberOfNodes;
		private final Set<String> tags = new LinkedHashSet<>();
		private final Set<String> labels = new LinkedHashSet<>();
		private final Set<String> taints = new LinkedHashSet<>();
		private boolean autoScale;
		private int minNodes;
		private int maxNodes;

		/**
		 * Creates a new node pool.
		 *
		 * @param client               the client configuration
		 * @param name                 the name of the node pool
		 * @param dropletType          the type of droplets to use for cluster nodes
		 * @param initialNumberOfNodes the initial number of nodes to populate the pool with
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                    <li>any of the arguments contain leading or trailing whitespace or
		 *                                    are empty.</li>
		 *                                    <li>{@code initialNumberOfNodes} is negative or zero.</li>
		 *                                  </ul>
		 */
		public NodePool(DigitalOceanClient client, String name, DropletType.Id dropletType,
			int initialNumberOfNodes)
		{
			requireThat(client, "client").isNotNull();
			requireThat(name, "name").isStripped().isNotEmpty();
			requireThat(dropletType, "dropletType").isNotNull();
			requireThat(initialNumberOfNodes, "initialNumberOfNodes").isPositive();

			this.client = client;
			this.name = name;
			this.dropletType = dropletType;
			this.initialNumberOfNodes = initialNumberOfNodes;
		}

		/**
		 * Returns the type of droplets to use for cluster nodes.
		 *
		 * @return the type of droplets to use for cluster nodes
		 */
		public DropletType.Id dropletType()
		{
			return dropletType;
		}

		/**
		 * Returns the name of the node pool.
		 *
		 * @return the name of the node pool
		 */
		public String name()
		{
			return name;
		}

		/**
		 * Returns the initial number of nodes to populate the pool with.
		 *
		 * @return the initial number of nodes
		 */
		public int initialNumberOfNodes()
		{
			return initialNumberOfNodes;
		}

		/**
		 * Adds a tag to the pool.
		 *
		 * @param tag the tag to add
		 * @return this
		 * @throws NullPointerException     if {@code tag} is null
		 * @throws IllegalArgumentException if the tag contains leading or trailing whitespace or is empty
		 */
		public NodePool tag(String tag)
		{
			requireThat(tag, "tag").isStripped().isNotEmpty();
			this.tags.add(tag);
			return this;
		}

		/**
		 * Sets the tags of the node pool.
		 *
		 * @param tags the tags
		 * @return this
		 * @throws NullPointerException     if {@code tags} is null
		 * @throws IllegalArgumentException if any of the tags are null, contain leading or trailing whitespace or
		 *                                  are empty
		 */
		public NodePool tags(Collection<String> tags)
		{
			requireThat(tags, "tags").isNotNull().doesNotContain(null);
			this.tags.clear();
			for (String tag : tags)
			{
				requireThat(tag, "tag").withContext(tags, "tags").isStripped().isNotEmpty();
				this.tags.add(tag);
			}
			return this;
		}

		/**
		 * Returns the pool's tags.
		 *
		 * @return the tags
		 */
		public Set<String> tags()
		{
			return Set.copyOf(tags);
		}

		/**
		 * Adds a label to the pool.
		 *
		 * @param label the label to add
		 * @return this
		 * @throws NullPointerException     if {@code label} is null
		 * @throws IllegalArgumentException if the label contains leading or trailing whitespace or is empty
		 */
		public NodePool label(String label)
		{
			requireThat(label, "label").isStripped().isNotEmpty();
			this.labels.add(label);
			return this;
		}

		/**
		 * Sets the labels of the node pool.
		 *
		 * @param labels the labels
		 * @return this
		 * @throws NullPointerException     if {@code labels} is null
		 * @throws IllegalArgumentException if any of the labels are null, contain leading or trailing whitespace
		 *                                  or are empty
		 */
		public NodePool labels(Collection<String> labels)
		{
			requireThat(labels, "labels").isNotNull().doesNotContain(null);
			this.labels.clear();
			for (String label : labels)
			{
				requireThat(label, "label").withContext(labels, "labels").isStripped().isNotEmpty();
				this.labels.add(label);
			}
			return this;
		}

		/**
		 * Returns the pool's labels.
		 *
		 * @return the labels
		 */
		public Set<String> labels()
		{
			return Set.copyOf(labels);
		}

		/**
		 * Adds a taint to the pool.
		 *
		 * @param taint the taint to add
		 * @return this
		 * @throws NullPointerException     if {@code taint} is null
		 * @throws IllegalArgumentException if the taint contains leading or trailing whitespace or is empty
		 */
		public NodePool taint(String taint)
		{
			requireThat(taint, "taint").isStripped().isNotEmpty();
			this.taints.add(taint);
			return this;
		}

		/**
		 * Sets the taints of the node pool.
		 *
		 * @param taints the taints
		 * @return this
		 * @throws NullPointerException     if {@code taints} is null
		 * @throws IllegalArgumentException if any of the taints are null, contain leading or trailing whitespace
		 *                                  or are empty
		 */
		public NodePool taints(Collection<String> taints)
		{
			requireThat(taints, "taints").isNotNull().doesNotContain(null);
			this.taints.clear();
			for (String taint : taints)
			{
				requireThat(taint, "taint").withContext(taints, "taints").isStripped().isNotEmpty();
				this.taints.add(taint);
			}
			return this;
		}

		/**
		 * Returns the pool's taints.
		 *
		 * @return the taints
		 */
		public Set<String> taints()
		{
			return Set.copyOf(taints);
		}

		/**
		 * Configures the pool size to adjust automatically to meet demand.
		 *
		 * @param minNodes the minimum number of nodes in the pool
		 * @param maxNodes the maximum number of nodes in the pool
		 * @return this
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                    <li>{@code minNodes} is greater than
		 *                                    {@link #initialNumberOfNodes()}</li>
		 *                                    <li>{@code maxNodes} is less than
		 *                                    {@link #initialNumberOfNodes()}</li>
		 *                                  </ul>
		 */
		public NodePool autoscale(int minNodes, int maxNodes)
		{
			requireThat(minNodes, "minNodes").isLessThanOrEqualTo(maxNodes, "maxNodes");
			this.autoScale = true;
			this.minNodes = minNodes;
			this.maxNodes = maxNodes;
			return this;
		}

		/**
		 * Indicates if the pool size should adjust automatically to meet demand.
		 *
		 * @return {@code true} if the pool size should adjust automatically to meet demand
		 */
		public boolean autoScale()
		{
			return autoScale;
		}

		/**
		 * Returns the minimum number of nodes in the pool.
		 *
		 * @return the minimum number of nodes in the pool
		 */
		public int minNodes()
		{
			return minNodes;
		}

		/**
		 * Returns the maximum number of nodes in the pool.
		 *
		 * @return the maximum number of nodes in the pool
		 */
		public int maxNodes()
		{
			return maxNodes;
		}

		/**
		 * Returns the JSON representation of this object.
		 *
		 * @return the JSON representation
		 * @throws IllegalStateException if the client is closed
		 */
		public ObjectNode toJson()
		{
			ObjectNode json = client.getJsonMapper().createObjectNode().
				put("size", dropletType.getValue()).
				put("name", name).
				put("count", initialNumberOfNodes);
			if (!tags.isEmpty())
			{
				ArrayNode array = json.putArray("tags");
				for (String tag : tags)
					array.add(tag);
			}
			if (!labels.isEmpty())
			{
				ArrayNode array = json.putArray("labels");
				for (String label : labels)
					array.add(label);
			}
			if (!taints.isEmpty())
			{
				ArrayNode array = json.putArray("taints");
				for (String taint : taints)
					array.add(taint);
			}
			if (autoScale)
			{
				json.put("auto_scale", true);
				json.put("min_nodes", minNodes);
				json.put("max_nodes", maxNodes);
			}
			return json;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(name, dropletType, initialNumberOfNodes, tags, labels, taints, autoScale, minNodes,
				maxNodes);
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof NodePool other && other.name.equals(name) &&
				other.dropletType.equals(dropletType) && other.initialNumberOfNodes == initialNumberOfNodes &&
				other.tags.equals(tags) && other.labels.equals(labels) && other.taints.equals(taints) &&
				other.autoScale == autoScale && other.minNodes == minNodes && other.maxNodes == maxNodes;
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(NodePool.class).
				add("name", name).
				add("type", dropletType).
				add("initialNumberOfNodes", initialNumberOfNodes).
				add("tags", tags).
				add("labels", labels).
				add("taints", taints).
				add("autoScale", autoScale).
				add("minNodes", minNodes).
				add("maxNodes", maxNodes).
				toString();
		}
	}
}