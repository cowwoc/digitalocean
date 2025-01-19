package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.exception.KubernetesNotFoundException;
import com.github.cowwoc.digitalocean.id.StringId;
import com.github.cowwoc.digitalocean.internal.util.RetryDelay;
import com.github.cowwoc.digitalocean.internal.util.Strings;
import com.github.cowwoc.digitalocean.internal.util.TimeLimit;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.PROGRESS_FREQUENCY;
import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
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
public final class Kubernetes
{
	/**
	 * Returns all the clusters.
	 *
	 * @param client the client configuration
	 * @return an empty set if no match is found
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Set<Kubernetes> getAll(DigitalOceanClient client)
		throws IOException, InterruptedException, TimeoutException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_list_clusters
		return client.getElements(REST_SERVER.resolve("v2/kubernetes/clusters"), Map.of(), body ->
		{
			Set<Kubernetes> clusters = new HashSet<>();
			for (JsonNode projectNode : body.get("kubernetes_clusters"))
				clusters.add(getByJson(client, projectNode));
			return clusters;
		});
	}

	/**
	 * Returns the first cluster type that matches a predicate.
	 *
	 * @param client    the client configuration
	 * @param predicate the name
	 * @return null if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Kubernetes getByPredicate(DigitalOceanClient client, Predicate<Kubernetes> predicate)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_list_clusters
		return client.getElement(REST_SERVER.resolve("v2/kubernetes/clusters"), Map.of(), body ->
		{
			for (JsonNode cluster : body.get("kubernetes_clusters"))
			{
				Kubernetes candidate = getByJson(client, cluster);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param client the client configuration
	 * @param json   the JSON representation
	 * @return the cluster
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	static Kubernetes getByJson(DigitalOceanClient client, JsonNode json)
		throws IOException, InterruptedException, TimeoutException
	{
		Id id = id(json.get("id").textValue());
		String name = json.get("name").textValue();
		Zone.Id zone = Zone.id(json.get("region").textValue());
		KubernetesVersion version = KubernetesVersion.fromJson(json.get("version"));
		String clusterSubnet = json.get("cluster_subnet").textValue();
		String serviceSubnet = json.get("service_subnet").textValue();
		Vpc.Id vpc = Vpc.id(json.get("vpc_uuid").textValue());
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
		Set<NodePool> nodePools = new HashSet<>();
		for (JsonNode node : nodePoolsNode)
			nodePools.add(Kubernetes.NodePool.getByJson(client, node));

		MaintenanceSchedule maintenanceSchedule = MaintenanceSchedule.getByJson(client,
			json.get("maintenance_policy"));
		boolean autoUpgrade = client.getBoolean(json, "auto_upgrade");
		Status status = Status.getByJson(json.get("status"));

		Instant createdAt = Instant.parse(json.get("created_at").textValue());
		Instant updatedAt = Instant.parse(json.get("updated_at").textValue());
		boolean surgeUpgrade = client.getBoolean(json, "surge_upgrade");
		boolean ha = client.getBoolean(json, "ha");
		boolean canAccessRegistry = client.getBoolean(json, "registry_enabled");
		return new Kubernetes(client, id, name, zone, version, clusterSubnet, serviceSubnet, vpc, ipv4,
			endpoint, tags, nodePools, maintenanceSchedule, autoUpgrade, status, surgeUpgrade, ha,
			canAccessRegistry, createdAt, updatedAt);
	}

	/**
	 * Creates a new cluster.
	 *
	 * @param client    the client configuration
	 * @param name      the name of the node pool
	 * @param zone      the zone that the droplet is deployed in
	 * @param version   the version of Kubernetes software
	 * @param nodePools the node pools that are deployed in the cluster
	 * @return a new cluster creator
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	public static KubernetesCreator creator(DigitalOceanClient client, String name, Zone.Id zone,
		KubernetesVersion version, Set<KubernetesCreator.NodePool> nodePools)
	{
		return new KubernetesCreator(client, name, zone, version, nodePools);
	}

	/**
	 * Creates a new ID.
	 *
	 * @param value the server-side identifier (slug)
	 * @return the type-safe identifier for the resource
	 * @throws IllegalArgumentException if {@code value} contains leading or trailing whitespace or is empty
	 */
	public static Id id(String value)
	{
		if (value == null)
			return null;
		return new Id(value);
	}

