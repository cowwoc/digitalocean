package com.github.cowwoc.digitalocean.resource.kubernetes;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.exception.KubernetesClusterNotFoundException;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import com.github.cowwoc.digitalocean.internal.util.DigitalOceans;
import com.github.cowwoc.digitalocean.internal.util.RetryDelay;
import com.github.cowwoc.digitalocean.internal.util.TimeLimit;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import com.github.cowwoc.digitalocean.resource.DropletType;
import com.github.cowwoc.digitalocean.resource.Vpc;
import com.github.cowwoc.digitalocean.resource.Zone;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.util.DigitalOceans.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.DELETE;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.PUT;
import static org.eclipse.jetty.http.HttpStatus.ACCEPTED_202;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

/**
 * A Kubernetes cluster.
 */
public final class Cluster
{
	static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("H:mm");

	/**
	 * Creates a new cluster configuration.
	 *
	 * @param client    the client configuration
	 * @param name      the name of the node pool
	 * @param zone      the zone that the droplet is deployed in
	 * @param version   the software version of Kubernetes
	 * @param nodePools the node pools that are deployed in the cluster
	 * @return the configuration
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	public static ClusterConfiguration configuration(DigitalOceanClient client, String name,
		Zone zone, KubernetesVersion version, List<NodePoolConfiguration> nodePools)
	{
		return new ClusterConfiguration(client, name, zone, version, nodePools);
	}

	/**
	 * Returns the clusters that are deployed in a zone.
	 *
	 * @param client the client configuration
	 * @param zone   the zone
	 * @return the clusters
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static List<Cluster> getByZone(DigitalOceanClient client, Zone zone)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(zone, "zone").isNotNull();
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_list_clusters
		String uri = REST_SERVER + "/v2/kubernetes/clusters";
		return DigitalOceans.getElements(client, uri, Map.of(), body ->
		{
			JsonNode clusterNodes = body.get("kubernetes_clusters");
			if (clusterNodes == null)
				throw new JsonMappingException(null, "kubernetes_clusters must be set");
			List<Cluster> matches = new ArrayList<>();
			for (JsonNode cluster : clusterNodes)
			{
				Zone actualZone = Zone.getBySlug(cluster.get("region").textValue());
				if (actualZone.equals(zone))
					matches.add(getByJson(client, cluster));
			}
			return matches;
		});
	}

	/**
	 * Looks up a cluster by its name.
	 *
	 * @param client the client configuration
	 * @param name   the name
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static Cluster getByName(DigitalOceanClient client, String name)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_list_clusters
		String uri = REST_SERVER + "/v2/kubernetes/clusters";
		return DigitalOceans.getElement(client, uri, Map.of(), body ->
		{
			JsonNode clusterNodes = body.get("kubernetes_clusters");
			if (clusterNodes == null)
				throw new JsonMappingException(null, "kubernetes_clusters must be set");
			for (JsonNode cluster : clusterNodes)
			{
				String actualName = cluster.get("name").textValue();
				if (actualName.equals(name))
					return getByJson(client, cluster);
			}
			return null;
		});
	}

	/**
	 * @param client the client configuration
	 * @param json   the JSON representation of the cluster
	 * @return the cluster
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	static Cluster getByJson(DigitalOceanClient client, JsonNode json)
		throws IOException, InterruptedException, TimeoutException
	{
		String id = json.get("id").textValue();
		String name = json.get("name").textValue();
		Zone zone = Zone.getBySlug(json.get("region").textValue());
		KubernetesVersion version = KubernetesVersion.getBySlug(json.get("version").textValue());
		String clusterSubnet = json.get("cluster_subnet").textValue();
		String serviceSubnet = json.get("service_subnet").textValue();
		Vpc vpc = Vpc.getById(client, json.get("vpc_uuid").textValue());
		String ipv4;
		JsonNode ipv4Node = json.get("ipv4");
		if (ipv4Node == null)
			ipv4 = "";
		else
			ipv4 = ipv4Node.textValue();
		String endpoint = json.get("endpoint").textValue();

		Set<String> tags = new LinkedHashSet<>();
		JsonNode tagsNode = json.get("tags");
		for (JsonNode tag : tagsNode)
			tags.add(tag.textValue());

		JsonNode nodePoolsNode = json.get("node_pools");
		List<NodePool> nodePools = new ArrayList<>();
		for (JsonNode node : nodePoolsNode)
			nodePools.add(NodePool.getByJson(client, node));

		MaintenanceWindow maintenanceWindow = MaintenanceWindow.getByJson(client, json.get("maintenance_policy"));
		boolean autoUpgrade = DigitalOceans.toBoolean(json, "auto_upgrade");
		Status status = Status.getByJson(json.get("status"));

		Instant createdAt = Instant.parse(json.get("created_at").textValue());
		Instant updatedAt = Instant.parse(json.get("updated_at").textValue());
		boolean surgeUpgrade = DigitalOceans.toBoolean(json, "surge_upgrade");
		boolean ha = DigitalOceans.toBoolean(json, "ha");
		boolean registryEnabled = DigitalOceans.toBoolean(json, "registry_enabled");
		return new Cluster(client, id, name, zone, version, clusterSubnet, serviceSubnet, vpc, ipv4,
			endpoint, tags, nodePools, maintenanceWindow, autoUpgrade, status, surgeUpgrade, ha, registryEnabled,
			createdAt, updatedAt);
	}

	private final DigitalOceanClient client;
	private final String id;
	private final String name;
	private final Zone zone;
	private final KubernetesVersion version;
	private final String clusterSubnet;
	private final String serviceSubnet;
	private final Vpc vpc;
	private final String ipv4;
	private final String endpoint;
	private final Set<String> tags;
	private final List<NodePool> nodePools;
	private final MaintenanceWindow maintenanceWindow;
	private final boolean autoUpgrade;
	private final Status status;
	private final boolean surgeUpgrade;
	private final boolean highAvailability;
	private final boolean registryEnabled;
	private final Instant createdAt;
	private final Instant updatedAt;
	private final Logger log = LoggerFactory.getLogger(Cluster.class);

	/**
	 * Creates a new Kubernetes cluster.
	 *
	 * @param client            the client configuration
	 * @param id                the ID of the cluster
	 * @param name              the name of the cluster
	 * @param zone              the zone that the droplet is deployed in
	 * @param version           the software version of Kubernetes
	 * @param clusterSubnet     the range of IP addresses for the overlay network of the Kubernetes cluster, in
	 *                          CIDR notation
	 * @param serviceSubnet     the range of IP addresses for the services running in the Kubernetes cluster, in
	 *                          CIDR notation
	 * @param vpc               the VPC that the cluster is deployed in
	 * @param ipv4              (optional) the public IPv4 address of the Kubernetes control plane or an empty
	 *                          string if not set. This value will not be set if high availability is configured
	 *                          on the cluster.
	 * @param endpoint          the base URL of the API server
	 * @param tags              the tags that are associated with the cluster
	 * @param nodePools         the node pools that are deployed in the cluster
	 * @param maintenanceWindow the maintenance window policy for the cluster
	 * @param autoUpgrade       {@code true} if the cluster will be automatically upgraded to new patch releases
	 *                          during its maintenance window
	 * @param status            the status of the cluster
	 * @param surgeUpgrade      {@code true} if new nodes should be deployed before destroying the outdated
	 *                          nodes
	 * @param highAvailability  {@code true} if the control plane should run in a highly available
	 *                          configuration
	 * @param registryEnabled   {@code true} if a container registry is integrated with the cluster
	 * @param createdAt         the time the cluster was created
	 * @param updatedAt         the time the cluster was last updated
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace</li>
	 *                                    <li>any of the mandatory arguments are empty</li>
	 *                                  </ul>
	 * @see Vpc#getDefault(DigitalOceanClient, Zone)
	 */
	private Cluster(DigitalOceanClient client, String id, String name, Zone zone,
		KubernetesVersion version, String clusterSubnet, String serviceSubnet, Vpc vpc, String ipv4,
		String endpoint, Set<String> tags, List<NodePool> nodePools, MaintenanceWindow maintenanceWindow,
		boolean autoUpgrade, Status status, boolean surgeUpgrade, boolean highAvailability,
		boolean registryEnabled, Instant createdAt, Instant updatedAt)
	{
		requireThat(client, "client").isNotNull();
		requireThat(id, "id").isStripped().isNotEmpty();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(zone, "zone").isNotNull();
		requireThat(version, "version").isNotNull();
		requireThat(clusterSubnet, "clusterSubnet").isNotNull();
		requireThat(serviceSubnet, "serviceSubnet").isNotNull();
		requireThat(vpc, "vpc").isNotNull();
		requireThat(ipv4, "ipv4").isNotNull();
		requireThat(endpoint, "endpoint").isNotNull();
		requireThat(tags, "tags").isNotNull();
		requireThat(nodePools, "nodePools").isNotNull();
		requireThat(maintenanceWindow, "maintenanceWindow").isNotNull();
		requireThat(status, "status").isNotNull();
		requireThat(createdAt, "createdAt").isNotNull();
		requireThat(updatedAt, "updatedAt").isNotNull();
		this.client = client;
		this.id = id;
		this.name = name;
		this.zone = zone;
		this.version = version;
		this.clusterSubnet = clusterSubnet;
		this.serviceSubnet = serviceSubnet;
		this.vpc = vpc;
		this.ipv4 = ipv4;
		this.endpoint = endpoint;
		this.tags = Set.copyOf(tags);
		this.nodePools = nodePools;
		this.maintenanceWindow = maintenanceWindow;
		this.autoUpgrade = autoUpgrade;
		this.status = status;
		this.surgeUpgrade = surgeUpgrade;
		this.highAvailability = highAvailability;
		this.registryEnabled = registryEnabled;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns the cluster's ID.
	 *
	 * @return the cluster's ID
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the cluster's name.
	 *
	 * @return the cluster's name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the zone that the cluster is deployed in.
	 *
	 * @return the zone that the cluster is deployed in
	 */
	public Zone getZone()
	{
		return zone;
	}

	/**
	 * Returns the version of the Kubernetes software that is deployed.
	 *
	 * @return the version of the Kubernetes software that is deployed
	 */
	public KubernetesVersion getVersion()
	{
		return version;
	}

	/**
	 * Returns the range of IP addresses for the overlay network of the Kubernetes cluster, in CIDR notation.
	 *
	 * @return the range of IP addresses for the overlay network
	 */
	public String getClusterSubnet()
	{
		return clusterSubnet;
	}

	/**
	 * Returns the range of IP addresses for the services running in the Kubernetes cluster, in CIDR notation.
	 *
	 * @return the range of IP addresses for the services
	 */
	public String getServiceSubnet()
	{
		return serviceSubnet;
	}

	/**
	 * Returns the VPC that the cluster is deployed in.
	 *
	 * @return the VPC that the cluster is deployed in
	 */
	public Vpc getVpc()
	{
		return vpc;
	}

	/**
	 * Returns the public IPv4 address of the Kubernetes control plane. This value will not be set if high
	 * availability is configured on the cluster.
	 *
	 * @return an empty string if not set
	 */
	public String getIpv4()
	{
		return ipv4;
	}

	/**
	 * Returns the base URL of the API server.
	 *
	 * @return the base URL of the API server
	 */
	public String getEndpoint()
	{
		return endpoint;
	}

	/**
	 * Returns the tags that are associated with the cluster.
	 *
	 * @return the tags that
	 */
	public Set<String> getTags()
	{
		return tags;
	}

	/**
	 * Returns the node pools that are deployed in the cluster.
	 *
	 * @return the node pools
	 */
	public List<NodePool> getNodePools()
	{
		return nodePools;
	}

	/**
	 * Returns the maintenance window policy for the cluster.
	 *
	 * @return the maintenance window policy
	 */
	public MaintenanceWindow getMaintenanceWindow()
	{
		return maintenanceWindow;
	}

	/**
	 * Determines if the cluster will be automatically upgraded to new patch releases during its maintenance
	 * window.
	 *
	 * @return {@code true} if the cluster will be automatically upgraded
	 */
	public boolean isAutoUpgrade()
	{
		return autoUpgrade;
	}

	/**
	 * Returns the status of the cluster.
	 *
	 * @return the status
	 */
	public Status getStatus()
	{
		return status;
	}

	/**
	 * Determines if new nodes should be deployed before destroying the outdated nodes.
	 *
	 * @return {@code true} if new nodes should be deployed before destroying the outdated nodes
	 */
	public boolean isSurgeUpgrade()
	{
		return surgeUpgrade;
	}

	/**
	 * Returns {@code true} if the control plane should run in a highly available configuration.
	 *
	 * @return {@code true} if the control plane should run in a highly available configuration
	 */
	public boolean isHighAvailability()
	{
		return highAvailability;
	}

	/**
	 * Determines if a container registry is integrated with the cluster.
	 *
	 * @return {@code true} if a container registry is integrated with the cluster
	 */
	public boolean isRegistryEnabled()
	{
		return registryEnabled;
	}

	/**
	 * Returns the time the cluster was created.
	 *
	 * @return the time the cluster was created
	 */
	public Instant getCreatedAt()
	{
		return createdAt;
	}

	/**
	 * Returns the time the cluster was last updated.
	 *
	 * @return the time the cluster was last updated
	 */
	public Instant getUpdatedAt()
	{
		return updatedAt;
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Cluster.class).
			add("id", id).
			add("name", name).
			add("zone", zone).
			add("version", version).
			add("clusterSubnet", clusterSubnet).
			add("serviceSubnet", serviceSubnet).
			add("vpc", vpc).
			add("ipv4", ipv4).
			add("endpoint", endpoint).
			add("tags", tags).
			add("nodePools", nodePools).
			add("maintenanceWindow", maintenanceWindow).
			add("autoUpgrade", autoUpgrade).
			add("status", status).
			add("surgeUpgrade", surgeUpgrade).
			add("highAvailability", highAvailability).
			add("registryEnabled", registryEnabled).
			add("createdAt", createdAt).
			add("updatedAt", updatedAt).
			toString();
	}

