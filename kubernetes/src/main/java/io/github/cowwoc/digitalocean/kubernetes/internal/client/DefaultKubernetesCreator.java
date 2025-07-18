package io.github.cowwoc.digitalocean.kubernetes.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cowwoc.digitalocean.compute.resource.DropletType;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;
import io.github.cowwoc.digitalocean.core.util.CreateResult;
import io.github.cowwoc.digitalocean.kubernetes.resource.Kubernetes;
import io.github.cowwoc.digitalocean.kubernetes.resource.Kubernetes.MaintenanceSchedule;
import io.github.cowwoc.digitalocean.kubernetes.resource.KubernetesCreator;
import io.github.cowwoc.digitalocean.kubernetes.resource.KubernetesVersion;
import io.github.cowwoc.digitalocean.network.internal.resource.NetworkParser;
import io.github.cowwoc.digitalocean.network.resource.Region;
import io.github.cowwoc.digitalocean.network.resource.Vpc;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.cowwoc.digitalocean.core.internal.client.AbstractInternalClient.REST_SERVER;
import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.UNPROCESSABLE_ENTITY_422;

public final class DefaultKubernetesCreator implements KubernetesCreator
{
	private final DefaultKubernetesClient client;
	private final String name;
	private final Region.Id region;
	private final KubernetesVersion version;
	private final Set<String> tags = new LinkedHashSet<>();
	private final Set<NodePoolBuilder> nodePools;
	private final NetworkParser networkParser;
	private String clusterSubnet = "";
	private String serviceSubnet = "";
	private Vpc.Id vpc;
	private MaintenanceSchedule maintenanceSchedule;
	private boolean autoUpgrade;
	private boolean surgeUpgrade;
	private boolean highAvailability;