	private final DigitalOceanClient client;
	private final Id id;
	private final String name;
	private final Zone.Id zone;
	private final KubernetesVersion version;
	private final String clusterSubnet;
	private final String serviceSubnet;
	private final Vpc.Id vpc;
	private final String ipv4;
	private final String endpoint;
	private final Set<String> tags;
	private final Set<NodePool> nodePools;
	private final MaintenanceSchedule maintenanceSchedule;
	private final boolean autoUpgrade;
	private final Status status;
	private final boolean surgeUpgrade;
	private final boolean highAvailability;
	private final boolean canAccessRegistry;
	private final Instant createdAt;
	private final Instant updatedAt;
	private final Logger log = LoggerFactory.getLogger(Kubernetes.class);

	/**
	 * Creates a new Kubernetes cluster.
	 *
	 * @param client              the client configuration
	 * @param id                  the ID of the cluster
	 * @param name                the name of the cluster
	 * @param zone                the zone that the droplet is deployed in
	 * @param version             the software version of Kubernetes
	 * @param clusterSubnet       the range of IP addresses for the overlay network of the Kubernetes cluster,
	 *                            in CIDR notation
	 * @param serviceSubnet       the range of IP addresses for the services running in the Kubernetes cluster,
	 *                            in CIDR notation
	 * @param vpc                 the VPC that the cluster is deployed in
	 * @param ipv4                (optional) the public IPv4 address of the Kubernetes control plane or an empty
	 *                            string if not set. This value will not be set if high availability is
	 *                            configured on the cluster.
	 * @param endpoint            the base URL of the API server
	 * @param tags                the tags that are associated with the cluster
	 * @param nodePools           the node pools that are deployed in the cluster
	 * @param maintenanceSchedule the maintenance window policy for the cluster
	 * @param autoUpgrade         {@code true} if the cluster will be automatically upgraded to new patch
	 *                            releases during its maintenance window
	 * @param status              the status of the cluster
	 * @param surgeUpgrade        {@code true} if new nodes should be deployed before destroying the outdated
	 *                            nodes
	 * @param highAvailability    {@code true} if the control plane should run in a highly available
	 *                            configuration
	 * @param canAccessRegistry   {@code true} if a container registry is integrated with the cluster
	 * @param createdAt           the time the cluster was created
	 * @param updatedAt           the time the cluster was last updated
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace</li>
	 *                                    <li>any of the mandatory arguments are empty</li>
	 *                                  </ul>
	 * @see Vpc#getDefault(DigitalOceanClient, Zone.Id)
	 */
	private Kubernetes(DigitalOceanClient client, Id id, String name, Zone.Id zone, KubernetesVersion version,
		String clusterSubnet, String serviceSubnet, Vpc.Id vpc, String ipv4, String endpoint, Set<String> tags,
		Set<NodePool> nodePools, MaintenanceSchedule maintenanceSchedule, boolean autoUpgrade, Status status,
		boolean surgeUpgrade, boolean highAvailability, boolean canAccessRegistry, Instant createdAt,
		Instant updatedAt)
	{
		requireThat(client, "client").isNotNull();
		requireThat(id, "id").isNotNull();
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
		requireThat(maintenanceSchedule, "maintenanceSchedule").isNotNull();
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
		this.maintenanceSchedule = maintenanceSchedule;
		this.autoUpgrade = autoUpgrade;
		this.status = status;
		this.surgeUpgrade = surgeUpgrade;
		this.highAvailability = highAvailability;
		this.canAccessRegistry = canAccessRegistry;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns the cluster's ID.
	 *
	 * @return the cluster's ID
	 */
	public Id getId()
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
	 * @return the zone
	 */
	public Zone.Id getZone()
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
	 * @return the VPC
	 */
	public Vpc.Id getVpc()
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
	public Set<NodePool> getNodePools()
	{
		return nodePools;
	}

	/**
	 * Returns the maintenance window policy for the cluster.
	 *
	 * @return the maintenance window policy
	 */
	public MaintenanceSchedule getMaintenanceSchedule()
	{
		return maintenanceSchedule;
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
	 * Determines if the control plane should run in a highly available configuration.
	 *
	 * @return {@code true} if highly available
	 */
	public boolean isHighAvailability()
	{
		return highAvailability;
	}

	/**
	 * Determines if the cluster has access to the container registry, allowing it to push and pull images.
	 *
	 * @return {@code true} if the cluster has access
	 */
	public boolean canAccessRegistry()
	{
		return canAccessRegistry;
	}

	// TODO: To look up the list of clusters that have access to the registry, list all clusters and aggregate
	//  all cluster IDs that have "registry_enabled" set to true. We can then use
	// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_add_registry
	// to add/remove entries.

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

	/**
	 * Determines if the cluster matches the desired state.
	 *
	 * @param state the desired state
	 * @return {@code true} if the droplet matches the desired state; otherwise, {@code false}
	 * @throws NullPointerException if {@code state} is null
	 */
	public boolean matches(KubernetesCreator state)
	{
		return state.name().equals(name) && state.zone().equals(zone) && state.version().equals(version) &&
			state.clusterSubnet().equals(clusterSubnet) && state.serviceSubnet().equals(serviceSubnet) &&
			Objects.equals(state.vpc(), vpc) && state.tags().equals(tags) &&
			state.nodePools().equals(nodePools.stream().map(NodePool::forCreator).collect(Collectors.toSet())) &&
			state.maintenanceSchedule().equals(maintenanceSchedule) && state.autoUpgrade() == autoUpgrade &&
			state.surgeUpgrade() == surgeUpgrade && state.highAvailability() == highAvailability;
	}

	/**
	 * Updates the cluster to the desired configuration.
	 *
	 * @param target the desired state
	 * @throws NullPointerException        if {@code target} is null
	 * @throws IllegalArgumentException    if the server does not support applying any of the desired changes
	 * @throws IllegalStateException       if the client is closed
	 * @throws IOException                 if an I/O error occurs. These errors are typically transient, and
	 *                                     retrying the request may resolve the issue.
	 * @throws TimeoutException            if the request times out before receiving a response. This might
	 *                                     indicate network latency or server overload.
	 * @throws InterruptedException        if the thread is interrupted while waiting for a response. This can
	 *                                     happen due to shutdown signals.
	 * @throws KubernetesNotFoundException if the cluster could not be found
	 * @see KubernetesCreator#copyUnchangeablePropertiesFrom(Kubernetes)
	 */
	public void update(KubernetesCreator target)
		throws IOException, TimeoutException, InterruptedException, KubernetesNotFoundException
	{
		requireThat(target.zone(), "target.zone").isEqualTo(zone);
		requireThat(target.version(), "target.version").isEqualTo(version);
		requireThat(target.clusterSubnet(), "target.clusterSubnet").isEqualTo(clusterSubnet);
		requireThat(target.serviceSubnet(), "target.serviceSubnet").isEqualTo(serviceSubnet);
		requireThat(target.vpc(), "target.vpc").isEqualTo(vpc);
		requireThat(target.nodePools(), "target.nodePools").isEqualTo(nodePools.stream().
			map(Kubernetes.NodePool::forCreator).collect(Collectors.toSet()));
		if (matches(target))
			return;

		JsonMapper jm = client.getJsonMapper();
		ObjectNode requestBody = jm.createObjectNode();
		requestBody.put("name", target.name());
		if (!target.tags().equals(tags))
		{
			ArrayNode tagsNode = requestBody.putArray("tags");
			for (String tag : tags)
				tagsNode.add(tag);
		}
		if (!target.maintenanceSchedule().equals(maintenanceSchedule))
			requestBody.set("maintenance_policy", target.maintenanceSchedule().toJson());
		if (target.autoUpgrade() != autoUpgrade)
			requestBody.put("auto_upgrade", target.autoUpgrade());
		if (target.surgeUpgrade() != surgeUpgrade)
			requestBody.put("surge_upgrade", target.surgeUpgrade());
		if (target.highAvailability() != highAvailability)
			requestBody.put("ha", target.highAvailability());

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_update_cluster
		URI uri = REST_SERVER.resolve("v2/kubernetes/clusters/" + id);
		Request request = client.createRequest(uri, requestBody).
			method(PUT);
		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case ACCEPTED_202 ->
			{
				// success
			}
			case NOT_FOUND_404 -> throw new KubernetesNotFoundException(getId());
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
	}

	/**
	 * Downloads the kubeconfig file for this cluster. If the cluster <a
	 * href="https://docs.digitalocean.com/products/kubernetes/how-to/connect-to-cluster/#version-requirements-for-obtaining-tokens">
	 * supports</a> token-based authentication, the provided duration specifies the token's lifetime before
	 * expiration.
	 *
	 * @param duration the lifetime of the authentication token
	 * @return the {@code kubeconfig} file
	 * @throws NullPointerException        if {@code duration} is null
	 * @throws IOException                 if an I/O error occurs. These errors are typically transient, and
	 *                                     retrying the request may resolve the issue.
	 * @throws TimeoutException            if the request times out before receiving a response. This might
	 *                                     indicate network latency or server overload.
	 * @throws InterruptedException        if the thread is interrupted while waiting for a response. This can
	 *                                     happen due to shutdown signals.
	 * @throws KubernetesNotFoundException if the cluster could not be found
	 */
	public String getKubeConfig(Duration duration)
		throws IOException, TimeoutException, InterruptedException, KubernetesNotFoundException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_get_kubeconfig
		URI uri = REST_SERVER.resolve("v2/kubernetes/clusters/" + id + "/kubeconfig");
		Request request = client.createRequest(uri).
			param("expiry_seconds", String.valueOf(duration.toSeconds())).
			method(GET);
		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				return serverResponse.getContentAsString();
			}
			case NOT_FOUND_404 -> throw new KubernetesNotFoundException(getId());
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
	}