	/**
	 * Updates the cluster to the desired configuration.
	 *
	 * @param target the desired state
	 * @throws NullPointerException               if {@code target} is null
	 * @throws IllegalArgumentException           if the server does not support applying any of the desired
	 *                                            changes
	 * @throws IllegalStateException              if the client is closed
	 * @throws IOException                        if an I/O error occurs. These errors are typically transient,
	 *                                            and retrying the request may resolve the issue.
	 * @throws TimeoutException                   if the request times out before receiving a response. This
	 *                                            might indicate network latency or server overload.
	 * @throws InterruptedException               if the thread is interrupted while waiting for a response.
	 *                                            This can happen due to shutdown signals.
	 * @throws KubernetesClusterNotFoundException if the cluster could not be found
	 * @see ClusterConfiguration#copyUnchangeablePropertiesFrom(Cluster)
	 */
	public void update(ClusterConfiguration target)
		throws IOException, TimeoutException, InterruptedException, KubernetesClusterNotFoundException
	{
		requireThat(target.zone(), "target.zone").isEqualTo(zone);
		requireThat(target.version(), "target.version").isEqualTo(version);
		requireThat(target.clusterSubnet(), "target.clusterSubnet").isEqualTo(clusterSubnet);
		requireThat(target.serviceSubnet(), "target.serviceSubnet").isEqualTo(serviceSubnet);
		requireThat(target.vpc(), "target.vpc").isEqualTo(vpc);
		requireThat(target.nodePools(), "target.nodePools").isEqualTo(nodePools.stream().
			map(NodePool::toConfiguration).toList());

		ObjectMapper om = client.getObjectMapper();
		ObjectNode requestBody = om.createObjectNode();
		requestBody.put("name", target.name());
		if (!target.tags().equals(tags))
		{
			ArrayNode tagsNode = requestBody.putArray("tags");
			for (String tag : tags)
				tagsNode.add(tag);
		}
		if (!target.maintenanceWindow().equals(maintenanceWindow))
			requestBody.set("maintenance_policy", target.maintenanceWindow().toJson());
		if (target.autoUpgrade() != autoUpgrade)
			requestBody.put("auto_upgrade", target.autoUpgrade());
		if (target.surgeUpgrade() != surgeUpgrade)
			requestBody.put("surge_upgrade", target.surgeUpgrade());
		if (target.highAvailability() != highAvailability)
			requestBody.put("ha", target.highAvailability());

		ClientRequests clientRequests = client.getClientRequests();
		// https://docs.digitalocean.com/reference/api/api-referenc /#operation/kubernetes_update_cluster
		String uri = REST_SERVER + "/v2/kubernetes/clusters/" + id;
		Request request = DigitalOceans.createRequest(client, uri, requestBody).
			method(PUT);
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case ACCEPTED_202 ->
			{
				// success
			}
			case NOT_FOUND_404 -> throw new KubernetesClusterNotFoundException(getId());
			default -> throw new AssertionError(
				"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
					"Request: " + clientRequests.toString(request));
		}
	}

	/**
	 * Blocks until the cluster reaches the desired {@code state} or a timeout occurs.
	 *
	 * @param state   the desired state
	 * @param timeout the maximum amount of time to wait
	 * @return the updated cluster
	 * @throws NullPointerException               if {@code state} is null
	 * @throws IllegalStateException              if the client is closed
	 * @throws IOException                        if an I/O error occurs. These errors are typically transient,
	 *                                            and retrying the request may resolve the issue.
	 * @throws TimeoutException                   if the operation times out before the cluster reaches the
	 *                                            desired status. This might indicate network latency or server
	 *                                            overload.
	 * @throws InterruptedException               if the thread is interrupted while waiting for a response.
	 *                                            This can happen due to shutdown signals.
	 * @throws KubernetesClusterNotFoundException if the cluster could not be found
	 * @see #waitForDeletion(Duration)
	 */
	public Cluster waitFor(State state, Duration timeout)
		throws IOException, TimeoutException, InterruptedException, KubernetesClusterNotFoundException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_get_cluster
		ClientRequests clientRequests = client.getClientRequests();
		String uri = REST_SERVER + "/v2/kubernetes/clusters/" + id;
		Request request = DigitalOceans.createRequest(client, uri).
			method(GET);
		RetryDelay retryDelay = new RetryDelay(Duration.ofSeconds(3), Duration.ofSeconds(30), 2);
		TimeLimit timeLimit = new TimeLimit(timeout);
		while (true)
		{
			ContentResponse serverResponse = clientRequests.send(request);
			switch (serverResponse.getStatus())
			{
				case OK_200 ->
				{
					// success
				}
				case NOT_FOUND_404 -> throw new KubernetesClusterNotFoundException(getId());
				default -> throw new AssertionError(
					"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
						"Request: " + clientRequests.toString(request));
			}
			JsonNode body = DigitalOceans.getResponseBody(client, serverResponse);
			Cluster newCluster = getByJson(client, body.get("kubernetes_cluster"));
			if (newCluster.getStatus().state.equals(state))
				return newCluster;
			if (!timeLimit.getTimeLeft().isPositive())
				throw new TimeoutException("Operation failed after " + timeLimit.getTimeQuota());
			log.info("Waiting for the status of {} to change from {} to {}", name, newCluster.status.state, state);
			retryDelay.sleep();
		}
	}

	/**
	 * Destroys the cluster and all of its associated resources.
	 *
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public void destroyRecursively() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_destroy_associatedResourcesDangerous
		ClientRequests clientRequests = client.getClientRequests();
		String uri = REST_SERVER + "/v2/kubernetes/clusters/" + id + "/destroy_with_associated_resources/" +
			"dangerous";
		Request request = DigitalOceans.createRequest(client, uri).
			method(DELETE);
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case NO_CONTENT_204, NOT_FOUND_404 ->
			{
				// success
			}
			default -> throw new AssertionError(
				"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
					"Request: " + clientRequests.toString(request));
		}
	}

	/**
	 * Blocks until the cluster deletion completes.
	 *
	 * @param timeout the maximum amount of time to wait
	 * @throws NullPointerException if {@code state} is null
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the operation times out before the cluster reaches the desired status.
	 *                              This might indicate network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public void waitForDeletion(Duration timeout)
		throws IOException, TimeoutException, InterruptedException
	{
		try
		{
			waitFor(State.DELETED, timeout);
		}
		catch (KubernetesClusterNotFoundException _)
		{
		}
	}

	/**
	 * A pool of worker nodes.
	 */
	public static final class NodePool
	{
		/**
		 * Creates a new {@code NodePool} configuration.
		 *
		 * @param client               the client configuration
		 * @param name                 the name of the node pool
		 * @param dropletType          the type of droplets to use for cluster nodes
		 * @param initialNumberOfNodes the initial number of nodes to populate the pool with
		 * @return a configuration
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
		 *                                  empty
		 */
		public static NodePoolConfiguration configuration(DigitalOceanClient client, String name,
			DropletType dropletType, int initialNumberOfNodes)
		{
			return new NodePoolConfiguration(client, name, dropletType, initialNumberOfNodes);
		}

		/**
		 * @param client the client configuration
		 * @param json   the JSON representation of the node pool
		 * @return the node pool
		 * @throws NullPointerException if any of the arguments are null
		 */
		private static NodePool getByJson(DigitalOceanClient client, JsonNode json)
		{
			DropletType dropletType = DropletType.getBySlug(json.get("size").textValue());
			String id = json.get("id").textValue();
			String name = json.get("name").textValue();
			int initialNumberOfNodes = DigitalOceans.toInt(json, "count");

			Set<String> tags = new LinkedHashSet<>();
			JsonNode tagsNode = json.get("tags");
			for (JsonNode tag : tagsNode)
				tags.add(tag.textValue());

			Set<String> labels = new LinkedHashSet<>();
			JsonNode labelsNode = json.get("labels");
			for (JsonNode label : labelsNode)
				labels.add(label.textValue());

			Set<String> taints = new LinkedHashSet<>();
			JsonNode taintsNode = json.get("taints");
			for (JsonNode taint : taintsNode)
				taints.add(taint.textValue());

			boolean autoScale = DigitalOceans.toBoolean(json, "auto_scale");
			int minNodes;
			int maxNodes;
			if (autoScale)
			{
				minNodes = DigitalOceans.toInt(json, "min_nodes");
				maxNodes = DigitalOceans.toInt(json, "max_nodes");
			}
			else
			{
				minNodes = initialNumberOfNodes;
				maxNodes = initialNumberOfNodes;
			}

			List<Node> nodes = new ArrayList<>();
			JsonNode nodesNode = json.get("nodes");
			for (JsonNode node : nodesNode)
				nodes.add(Node.getByJson(node));
			return new NodePool(client, id, name, dropletType, initialNumberOfNodes, tags, labels, taints,
				autoScale, minNodes, maxNodes, nodes);
		}

		private final DigitalOceanClient client;
		private final String id;
		private final String name;
		private final DropletType dropletType;
		private final int initialNumberOfNodes;
		private final Set<String> tags;
		private final Set<String> labels;
		private final Set<String> taints;
		private final boolean autoScale;
		private final int minNodes;
		private final int maxNodes;

		/**
		 * Creates a new node pool.
		 *
		 * @param client               the client configuration
		 * @param id                   the ID of the node pool
		 * @param name                 the name of the node pool
		 * @param dropletType          the type of droplets to use for cluster nodes
		 * @param initialNumberOfNodes the initial number of nodes to populate the pool with
		 * @param tags                 the tags of the node pool
		 * @param labels               the labels of the node pool
		 * @param taints               the taints of the node pool
		 * @param autoScale            {@code true} for pool size to adjust automatically to meet demand
		 * @param minNodes             the minimum number of nodes in the pool
		 * @param maxNodes             the maximum number of nodes in the pool
		 * @param nodes                the nodes in the pool
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                    <li>any of the arguments contain leading or trailing whitespace or
		 *                                    are empty.</li>
		 *                                    <li>{@code initialNumberOfNodes} is negative or zero.</li>
		 *                                    <li>{@code initialNumberOfNodes} is less than
		 *                                    {@code minNodes}.</li>
		 *                                    <li>{@code initialNumberOfNodes} is greater than
		 *                                    {@code maxNodes}.</li>
		 *                                    <li>{@code minNodes} is greater than {@code maxNodes}.</li>
		 *                                  </ul>
		 */
		public NodePool(DigitalOceanClient client, String id, String name, DropletType dropletType,
			int initialNumberOfNodes, Set<String> tags, Set<String> labels, Set<String> taints, boolean autoScale,
			int minNodes, int maxNodes, List<Node> nodes)
		{
			requireThat(client, "client").isNotNull();
			requireThat(id, "id").isStripped().isNotEmpty();
			requireThat(name, "name").isStripped().isNotEmpty();
			requireThat(dropletType, "dropletType").isNotNull();
			requireThat(initialNumberOfNodes, "initialNumberOfNodes").isPositive();
			requireThat(tags, "tags").isNotNull();
			requireThat(labels, "labels").isNotNull();
			requireThat(taints, "taints").isNotNull();
			requireThat(minNodes, "minNodes").isPositive();
			requireThat(maxNodes, "maxNodes").isPositive();

			requireThat(minNodes, "minNodes").isLessThanOrEqualTo(maxNodes, "maxNodes");
			requireThat(initialNumberOfNodes, "initialNumberOfNodes").
				isBetween(minNodes, true, maxNodes, true);

			requireThat(nodes, "nodes").isNotNull();

			this.client = client;
			this.id = id;
			this.name = name;
			this.dropletType = dropletType;
			this.initialNumberOfNodes = initialNumberOfNodes;
			this.tags = Set.copyOf(tags);
			this.labels = Set.copyOf(labels);
			this.taints = Set.copyOf(taints);
			this.autoScale = autoScale;
			this.minNodes = initialNumberOfNodes;
			this.maxNodes = initialNumberOfNodes;
		}

		/**
		 * Returns the ID of the node pool.
		 *
		 * @return the ID
		 */
		public String getId()
		{
			return id;
		}

		/**
		 * Returns the type of droplets to use for cluster nodes.
		 *
		 * @return the type of droplets to use for cluster nodes
		 */
		public DropletType getDropletType()
		{
			return dropletType;
		}

		/**
		 * Returns the name of the node pool.
		 *
		 * @return the name of the node pool
		 */
		public String getName()
		{
			return name;
		}

		/**
		 * Returns the initial number of nodes to populate the pool with.
		 *
		 * @return the initial number of nodes
		 */
		public int getInitialNumberOfNodes()
		{
			return initialNumberOfNodes;
		}

		/**
		 * Returns the pool's tags.
		 *
		 * @return the tags
		 */
		public Set<String> getTags()
		{
			return Set.copyOf(tags);
		}

		/**
		 * Returns the pool's labels.
		 *
		 * @return the labels
		 */
		public Set<String> getLabels()
		{
			return Set.copyOf(labels);
		}

		/**
		 * Returns the pool's taints.
		 *
		 * @return the taints
		 */
		public Set<String> getTaints()
		{
			return Set.copyOf(taints);
		}

		/**
		 * Indicates if the pool size should adjust automatically to meet demand.
		 *
		 * @return {@code true} if the pool size should adjust automatically to meet demand
		 */
		public boolean isAutoScale()
		{
			return autoScale;
		}

		/**
		 * Returns the minimum number of nodes in the pool.
		 *
		 * @return the minimum number of nodes in the pool
		 */
		public int getMinNodes()
		{
			return minNodes;
		}

		/**
		 * Returns the maximum number of nodes in the pool.
		 *
		 * @return the maximum number of nodes in the pool
		 */
		public int getMaxNodes()
		{
			return maxNodes;
		}

		@Override
		public int hashCode()
		{
			return id.hashCode();
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof NodePool other && other.id.equals(id);
		}

		/**
		 * Returns the configuration of this {@code NodePool}.
		 *
		 * @return the configuration of this {@code NodePool}
		 */
		public NodePoolConfiguration toConfiguration()
		{
			NodePoolConfiguration spec = new NodePoolConfiguration(client, name, dropletType, initialNumberOfNodes).
				tags(tags).
				labels(labels).
				taints(taints);
			if (autoScale)
				spec.autoscale(minNodes, maxNodes);
			return spec;
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(NodePool.class).
				add("id", id).
				add("name", name).
				add("type", dropletType).
				add("count", initialNumberOfNodes).
				add("tags", tags).
				add("labels", labels).
				add("taints", taints).
				add("autoScale", autoScale).
				add("minNodes", minNodes).
				add("maxNodes", maxNodes).
				toString();
		}
	}

	/**
	 * The desired state of a {@code NodePool}.
	 */
	public static final class NodePoolConfiguration
	{
		private final DigitalOceanClient client;
		private final String name;
		private final DropletType dropletType;
		private final int initialNumberOfNodes;
		private final Set<String> tags = new LinkedHashSet<>();
		private final Set<String> labels = new LinkedHashSet<>();
		private final Set<String> taints = new LinkedHashSet<>();
		private boolean autoScale;
		private int minNodes;
		private int maxNodes;

		/**
		 * Creates a new node pool builder.
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
		public NodePoolConfiguration(DigitalOceanClient client, String name, DropletType dropletType,
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
		public DropletType dropletType()
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
		public NodePoolConfiguration tag(String tag)
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
		public NodePoolConfiguration tags(Collection<String> tags)
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
		public NodePoolConfiguration label(String label)
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
		public NodePoolConfiguration labels(Collection<String> labels)
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
		public NodePoolConfiguration taint(String taint)
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
		public NodePoolConfiguration taints(Collection<String> taints)
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
		public NodePoolConfiguration autoscale(int minNodes, int maxNodes)
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
		 * @return the JSON representation of this object
		 * @throws IllegalStateException if the client is closed
		 */
		public ObjectNode toJson()
		{
			ObjectNode json = client.getObjectMapper().createObjectNode().
				put("size", dropletType.getSlug()).
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
			return o instanceof NodePoolConfiguration other && other.name.equals(name) &&
				other.dropletType.equals(dropletType) && other.initialNumberOfNodes == initialNumberOfNodes &&
				other.tags.equals(tags) && other.labels.equals(labels) && other.taints.equals(taints) &&
				other.autoScale == autoScale && other.minNodes == minNodes && other.maxNodes == maxNodes;
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(NodePoolConfiguration.class).
				add("name", name).
				add("type", dropletType).
				add("count", initialNumberOfNodes).
				add("tags", tags).
				add("labels", labels).
				add("taints", taints).
				add("autoScale", autoScale).
				add("minNodes", minNodes).
				add("maxNodes", maxNodes).
				toString();
		}
	}

	/**
	 * A worker node.
	 */
	public static class Node
	{
		/**
		 * @param json the JSON representation of the node
		 * @return the node
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static Node getByJson(JsonNode json)
		{
			String id = json.get("id").textValue();
			String name = json.get("name").textValue();
			NodeStatus status = NodeStatus.getByJson(json.get("status"));
			String dropletId = json.get("droplet_id").textValue();
			Instant createdAt = Instant.parse(json.get("created_at").textValue());
			Instant updatedAt = Instant.parse(json.get("updated_at").textValue());
			return new Node(id, name, status, dropletId, createdAt, updatedAt);
		}

		private final String id;
		private final String name;
		private final NodeStatus status;
		private final String dropletId;
		private final Instant createdAt;
		private final Instant updatedAt;

		/**
		 * Creates a new node.
		 *
		 * @param id        the ID of the node
		 * @param name      the name of the node or an empty string if the droplet is still provisioning
		 * @param status    the status of the node
		 * @param dropletId the droplet that the node is running on or an empty string if the droplet is still
		 *                  provisioning
		 * @param createdAt the time that the node was created
		 * @param updatedAt the last time that the node was updated
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or is
		 *                                  empty
		 */
		Node(String id, String name, NodeStatus status, String dropletId, Instant createdAt, Instant updatedAt)
		{
			requireThat(id, "id").isStripped().isNotEmpty();
			requireThat(name, "name").isStripped();
			requireThat(status, "status").isNotNull();
			requireThat(dropletId, "dropletId").isStripped();
			requireThat(createdAt, "createdAt").isNotNull();
			requireThat(updatedAt, "updatedAt").isNotNull();

			this.id = id;
			this.name = name;
			this.status = status;
			this.dropletId = dropletId;
			this.createdAt = createdAt;
			this.updatedAt = updatedAt;
		}

		/**
		 * Returns the ID of the node.
		 *
		 * @return the ID
		 */
		public String getId()
		{
			return id;
		}

		/**
		 * Returns the name of the node.
		 *
		 * @return the name
		 */
		public String getName()
		{
			return name;
		}

		/**
		 * Returns the status of the node.
		 *
		 * @return the status
		 */
		public NodeStatus getStatus()
		{
			return status;
		}

		/**
		 * Returns the ID of the droplet that the node is running on.
		 *
		 * @return the droplet ID
		 */
		public String getDropletId()
		{
			return dropletId;
		}

		/**
		 * Returns the time that the node was created.
		 *
		 * @return the time that the node was created
		 */
		public Instant getCreatedAt()
		{
			return createdAt;
		}

		/**
		 * Returns the last time that the node was updated.
		 *
		 * @return the last time that the node was updated
		 */
		public Instant getUpdatedAt()
		{
			return updatedAt;
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(Node.class).
				add("id", id).
				add("name", name).
				add("state", status).
				add("dropletId", dropletId).
				add("createdAt", createdAt).
				add("updatedAt", updatedAt).
				toString();
		}
	}

	/**
	 * The status of a node.
	 *
	 * @param state   the state of the node
	 * @param message (optional) a message providing additional information about the node state, or an empty
	 *                string if absent
	 */
	public record NodeStatus(NodeState state, String message)
	{
		private static NodeStatus getByJson(JsonNode json)
		{
			NodeState state = NodeState.valueOf(json.get("state").textValue().toUpperCase(Locale.ROOT));
			JsonNode messageNode = json.get("message");
			String message;
			if (messageNode == null)
				message = "";
			else
				message = messageNode.textValue();
			return new NodeStatus(state, message);
		}

		/**
		 * Creates a new node status.
		 *
		 * @param state   the state of the node
		 * @param message (optional) a message providing additional information about the node state, or an empty
		 *                string if absent
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code message} contains leading or trailing whitespace or is
		 *                                  empty
		 */
		public NodeStatus
		{
			requireThat(state, "state").isNotNull();
			requireThat(message, "message").isStripped();
		}

		/**
		 * Returns the state of a node.
		 *
		 * @return the state
		 */
		@Override
		public NodeState state()
		{
			return state;
		}

		/**
		 * Returns an optional message providing additional information about the node state.
		 *
		 * @return an empty string if absent
		 */
		@Override
		public String message()
		{
			return message;
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(NodeState.class).
				add("state", state).
				add("message", message).
				toString();
		}
	}

	/**
	 * The state of a node.
	 */
	public enum NodeState
	{
		/**
		 * The node is being configured.
		 */
		PROVISIONING,
		/**
		 * The node is up and running.
		 */
		RUNNING,
		/**
		 * The node is evicting all pods to other nodes.
		 */
		DRAINING,
		/**
		 * The node is being deleted.
		 */
		DELETING
	}

	/**
	 * The schedule for when maintenance activities may be performed on the cluster.
	 */
	public static final class MaintenanceWindow
	{
		/**
		 * @param client the client configuration
		 * @param json   the JSON representation of the maintenance window
		 * @return the MaintenanceWindow
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static MaintenanceWindow getByJson(DigitalOceanClient client, JsonNode json)
		{
			OffsetTime startTime = LocalTime.parse(json.get("start_time").textValue(), HOUR_MINUTE).
				atOffset(ZoneOffset.UTC);
			String dayAsString = json.get("day").textValue();
			DayOfWeek day;
			if (dayAsString.equals("any"))
				day = null;
			else
				day = DayOfWeek.valueOf(dayAsString.toUpperCase(Locale.ROOT));
			return new MaintenanceWindow(client, startTime, day);
		}

		private final DigitalOceanClient client;
		private final OffsetTime startTime;
		private final DayOfWeek day;

		/**
		 * Creates a new schedule.
		 *
		 * @param client    the client configuration
		 * @param startTime the start time in UTC when maintenance may take place
		 * @param day       the day of the week when maintenance may take place; {@code null} if maintenance can
		 *                  occur on any day.
		 * @throws NullPointerException if {@code client} or {@code startTime} are null
		 */
		public MaintenanceWindow(DigitalOceanClient client, OffsetTime startTime, DayOfWeek day)
		{
			requireThat(client, "client").isNotNull();
			requireThat(startTime, "startTime").isNotNull();
			this.client = client;
			this.startTime = startTime;
			this.day = day;
		}

		/**
		 * Returns the JSON representation of this object.
		 *
		 * @return the JSON representation of this object
		 * @throws IllegalStateException if the client is closed
		 */
		public ObjectNode toJson()
		{
			ObjectNode json = client.getObjectMapper().createObjectNode();
			OffsetTime startTimeAtUtc = startTime.withOffsetSameInstant(ZoneOffset.UTC);
			json.put("start_time", HOUR_MINUTE.format(startTimeAtUtc));
			if (day == null)
				json.put("day", "any");
			else
				json.put("day", day.name().toLowerCase(Locale.ROOT));
			return json;
		}

		/**
		 * Returns the start time in UTC when maintenance may take place.
		 *
		 * @return the start time in UTC when maintenance may take place
		 */
		public OffsetTime startTime()
		{
			return startTime;
		}

		/**
		 * Returns the day of the week when maintenance may take place.
		 *
		 * @return null if any day
		 */
		public DayOfWeek day()
		{
			return day;
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(MaintenanceWindow.class).
				add("startTime", startTime).
				add("day", day).
				toString();
		}
	}

	/**
	 * A cluster's state.
	 */
	public enum State
	{
		/**
		 * The cluster is launching.
		 */
		PROVISIONING,
		/**
		 * The cluster is up and running.
		 */
		RUNNING,
		/**
		 * <ul>
		 *   <li>One or more nodes in the cluster have become unresponsive or failed.</li>
		 *   <li>The cluster has run out of resources (CPU, memory, etc.).</li>
		 * </ul>
		 */
		DEGRADED,
		/**
		 * <ul>
		 *   <li>The cluster has experiencing resource exhaustion.</li>
		 *   <li>The scheduler was unable to find suitable nodes for pod placement.</li>
		 *   <li>Issues with individual pods, such as failed deployments or evictions, can contribute to the
		 *   overall cluster error status.</li>
		 * </ul>
		 */
		ERROR,
		/**
		 * The cluster has been deleted.
		 */
		DELETED,
		/**
		 * The cluster is being upgraded.
		 */
		UPGRADING,
		/**
		 * The cluster is being deleted.
		 */
		DELETING;
	}

	/**
	 * The status of a cluster.
	 *
	 * @param state   the state of the cluster
	 * @param message (optional) a message providing additional information about the state, or an empty string
	 *                if absent
	 */
	public record Status(State state, String message)
	{
		private static Status getByJson(JsonNode json)
		{
			State state = State.valueOf(json.get("state").textValue().toUpperCase(Locale.ROOT));
			JsonNode messageNode = json.get("message");
			String message;
			if (messageNode == null)
				message = "";
			else
				message = messageNode.textValue();
			return new Status(state, message);
		}

		/**
		 * Creates a new status.
		 *
		 * @param state   the state of the cluster
		 * @param message (optional) a message providing additional information about the state, or an empty
		 *                string if absent
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code message} contains leading or trailing whitespace or is
		 *                                  empty
		 */
		public Status
		{
			requireThat(state, "state").isNotNull();
			requireThat(message, "message").isStripped();
		}

		/**
		 * Returns the state of the cluster.
		 *
		 * @return the state
		 */
		@Override
		public State state()
		{
			return state;
		}

		/**
		 * Returns an optional message providing additional information about the state.
		 *
		 * @return an empty string if absent
		 */
		@Override
		public String message()
		{
			return message;
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(NodeState.class).
				add("state", state).
				add("message", message).
				toString();
		}
	}
}