	/**
	 * Creates a new instance.
	 *
	 * @param client    the client configuration
	 * @param name      the name of the cluster. Names are case-insensitive.
	 * @param region    the region to deploy the cluster into
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
	public DefaultKubernetesCreator(DefaultKubernetesClient client, String name, Region.Id region,
		KubernetesVersion version, Set<NodePoolBuilder> nodePools)
	{
		requireThat(client, "client").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(region, "region").isNotNull();
		requireThat(version, "version").isNotNull();
		requireThat(nodePools, "nodePools").isNotEmpty();
		this.client = client;
		this.name = name;
		this.region = region;
		this.version = version;
		this.nodePools = new HashSet<>(nodePools);
		this.networkParser = new NetworkParser(client);
	}

	@Override
	public String name()
	{
		return name;
	}

	@Override
	public Region.Id region()
	{
		return region;
	}

	@Override
	public KubernetesVersion version()
	{
		return version;
	}

	@Override
	public KubernetesCreator clusterSubnet(String clusterSubnet)
	{
		requireThat(clusterSubnet, "clusterSubnet").isStripped().isNotEmpty();
		this.clusterSubnet = clusterSubnet;
		return this;
	}

	@Override
	public String clusterSubnet()
	{
		return clusterSubnet;
	}

	@Override
	public KubernetesCreator serviceSubnet(String serviceSubnet)
	{
		requireThat(serviceSubnet, "serviceSubnet").isStripped().isNotEmpty();
		this.serviceSubnet = serviceSubnet;
		return this;
	}

	@Override
	public String serviceSubnet()
	{
		return serviceSubnet;
	}

	@Override
	public KubernetesCreator vpc(Vpc.Id vpc)
	{
		this.vpc = vpc;
		return this;
	}

	@Override
	public Vpc.Id vpc()
	{
		return vpc;
	}

	@Override
	public KubernetesCreator tag(String tag)
	{
		requireThat(tag, "tag").isStripped().isNotEmpty();
		tags.add(tag);
		return this;
	}

	@Override
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

	@Override
	public Set<String> tags()
	{
		return tags;
	}

	@Override
	public KubernetesCreator nodePools(Set<NodePoolBuilder> nodePools)
	{
		requireThat(nodePools, "nodePools").isNotNull();
		this.nodePools.clear();
		this.nodePools.addAll(nodePools);
		return this;
	}

	@Override
	public Set<NodePoolBuilder> nodePools()
	{
		return nodePools;
	}

	@Override
	public KubernetesCreator maintenanceSchedule(MaintenanceSchedule maintenanceSchedule)
	{
		requireThat(maintenanceSchedule, "maintenanceSchedule").isNotNull();
		this.maintenanceSchedule = maintenanceSchedule;
		return this;
	}

	@Override
	public MaintenanceSchedule maintenanceSchedule()
	{
		return maintenanceSchedule;
	}

	@Override
	public KubernetesCreator autoUpgrade(boolean autoUpgrade)
	{
		this.autoUpgrade = autoUpgrade;
		return this;
	}

	@Override
	public boolean autoUpgrade()
	{
		return autoUpgrade;
	}

	@Override
	public KubernetesCreator surgeUpgrade(boolean surgeUpgrade)
	{
		this.surgeUpgrade = surgeUpgrade;
		return this;
	}

	@Override
	public boolean surgeUpgrade()
	{
		return surgeUpgrade;
	}

	@Override
	public KubernetesCreator highAvailability(boolean highAvailability)
	{
		this.highAvailability = highAvailability;
		return this;
	}

	@Override
	public boolean highAvailability()
	{
		return highAvailability;
	}

	@Override
	public CreateResult<Kubernetes> create() throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Kubernetes/operation/kubernetes_create_cluster
		JsonMapper jm = client.getJsonMapper();
		KubernetesParser k8sParser = client.getParser();
		ObjectNode requestBody = jm.createObjectNode().
			put("name", name).
			put("region", networkParser.regionIdToServer(region)).
			put("version", k8sParser.kubernetesVersionToServer(version));
		ArrayNode nodePoolsNode = requestBody.putArray("node_pools");
		for (NodePoolBuilder pool : nodePools)
			nodePoolsNode.add(k8sParser.nodePoolToServer(pool));
		if (!clusterSubnet.isEmpty())
			requestBody.put("clusterSubnet", clusterSubnet);
		if (!serviceSubnet.isEmpty())
			requestBody.put("serviceSubnet", serviceSubnet);
		if (vpc != null)
			requestBody.put("vpc_uuid", vpc.getValue());
		if (maintenanceSchedule != null)
			requestBody.set("maintenance_policy", k8sParser.maintenanceScheduleToServer(maintenanceSchedule));
		if (autoUpgrade)
			requestBody.put("auto_upgrade", true);
		if (surgeUpgrade)
			requestBody.put("surge_upgrade", true);
		if (highAvailability)
			requestBody.put("ha", true);
		Request request = client.createRequest(REST_SERVER.resolve("v2/kubernetes/clusters"), requestBody).
			method(POST);
		Response serverResponse = client.send(request);
		return switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				ContentResponse contentResponse = (ContentResponse) serverResponse;
				JsonNode body = client.getResponseBody(contentResponse);
				yield CreateResult.created(k8sParser.kubernetesFromServer(body.get("kubernetes_cluster")));
			}
			case UNPROCESSABLE_ENTITY_422 ->
			{
				// Example: "a cluster with this name already exists"
				ContentResponse contentResponse = (ContentResponse) serverResponse;
				JsonNode json = client.getResponseBody(contentResponse);
				String message = json.get("message").textValue();
				if (message.equals("a cluster with this name already exists"))
				{
					Kubernetes conflict = client.getKubernetesCluster(cluster -> cluster.getName().equals(name));
					if (conflict != null)
						yield CreateResult.conflictedWith(conflict);
				}
				throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
					"Request: " + client.toString(request));
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		};
	}

	@Override
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
		return new ToStringBuilder(DefaultKubernetesCreator.class).
			add("name", name).
			add("region", region).
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

	public static final class DefaultNodePoolBuilder implements NodePoolBuilder
	{
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
		public DefaultNodePoolBuilder(String name, DropletType.Id dropletType, int initialNumberOfNodes)
		{
			requireThat(name, "name").isStripped().isNotEmpty();
			requireThat(dropletType, "dropletType").isNotNull();
			requireThat(initialNumberOfNodes, "initialNumberOfNodes").isPositive();

			this.name = name;
			this.dropletType = dropletType;
			this.initialNumberOfNodes = initialNumberOfNodes;
		}

		@Override
		public DropletType.Id dropletType()
		{
			return dropletType;
		}

		@Override
		public String name()
		{
			return name;
		}

		@Override
		public int initialNumberOfNodes()
		{
			return initialNumberOfNodes;
		}

		@Override
		public NodePoolBuilder tag(String tag)
		{
			requireThat(tag, "tag").isStripped().isNotEmpty();
			this.tags.add(tag);
			return this;
		}

		@Override
		public NodePoolBuilder tags(Collection<String> tags)
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

		@Override
		public Set<String> tags()
		{
			return Set.copyOf(tags);
		}

		@Override
		public NodePoolBuilder label(String label)
		{
			requireThat(label, "label").isStripped().isNotEmpty();
			this.labels.add(label);
			return this;
		}

		@Override
		public NodePoolBuilder labels(Collection<String> labels)
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

		@Override
		public Set<String> labels()
		{
			return Set.copyOf(labels);
		}

		@Override
		public NodePoolBuilder taint(String taint)
		{
			requireThat(taint, "taint").isStripped().isNotEmpty();
			this.taints.add(taint);
			return this;
		}

		@Override
		public NodePoolBuilder taints(Collection<String> taints)
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

		@Override
		public Set<String> taints()
		{
			return Set.copyOf(taints);
		}

		@Override
		public NodePoolBuilder autoscale(int minNodes, int maxNodes)
		{
			requireThat(minNodes, "minNodes").isLessThanOrEqualTo(maxNodes, "maxNodes");
			this.autoScale = true;
			this.minNodes = minNodes;
			this.maxNodes = maxNodes;
			return this;
		}

		@Override
		public boolean autoScale()
		{
			return autoScale;
		}

		@Override
		public int minNodes()
		{
			return minNodes;
		}

		@Override
		public int maxNodes()
		{
			return maxNodes;
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
			return o instanceof NodePoolBuilder other && other.name().equals(name) &&
				other.dropletType().equals(dropletType) && other.initialNumberOfNodes() == initialNumberOfNodes &&
				other.tags().equals(tags) && other.labels().equals(labels) && other.taints().equals(taints) &&
				other.autoScale() == autoScale && other.minNodes() == minNodes && other.maxNodes() == maxNodes;
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(NodePoolBuilder.class).
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