	/**
	 * Blocks until the cluster reaches the desired {@code state} or a timeout occurs.
	 *
	 * @param state   the desired state
	 * @param timeout the maximum amount of time to wait
	 * @return the updated cluster
	 * @throws NullPointerException        if {@code state} is null
	 * @throws IllegalStateException       if the client is closed
	 * @throws IOException                 if an I/O error occurs. These errors are typically transient, and
	 *                                     retrying the request may resolve the issue.
	 * @throws TimeoutException            if the operation times out before the cluster reaches the desired
	 *                                     status. This might indicate network latency or server overload.
	 * @throws InterruptedException        if the thread is interrupted while waiting for a response. This can
	 *                                     happen due to shutdown signals.
	 * @throws KubernetesNotFoundException if the cluster could not be found
	 * @see #waitForDestroy(Duration)
	 */
	public Kubernetes waitFor(State state, Duration timeout)
		throws IOException, TimeoutException, InterruptedException, KubernetesNotFoundException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/kubernetes_get_cluster
		URI uri = REST_SERVER.resolve("v2/kubernetes/clusters/" + id);
		RetryDelay retryDelay = new RetryDelay(Duration.ofSeconds(3), Duration.ofSeconds(30), 2);
		TimeLimit timeLimit = new TimeLimit(timeout);
		Instant timeOfLastStatus = Instant.MIN;
		while (true)
		{
			Request request = client.createRequest(uri).
				method(GET);
			ContentResponse serverResponse = client.send(request);
			switch (serverResponse.getStatus())
			{
				case OK_200 ->
				{
					// success
				}
				case NOT_FOUND_404 -> throw new KubernetesNotFoundException(getId());
				default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
					"Request: " + client.toString(request));
			}
			JsonNode body = client.getResponseBody(serverResponse);
			Kubernetes newCluster = getByJson(client, body.get("kubernetes_cluster"));
			if (newCluster.getStatus().state.equals(state))
			{
				if (timeOfLastStatus != Instant.MIN)
					log.info("The status of {} is {}", name, state);
				return newCluster;
			}
			if (!timeLimit.getTimeLeft().isPositive())
				throw new TimeoutException("Operation failed after " + timeLimit.getTimeQuota());
			Instant now = Instant.now();
			if (Duration.between(timeOfLastStatus, now).compareTo(PROGRESS_FREQUENCY) >= 0)
			{
				log.info("Waiting for the status of {} to change from {} to {}", name, newCluster.status.state,
					state);
				timeOfLastStatus = now;
			}
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
		URI uri = REST_SERVER.resolve("v2/kubernetes/clusters/" + id +
			"/destroy_with_associated_resources/dangerous");
		Request request = client.createRequest(uri).
			method(DELETE);
		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case NO_CONTENT_204, NOT_FOUND_404 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
	}

	/**
	 * Blocks until the cluster is destroyed.
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
	public void waitForDestroy(Duration timeout)
		throws IOException, TimeoutException, InterruptedException
	{
		try
		{
			waitFor(State.DELETED, timeout);
		}
		catch (KubernetesNotFoundException _)
		{
		}
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Kubernetes.class).
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
			add("maintenanceSchedule", maintenanceSchedule).
			add("autoUpgrade", autoUpgrade).
			add("status", status).
			add("surgeUpgrade", surgeUpgrade).
			add("highAvailability", highAvailability).
			add("registryEnabled", canAccessRegistry).
			add("createdAt", createdAt).
			add("updatedAt", updatedAt).
			toString();
	}

	/**
	 * A pool of worker nodes.
	 */
	public static final class NodePool
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param client the client configuration
		 * @param json   the JSON representation
		 * @return the node pool
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static NodePool getByJson(DigitalOceanClient client, JsonNode json)
		{
			DropletType.Id dropletType = DropletType.id(json.get("size").textValue());
			String id = json.get("id").textValue();
			String name = json.get("name").textValue();
			int initialNumberOfNodes = client.getInt(json, "count");

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

			boolean autoScale = client.getBoolean(json, "auto_scale");
			int minNodes;
			int maxNodes;
			if (autoScale)
			{
				minNodes = client.getInt(json, "min_nodes");
				maxNodes = client.getInt(json, "max_nodes");
			}
			else
			{
				minNodes = initialNumberOfNodes;
				maxNodes = initialNumberOfNodes;
			}

			Set<Node> nodes = new HashSet<>();
			JsonNode nodesNode = json.get("nodes");
			for (JsonNode node : nodesNode)
				nodes.add(Node.getByJson(node));
			return new NodePool(client, id, name, dropletType, initialNumberOfNodes, tags, labels, taints,
				autoScale, minNodes, maxNodes, nodes);
		}

		private final DigitalOceanClient client;
		private final String id;
		private final String name;
		private final DropletType.Id dropletType;
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
		public NodePool(DigitalOceanClient client, String id, String name, DropletType.Id dropletType,
			int initialNumberOfNodes, Set<String> tags, Set<String> labels, Set<String> taints, boolean autoScale,
			int minNodes, int maxNodes, Set<Node> nodes)
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
		 * @return the droplet type
		 */
		public DropletType.Id getDropletType()
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

		/**
		 * Converts this object to a type that is accepted by {@code KubernetesCreator}.
		 *
		 * @return the {@code KubernetesCreator.NodePool}
		 */
		public KubernetesCreator.NodePool forCreator()
		{
			KubernetesCreator.NodePool creator = new KubernetesCreator.NodePool(client, name, dropletType,
				initialNumberOfNodes).
				tags(tags).
				labels(labels).
				taints(taints);
			if (autoScale)
				creator.autoscale(minNodes, maxNodes);
			return creator;
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
	 * A worker node.
	 */
	public static class Node
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param json the JSON representation
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
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param json the JSON representation
		 * @return the node status
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static NodeStatus getByJson(JsonNode json)
		{
			NodeState state = NodeState.fromJson(json.get("state"));
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
		DELETING;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static NodeState fromJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
	}

	/**
	 * The schedule for when maintenance activities may be performed on the cluster.
	 */
	public static final class MaintenanceSchedule
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param client the client configuration
		 * @param json   the JSON representation
		 * @return the maintenance schedule
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static MaintenanceSchedule getByJson(DigitalOceanClient client, JsonNode json)
		{
			OffsetTime startTime = LocalTime.parse(json.get("start_time").textValue(), Strings.HOUR_MINUTE).
				atOffset(ZoneOffset.UTC);
			String dayAsString = json.get("day").textValue();
			DayOfWeek day;
			if (dayAsString.equals("any"))
				day = null;
			else
				day = DayOfWeek.valueOf(dayAsString.toUpperCase(Locale.ROOT));
			return new MaintenanceSchedule(client, startTime, day);
		}

		private final DigitalOceanClient client;
		private final OffsetTime startTime;
		private final DayOfWeek day;

		/**
		 * Creates a new schedule.
		 *
		 * @param client    the client configuration
		 * @param startTime the start time when maintenance may take place
		 * @param day       the day of the week when maintenance may take place; {@code null} if maintenance can
		 *                  occur on any day.
		 * @throws NullPointerException     if {@code client} or {@code startTime} are null
		 * @throws IllegalArgumentException if {@code startTime} contains a non-zero second or nano component
		 */
		public MaintenanceSchedule(DigitalOceanClient client, OffsetTime startTime, DayOfWeek day)
		{
			requireThat(client, "client").isNotNull();
			requireThat(startTime, "startTime").isNotNull();
			requireThat(startTime.getSecond(), "hour.getSecond()").isZero();
			requireThat(startTime.getNano(), "hour.getNano()").isZero();

			this.client = client;
			this.startTime = startTime;
			this.day = day;
		}

		/**
		 * Returns the JSON representation of this object.
		 *
		 * @return the JSON representation
		 * @throws IllegalStateException if the client is closed
		 */
		public ObjectNode toJson()
		{
			ObjectNode json = client.getJsonMapper().createObjectNode();
			OffsetTime startTimeAtUtc = startTime.withOffsetSameInstant(ZoneOffset.UTC);
			json.put("start_time", Strings.HOUR_MINUTE.format(startTimeAtUtc));
			if (day == null)
				json.put("day", "any");
			else
				json.put("day", day.name().toLowerCase(Locale.ROOT));
			return json;
		}

		/**
		 * Returns the start time when maintenance may take place.
		 *
		 * @return the start time
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
		public int hashCode()
		{
			return Objects.hash(startTime, day);
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof MaintenanceSchedule other && other.startTime.isEqual(startTime) &&
				Objects.equals(other.day, day);
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(MaintenanceSchedule.class).
				add("startTime", startTime).
				add("day", day).
				toString();
		}
	}

	/**
	 * A type-safe identifier for this type of resource.
	 * <p>
	 * This adds type-safety to API methods by ensuring that IDs specific to one class cannot be used in place
	 * of IDs belonging to another class.
	 */
	public static final class Id extends StringId
	{
		/**
		 * @param value a server-side identifier
		 * @throws NullPointerException     if {@code value} is null
		 * @throws IllegalArgumentException if {@code value} contains leading or trailing whitespace or is empty
		 */
		private Id(String value)
		{
			super(value);
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

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static State fromJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
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
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param json the JSON representation
		 * @return the Status
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static Status getByJson(JsonNode json)
		{
			State state = State.fromJson(json.get("state"));
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