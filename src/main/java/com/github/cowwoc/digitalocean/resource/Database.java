package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.exception.DatabaseNotFoundException;
import com.github.cowwoc.digitalocean.id.StringId;
import com.github.cowwoc.digitalocean.internal.util.RetryDelay;
import com.github.cowwoc.digitalocean.internal.util.Strings;
import com.github.cowwoc.digitalocean.internal.util.TimeLimit;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import com.github.cowwoc.requirements10.annotation.CheckReturnValue;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.PUT;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

/**
 * A database cluster.
 */
public final class Database
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
	public static Set<Database> getAll(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_list_clusters
		return client.getElements(REST_SERVER.resolve("v2/databases"), Map.of(), body ->
		{
			Set<Database> databases = new HashSet<>();
			for (JsonNode database : body.get("databases"))
				databases.add(getByJson(client, database));
			return databases;
		});
	}

	/**
	 * Returns the first database that matches a predicate.
	 *
	 * @param client    the client configuration
	 * @param predicate the predicate
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
	public static Database getByPredicate(DigitalOceanClient client, Predicate<Database> predicate)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_list_clusters
		return client.getElement(REST_SERVER.resolve("v2/databases"), Map.of(), body ->
		{
			for (JsonNode database : body.get("databases"))
			{
				Database candidate = getByJson(client, database);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	/**
	 * Looks up a cluster by its ID.
	 *
	 * @param client the client configuration
	 * @param id     the ID
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
	public static Database getById(DigitalOceanClient client, Id id)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_get_cluster
		return client.getResource(REST_SERVER.resolve("v2/databases/" + id), body ->
		{
			JsonNode database = body.get("database");
			return getByJson(client, database);
		});
	}

	/**
	 * Creates a database cluster.
	 *
	 * @param client               the client configuration
	 * @param name                 the name of the cluster
	 * @param databaseType         the type of the database
	 * @param numberOfStandbyNodes the number of standby nodes in the cluster. The cluster includes one primary
	 *                             node, and may include one or two standby nodes.
	 * @param dropletType          the machine type of the droplet
	 * @param zone                 the zone to create the cluster in. To create a cluster that spans multiple
	 *                             regions, add DatabaseReplica to the cluster.
	 * @return a new database creator
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code name} contains leading or trailing whitespace or is
	 *                                    empty.</li>
	 *                                    <li>{@code numberOfStandbyNodes} is negative, or greater than 2.</li>
	 *                                  </ul>
	 * @throws IllegalStateException    if the client is closed
	 */
	public static DatabaseCreator creator(DigitalOceanClient client, String name, DatabaseType databaseType,
		int numberOfStandbyNodes, DropletType.Id dropletType, Zone.Id zone)
	{
		return new DatabaseCreator(client, name, databaseType, numberOfStandbyNodes, dropletType, zone);
	}

	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param client the client configuration
	 * @param json   the JSON representation
	 * @return the database
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	static Database getByJson(DigitalOceanClient client, JsonNode json)
		throws IOException, InterruptedException, TimeoutException
	{
		try
		{
			Id id = id(json.get("id").textValue());
			String name = json.get("name").textValue();
			DatabaseType databaseType = DatabaseType.getBySlug(json.get("engine").textValue());
			String version = json.get("version").textValue();
			String semanticVersion = json.get("semantic_version").textValue();
			int numberOfNodes = client.getInt(json, "num_nodes");
			DropletType.Id dropletType = DropletType.id(json.get("size").textValue());

			Zone.Id zone = Zone.id(json.get("region").textValue());
			State state = State.fromJson(json.get("status"));

			JsonNode vpcNode = json.get("private_network_uuid");
			Vpc.Id vpc;
			if (vpcNode == null)
				vpc = null;
			else
				vpc = Vpc.id(vpcNode.textValue());

			Set<String> tags = client.getElements(json, "tags", JsonNode::textValue);
			Set<String> databaseNames = client.getElements(json, "db_names", JsonNode::textValue);

			OpenSearchDashboard openSearchDashboard = OpenSearchDashboard.getByJson(client,
				json.get("ui_connection"));
			Connection publicConnection = Connection.getByJson(client, json.get("connection"));
			Connection privateConnection = Connection.getByJson(client, json.get("private_connection"));
			Connection standbyPublicConnection = Connection.getByJson(client, json.get("standby_connection"));
			Connection standbyPrivateConnection = Connection.getByJson(client,
				json.get("standby_private_connection"));
			Set<User> users = client.getElements(json, "users",
				element -> User.getByJson(client, databaseType, element));
			MaintenanceSchedule maintenanceSchedule = Database.MaintenanceSchedule.getByJson(client,
				json.get("maintenance_window"));
			String projectId = json.get("project_id").textValue();
			Set<FirewallRule> firewallRules = client.getElements(json, "rules", node ->
				FirewallRule.getByJson(client, node));

			JsonNode endOfLifeNode = json.get("version_end_of_life");
			Instant versionEndOfLife;
			if (endOfLifeNode == null)
				versionEndOfLife = null;
			else
				versionEndOfLife = Instant.parse(endOfLifeNode.textValue());

			JsonNode endOfAvailabilityNode = json.get("version_end_of_life");
			Instant versionEndOfAvailability;
			if (endOfAvailabilityNode == null)
				versionEndOfAvailability = null;
			else
				versionEndOfAvailability = Instant.parse(endOfAvailabilityNode.textValue());
			int additionalStorageInMiB = json.get("storage_size_mib").intValue();
			Set<Endpoint> metricsEndpoints = client.getElements(json, "metrics_endpoints", node ->
				Endpoint.getByJson(client, node));

			Instant createdAt = Instant.parse(json.get("created_at").textValue());
			return new Database(client, id, name, databaseType, version, semanticVersion, numberOfNodes - 1,
				dropletType, zone, state, vpc, tags, databaseNames, openSearchDashboard, publicConnection,
				privateConnection, standbyPublicConnection, standbyPrivateConnection, users, maintenanceSchedule,
				projectId, firewallRules, versionEndOfLife, versionEndOfAvailability, additionalStorageInMiB,
				metricsEndpoints, createdAt);
		}
		catch (RuntimeException e)
		{
			LoggerFactory.getLogger(Database.class).warn(json.toPrettyString(), e);
			throw e;
		}
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
	private final DatabaseType databaseType;
	private final String version;
	private final String semanticVersion;
	private final int numberOfStandbyNodes;
	private final DropletType.Id dropletType;
	private final Zone.Id zone;
	private final State state;
	private final Vpc.Id vpc;
	private final Set<String> tags;
	private final Set<String> databaseNames;
	private final OpenSearchDashboard openSearchDashboard;
	private final Connection publicConnection;
	private final Connection privateConnection;
	private final Connection standbyPublicConnection;
	private final Connection standbyPrivateConnection;
	private final Set<User> users;
	private final MaintenanceSchedule maintenanceSchedule;
	private final String projectId;
	private final Set<FirewallRule> firewallRules;
	private final Instant versionEndOfLife;
	private final Instant versionEndOfAvailability;
	private final int additionalStorageInMiB;
	private final Set<Endpoint> metricsEndpoints;
	private final Instant createdAt;
	private final Logger log = LoggerFactory.getLogger(Database.class);

	/**
	 * Creates a new cluster.
	 *
	 * @param client                   the client configuration
	 * @param id                       the ID of the cluster
	 * @param name                     the name of the cluster
	 * @param databaseType             the database type
	 * @param version                  the major version number of the database software
	 * @param semanticVersion          the semantic version number of the database software
	 * @param numberOfStandbyNodes     the number of standby nodes in the cluster. The cluster includes one
	 *                                 primary node, and may include one or two standby nodes.
	 * @param dropletType              the machine type of the nodes
	 * @param zone                     the zone that the cluster is deployed in
	 * @param state                    the state of the cluster
	 * @param vpc                      the VPC that the cluster is deployed in, or {@code null} if it is
	 *                                 deployed into the default VPC of the zone
	 * @param tags                     the tags that are associated with the cluster
	 * @param databaseNames            the names of databases in the cluster
	 * @param openSearchDashboard      (optional) connection details for accessing the OpenSearch dashboard, or
	 *                                 {@code null} if this is not an OpenSearch database
	 * @param publicConnection         (optional) the database's public connection endpoint, or {@code null} if
	 *                                 absent
	 * @param privateConnection        (optional) the database's private connection endpoint (accessible only
	 *                                 from within the VPC), or {@code null} if absent
	 * @param standbyPublicConnection  (optional) the standby instance's public connection endpoint, or
	 *                                 {@code null} if absent
	 * @param standbyPrivateConnection (optional) the standby instance's private connection endpoint (accessible
	 *                                 only from within the VPC), or {@code null} if absent
	 * @param users                    the database users
	 * @param maintenanceSchedule      the maintenance schedule for the cluster
	 * @param projectId                the ID of the project that the cluster is assigned to
	 * @param firewallRules            the cluster's firewall rules
	 * @param versionEndOfLife         the time when this version will no longer be supported, or {@code null}
	 *                                 if this version does not have an end-of-life timeline.
	 * @param versionEndOfAvailability the time when this version will no longer be available for creating new
	 *                                 clusters, or {@code null} if this version does not have an
	 *                                 end-of-availability timeline.
	 * @param additionalStorageInMiB   the amount of additional storage (in MiB) that is accessible to the
	 *                                 cluster
	 * @param metricsEndpoints         the endpoints for accessing the cluster metrics
	 * @param createdAt                the time the cluster was created
	 * @throws NullPointerException     if any of the mandatory arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code name} contains leading or trailing whitespace or is
	 *                                    empty.</li>
	 *                                    <li>{@code numberOfStandbyNodes} is negative, or greater than 2.</li>
	 *                                    <li>{@code additionalStorageInMiB} is negative.</li>
	 *                                  </ul>
	 */
	private Database(DigitalOceanClient client, Id id, String name, DatabaseType databaseType, String version,
		String semanticVersion, int numberOfStandbyNodes, DropletType.Id dropletType, Zone.Id zone, State state,
		Vpc.Id vpc, Set<String> tags, Set<String> databaseNames, OpenSearchDashboard openSearchDashboard,
		Connection publicConnection, Connection privateConnection, Connection standbyPublicConnection,
		Connection standbyPrivateConnection, Set<User> users, MaintenanceSchedule maintenanceSchedule,
		String projectId, Set<FirewallRule> firewallRules, Instant versionEndOfLife,
		Instant versionEndOfAvailability, int additionalStorageInMiB, Set<Endpoint> metricsEndpoints,
		Instant createdAt)
	{
		requireThat(client, "client").isNotNull();
		requireThat(id, "id").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(databaseType, "databaseType").isNotNull();
		requireThat(version, "version").isStripped().isNotEmpty();
		requireThat(semanticVersion, "semanticVersion").isStripped().isNotEmpty();
		requireThat(numberOfStandbyNodes, "numberOfStandbyNodes").isBetween(0, true, 2, true);
		requireThat(dropletType, "dropletType").isNotNull();
		requireThat(zone, "zone").isNotNull();
		requireThat(state, "state").isNotNull();
		requireThat(vpc, "vpc").isNotNull();
		requireThat(tags, "tags").isNotNull();
		for (String tag : tags)
			requireThat(tag, "tag").withContext(tags, "tags").isStripped().isNotEmpty();
		requireThat(databaseNames, "databaseNames").isNotNull();
		for (String databaseName : databaseNames)
		{
			requireThat(databaseName, "databaseName").withContext(databaseNames, "databaseNames").
				isStripped().isNotEmpty();
		}
		requireThat(users, "users").isNotNull();
		requireThat(maintenanceSchedule, "maintenanceSchedule").isNotNull();
		requireThat(projectId, "projectId").isStripped().isNotEmpty();
		requireThat(firewallRules, "firewallRules").isNotNull();
		requireThat(additionalStorageInMiB, "additionalStorageInMiB").isNotNegative();
		requireThat(metricsEndpoints, "metricsEndpoints").isNotNull();
		requireThat(createdAt, "createdAt").isNotNull();

		this.client = client;
		this.id = id;
		this.name = name;
		this.databaseType = databaseType;
		this.version = version;
		this.semanticVersion = semanticVersion;
		this.numberOfStandbyNodes = numberOfStandbyNodes;
		this.dropletType = dropletType;
		this.zone = zone;
		this.state = state;
		this.vpc = vpc;
		this.tags = Set.copyOf(tags);
		this.databaseNames = Set.copyOf(databaseNames);
		this.openSearchDashboard = openSearchDashboard;
		this.publicConnection = publicConnection;
		this.privateConnection = privateConnection;
		this.standbyPublicConnection = standbyPublicConnection;
		this.standbyPrivateConnection = standbyPrivateConnection;
		this.users = Set.copyOf(users);
		this.maintenanceSchedule = maintenanceSchedule;
		this.projectId = projectId;
		this.firewallRules = Set.copyOf(firewallRules);
		this.versionEndOfLife = versionEndOfLife;
		this.versionEndOfAvailability = versionEndOfAvailability;
		this.additionalStorageInMiB = additionalStorageInMiB;
		this.metricsEndpoints = Set.copyOf(metricsEndpoints);
		this.createdAt = createdAt;
	}

	/**
	 * Returns the ID of the cluster.
	 *
	 * @return the ID
	 */
	public Id getId()
	{
		return id;
	}

	/**
	 * Returns the name of the cluster.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the database type.
	 *
	 * @return the type
	 */
	public DatabaseType getDatabaseType()
	{
		return databaseType;
	}

	/**
	 * Returns the version number of the database software.
	 *
	 * @return the version number
	 */
	public String getVersion()
	{
		return version;
	}

	/**
	 * Returns the semantic version number of the database software.
	 *
	 * @return the semantic version number
	 */
	public String getSemanticVersion()
	{
		return semanticVersion;
	}

	/**
	 * Returns the number of standby nodes in the cluster. The cluster includes one primary node, and may
	 * include one or two standby nodes.
	 *
	 * @return the number of standby nodes
	 */
	public int getNumberOfStandbyNodes()
	{
		return numberOfStandbyNodes;
	}

	/**
	 * Returns the machine type of the nodes.
	 *
	 * @return the machine type of the nodes
	 */
	public DropletType.Id getDropletType()
	{
		return dropletType;
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
	 * Returns the state of the cluster.
	 *
	 * @return the state
	 */
	public State getState()
	{
		return state;
	}

	/**
	 * Returns the VPC that the cluster is deployed in.
	 *
	 * @return {@code null} if it is deployed into the default VPC of the zone
	 */
	public Vpc.Id getVpc()
	{
		return vpc;
	}

	/**
	 * Returns the cluster's tags.
	 *
	 * @return the tags
	 */
	public Set<String> getTags()
	{
		return tags;
	}

	/**
	 * Returns the names of databases in the cluster.
	 *
	 * @return the names of databases
	 */
	public Set<String> getDatabaseNames()
	{
		return databaseNames;
	}

	/**
	 * Returns the connection details for accessing the OpenSearch dashboard, or {@code null} if this is not an
	 * OpenSearch database.
	 *
	 * @return the connection details
	 */
	public OpenSearchDashboard getOpenSearchDashboard()
	{
		return openSearchDashboard;
	}

	/**
	 * Returns the database's private connection endpoint.
	 *
	 * @return the private endpoint
	 */
	public Connection getPublicConnection()
	{
		return publicConnection;
	}

	/**
	 * Returns the database's public connection endpoint.
	 *
	 * @return the public endpoint
	 */
	public Connection getPrivateConnection()
	{
		return privateConnection;
	}

	/**
	 * Returns the standby instance's public connection endpoint.
	 *
	 * @return the standby instance's public endpoint
	 */
	public Connection getStandbyPublicConnection()
	{
		return standbyPublicConnection;
	}

	/**
	 * Returns the standby instance's private connection endpoint.
	 *
	 * @return the standby instance's private endpoint
	 */
	public Connection getStandbyPrivateConnection()
	{
		return standbyPrivateConnection;
	}

	/**
	 * Returns the database users.
	 *
	 * @return the users
	 */
	public Set<User> getUsers()
	{
		return users;
	}

	/**
	 * Returns the maintenance schedule policy for the cluster.
	 *
	 * @return the maintenance schedule
	 */
	public MaintenanceSchedule getMaintenanceSchedule()
	{
		return maintenanceSchedule;
	}

	/**
	 * Sets the cluster's maintenance schedule.
	 *
	 * @param maintenanceSchedule the maintenance schedule
	 * @throws NullPointerException      if {@code maintenanceSchedule} is null
	 * @throws IllegalArgumentException  if the server does not support applying any of the desired changes
	 * @throws IllegalStateException     if the client is closed
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws TimeoutException          if the request times out before receiving a response. This might
	 *                                   indicate network latency or server overload.
	 * @throws InterruptedException      if the thread is interrupted while waiting for a response. This can
	 *                                   happen due to shutdown signals.
	 * @throws DatabaseNotFoundException if the cluster could not be found
	 * @see KubernetesCreator#copyUnchangeablePropertiesFrom(Kubernetes)
	 */
	public void setMaintenanceSchedule(DatabaseCreator.MaintenanceSchedule maintenanceSchedule)
		throws IOException, TimeoutException, InterruptedException, DatabaseNotFoundException
	{
		if (maintenanceSchedule.equals(this.maintenanceSchedule.forCreator()))
			return;
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_update_maintenanceWindow
		URI uri = REST_SERVER.resolve("v2/databases/" + id + "/maintenance");
		Request request = client.createRequest(uri, maintenanceSchedule.toJson()).
			method(PUT);
		Response serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case NO_CONTENT_204 ->
			{
				// success
			}
			case NOT_FOUND_404 -> throw new DatabaseNotFoundException(getId());
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
	}

	/**
	 * Returns the ID of the project that the cluster is assigned to.
	 *
	 * @return the ID of the project
	 */
	public String getProjectId()
	{
		return projectId;
	}

	/**
	 * Returns the cluster's firewall rules.
	 *
	 * @return the firewall rules
	 */
	public Set<FirewallRule> getFirewallRules()
	{
		return firewallRules;
	}

	/**
	 * Returns the time when this version will no longer be supported.
	 *
	 * @return {@code null} if this version does not have an end-of-life timeline
	 */
	public Instant getVersionEndOfLife()
	{
		return versionEndOfLife;
	}

	/**
	 * Returns the time when this version will no longer be available for creating new clusters.
	 *
	 * @return {@code null} if this version does not have an end-of-availability timeline
	 */
	public Instant getVersionEndOfAvailability()
	{
		return versionEndOfAvailability;
	}

	/**
	 * Returns the amount of additional storage (in MiB) that is accessible to the cluster.
	 *
	 * @return the amount of additional storage
	 */
	public int getAdditionalStorageInMiB()
	{
		return additionalStorageInMiB;
	}

	/**
	 * Returns the endpoints for accessing the cluster metrics.
	 *
	 * @return the endpoints
	 */
	public Set<Endpoint> getMetricsEndpoints()
	{
		return metricsEndpoints;
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
	 * Reloads the cluster's state.
	 *
	 * @return the updated state
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	@CheckReturnValue
	public Database reload() throws IOException, TimeoutException, InterruptedException
	{
		return getById(client, id);
	}

	/**
	 * Determines if the cluster matches the desired state.
	 *
	 * @param state the desired state
	 * @return {@code true} if the cluster matches the desired state; otherwise, {@code false}
	 * @throws NullPointerException if {@code state} is null
	 */
	public boolean matches(DatabaseCreator state)
	{
		return state.name().equals(name) && state.databaseType().equals(databaseType) &&
			state.version().equals(version) && state.numberOfStandbyNodes() == numberOfStandbyNodes &&
			state.dropletType().equals(dropletType) && state.zone().equals(zone) &&
			Objects.equals(state.vpc(), vpc) && state.tags().equals(tags) && state.projectId().equals(projectId) &&
			state.firewallRules().equals(firewallRules.stream().map(FirewallRule::forCreator).
				collect(Collectors.toSet())) && state.additionalStorageInMiB() == additionalStorageInMiB;
	}

	/**
	 * Blocks until the cluster reaches the desired {@code state} or a timeout occurs.
	 *
	 * @param state   the desired state
	 * @param timeout the maximum amount of time to wait
	 * @return the updated cluster
	 * @throws NullPointerException      if {@code state} is null
	 * @throws IllegalStateException     if the client is closed
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws TimeoutException          if the operation times out before the cluster reaches the desired
	 *                                   status. This might indicate network latency or server overload.
	 * @throws InterruptedException      if the thread is interrupted while waiting for a response. This can
	 *                                   happen due to shutdown signals.
	 * @throws DatabaseNotFoundException if the cluster could not be found
	 * @see #waitForDestroy(Duration)
	 */
	public Database waitFor(State state, Duration timeout)
		throws IOException, TimeoutException, InterruptedException, DatabaseNotFoundException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_get_cluster
		URI uri = REST_SERVER.resolve("v2/kubernetes/databases/" + id);
		RetryDelay retryDelay = new RetryDelay(Duration.ofSeconds(3), Duration.ofSeconds(30), 2);
		TimeLimit timeLimit = new TimeLimit(timeout);
		Instant timeOfLastStatus = Instant.MIN;
		while (true)
		{
			Request request = client.createRequest(uri).
				method(GET);
			Response serverResponse = client.send(request);
			switch (serverResponse.getStatus())
			{
				case OK_200 ->
				{
					// success
				}
				case NOT_FOUND_404 -> throw new DatabaseNotFoundException(id);
				default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
					"Request: " + client.toString(request));
			}
			ContentResponse contentResponse = (ContentResponse) serverResponse;
			JsonNode body = client.getResponseBody(contentResponse);
			Database newCluster = getByJson(client, body.get("database"));
			if (newCluster.getState().equals(state))
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
				log.info("Waiting for the status of {} to change from {} to {}", name, newCluster.state, state);
				timeOfLastStatus = now;
			}
			retryDelay.sleep();
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
	public void waitForDestroy(Duration timeout)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_get_cluster
		URI uri = REST_SERVER.resolve("v2/kubernetes/databases/" + id);
		RetryDelay retryDelay = new RetryDelay(Duration.ofSeconds(3), Duration.ofSeconds(30), 2);
		TimeLimit timeLimit = new TimeLimit(timeout);
		Instant timeOfLastStatus = Instant.MIN;
		while (true)
		{
			Request request = client.createRequest(uri).
				method(GET);
			Response serverResponse = client.send(request);
			switch (serverResponse.getStatus())
			{
				case OK_200 ->
				{
					// The cluster is still alive
				}
				case NOT_FOUND_404 ->
				{
					if (timeOfLastStatus != Instant.MIN)
						log.info("{} was destroyed", name);
					return;
				}
				default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
					"Request: " + client.toString(request));
			}
			ContentResponse contentResponse = (ContentResponse) serverResponse;
			JsonNode body = client.getResponseBody(contentResponse);
			Database newCluster = getByJson(client, body.get("database"));
			if (!timeLimit.getTimeLeft().isPositive())
				throw new TimeoutException("Operation failed after " + timeLimit.getTimeQuota());
			Instant now = Instant.now();
			if (Duration.between(timeOfLastStatus, now).compareTo(PROGRESS_FREQUENCY) >= 0)
			{
				log.info("Waiting for {} to get destroyed. Current state: {}", name, newCluster.state);
				timeOfLastStatus = now;
			}
			retryDelay.sleep();
		}
	}

	/**
	 * Destroys the cluster.
	 *
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public void destroy() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_destroy_cluster
		client.destroyResource(REST_SERVER.resolve("v2/databases/" + id));
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Database.class).
			add("id", id).
			add("name", name).
			add("databaseType", databaseType).
			add("version", version).
			add("semanticVersion", semanticVersion).
			add("numberOfStandbyNodes", numberOfStandbyNodes).
			add("dropletType", dropletType).
			add("zone", zone).
			add("state", state).
			add("vpc", vpc).
			add("tags", tags).
			add("databaseNames", databaseNames).
			add("openSearchDashboard", openSearchDashboard).
			add("publicConnection", publicConnection).
			add("privateConnection", privateConnection).
			add("publicConnection", standbyPublicConnection).
			add("privateConnection", standbyPrivateConnection).
			add("users", users).
			add("maintenanceSchedule", maintenanceSchedule).
			add("projectId", projectId).
			add("firewallRules", firewallRules).
			add("versionEndOfLife", versionEndOfLife).
			add("versionEndOfAvailability", versionEndOfAvailability).
			add("additionalStorageInMiB", additionalStorageInMiB).
			add("metricsEndpointsB", metricsEndpoints).
			add("createdAt", createdAt).
			toString();
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
			// The server returns a value with a minute, second, nano component, but they are zeroed out by the
			// DigitalOcean web interface.
			OffsetTime startTime = LocalTime.parse(json.get("hour").textValue(), Strings.HOUR_MINUTE_SECOND).
				atOffset(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
			DayOfWeek day = DayOfWeek.valueOf(json.get("day").textValue().toUpperCase(Locale.ROOT));
			boolean pending = client.getBoolean(json, "pending");
			List<String> descriptions;
			try
			{
				descriptions = client.toList(json, "description", JsonNode::textValue);
			}
			catch (IOException | TimeoutException | InterruptedException e)
			{
				// Exceptions never thrown by JsonNode::textValue
				throw new AssertionError(e);
			}
			return new MaintenanceSchedule(client, startTime, day, pending, descriptions);
		}

		private final DigitalOceanClient client;
		private final OffsetTime hour;
		private final DayOfWeek day;
		private final boolean pending;
		private final List<String> descriptions;

		/**
		 * Creates a new schedule.
		 *
		 * @param client       the client configuration
		 * @param hour         the start hour when maintenance may take place
		 * @param day          the day of the week when maintenance may take place
		 * @param pending      determines whether any maintenance is scheduled to be performed in the next
		 *                     maintenance window
		 * @param descriptions a list of strings, each containing information about a pending maintenance update
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                    <li>one of the {@code descriptions} contains leading or trailing
		 *                                    whitespace or is empty.</li>
		 *                                    <li>{@code hour} contains a non-zero minute, second or nano
		 *                                    component.</li>
		 *                                  </ul>
		 */
		public MaintenanceSchedule(DigitalOceanClient client, OffsetTime hour, DayOfWeek day, boolean pending,
			List<String> descriptions)
		{
			requireThat(client, "client").isNotNull();
			requireThat(hour, "hour").isNotNull();
			requireThat(hour.getSecond(), "hour.getSecond()").isZero();
			requireThat(hour.getNano(), "hour.getNano()").isZero();
			requireThat(day, "day").isNotNull();

			requireThat(descriptions, "descriptions").isNotNull();
			for (String description : descriptions)
			{
				requireThat(description, "description").withContext(descriptions, "descriptions").
					isStripped().isNotEmpty();
			}

			this.client = client;
			this.hour = hour;
			this.day = day;
			this.pending = pending;
			this.descriptions = List.copyOf(descriptions);
		}

		/**
		 * Returns the start hour when maintenance may take place.
		 *
		 * @return the hour
		 */
		public OffsetTime hour()
		{
			return hour;
		}

		/**
		 * Returns the day of the week when maintenance may take place.
		 *
		 * @return the day
		 */
		public DayOfWeek day()
		{
			return day;
		}

		/**
		 * Determines whether any maintenance is scheduled to be performed in the next maintenance window.
		 *
		 * @return {@code true} if maintenance is scheduled
		 */
		public boolean isPending()
		{
			return pending;
		}

		/**
		 * Returns a list of strings, each containing information about a pending maintenance update.
		 *
		 * @return information about a pending maintenance update
		 */
		public List<String> getDescriptions()
		{
			return descriptions;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(hour, day, pending, descriptions);
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof MaintenanceSchedule other && other.hour.isEqual(hour) && other.day.equals(day) &&
				other.pending == pending && other.descriptions.equals(descriptions);
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(MaintenanceSchedule.class).
				add("hour", hour).
				add("day", day).
				add("pending", pending).
				add("descriptions", descriptions).
				toString();
		}

		/**
		 * Converts this object to a type that is accepted by {@code DatabaseCreator}.
		 *
		 * @return the {@code DatabaseCreator.MaintenanceSchedule}
		 */
		public DatabaseCreator.MaintenanceSchedule forCreator()
		{
			return new DatabaseCreator.MaintenanceSchedule(client, hour, day);
		}
	}

	/**
	 * The state of the cluster.
	 */
	public enum State
	{
		/**
		 * The cluster is being created.
		 */
		CREATING,
		/**
		 * The cluster is online.
		 */
		ONLINE,
		/**
		 * The cluster is being resized.
		 */
		RESIZING,
		/**
		 * The cluster is being moved to a new instance due to a configuration or version change.
		 */
		MIGRATING,
		/**
		 * The cluster is being copied, usually for backup purposes.
		 */
		FORKING;

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
	 * Connection details for an OpenSearch dashboard.
	 *
	 * @param uri      the URI of the dashboard
	 * @param hostname the hostname of the dashboard
	 * @param port     the port of the dashboard
	 * @param username the name of the default user
	 * @param password the password of the default user
	 * @param ssl      {@code true} if the connection should be made over SSL
	 */
	public record OpenSearchDashboard(URI uri, String hostname, int port, String username, String password,
	                                  boolean ssl)
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param client the client configuration
		 * @param json   the JSON representation, or {@code null} if omitted
		 * @return the connection details, or {@code null} if {@code json} is {@code null}
		 * @throws NullPointerException     if {@code client} is null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static OpenSearchDashboard getByJson(DigitalOceanClient client, JsonNode json)
		{
			if (json == null)
				return null;
			URI uri = URI.create(json.get("uri").textValue());
			String hostname = json.get("host").textValue();
			int port = client.getInt(json, "port");
			String username = json.get("user").textValue();
			String password = json.get("password").textValue();
			boolean ssl = client.getBoolean(json, "ssl");
			return new OpenSearchDashboard(uri, hostname, port, username, password, ssl);
		}

		/**
		 * Creates a new instance.
		 *
		 * @param uri      the URI of the dashboard
		 * @param hostname the hostname of the dashboard
		 * @param port     the port of the dashboard
		 * @param username the name of the default user
		 * @param password the password of the default user
		 * @param ssl      {@code true} if the connection should be made over SSL
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                    <li>if any of the arguments contain leading or trailing
		 *                                    whitespace or are empty.</li>
		 *                                    <li>if {@code port} is negative or zero.</li>
		 *                                  </ul>
		 */
		public OpenSearchDashboard
		{
			requireThat(uri, "uri").isNotNull();
			requireThat(username, "userName").isStripped().isNotEmpty();
			requireThat(password, "password").isStripped().isNotEmpty();
		}
	}

	/**
	 * Connection details for a database endpoint.
	 *
	 * @param uri          the URI of the database connection
	 * @param databaseName the name of the database
	 * @param hostname     the hostname of the cluster's primary database server
	 * @param port         the port of the server
	 * @param username     the name of the default user
	 * @param password     the password of the default user
	 * @param ssl          {@code true} if the connection should be made over SSL
	 */
	public record Connection(URI uri, String databaseName, String hostname, int port, String username,
	                         String password, boolean ssl)
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param client the client configuration
		 * @param json   the JSON representation, or {@code null} if omitted
		 * @return the connection details, or {@code null} if {@code json} is {@code null}
		 * @throws NullPointerException     if {@code client} is null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static Connection getByJson(DigitalOceanClient client, JsonNode json)
		{
			if (json == null)
				return null;
			URI uri = URI.create(json.get("uri").textValue());
			String databaseName = json.get("database").textValue();
			String hostname = json.get("host").textValue();
			int port = client.getInt(json, "port");
			String username = json.get("user").textValue();
			String password = json.get("password").textValue();
			boolean ssl = client.getBoolean(json, "ssl");
			return new Connection(uri, databaseName, hostname, port, username, password, ssl);
		}

		/**
		 * Creates a new instance.
		 *
		 * @param uri          the URI of the database connection
		 * @param databaseName the name of the database
		 * @param hostname     the hostname of the cluster's primary database server
		 * @param port         the port of the server
		 * @param username     the name of the default user
		 * @param password     the password of the default user
		 * @param ssl          {@code true} if the connection should be made over SSL
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                    <li>if any of the arguments contain leading or trailing
		 *                                    whitespace or are empty.</li>
		 *                                    <li>if {@code port} is negative or zero.</li>
		 *                                  </ul>
		 */
		public Connection
		{
			requireThat(uri, "uri").isNotNull();
			requireThat(databaseName, "databaseName").isStripped().isNotEmpty();
			requireThat(username, "username").isStripped().isNotEmpty();
			requireThat(password, "password").isStripped().isNotEmpty();
		}
	}

	/**
	 * A database user.
	 *
	 * @param name               the name of the user
	 * @param role               the role of the user
	 * @param password           the user's password
	 * @param mySqlSettings      (optional) MySQL-specific settings, or null on other databases
	 * @param postgresqlSettings (optional) PostgreSQL-specific settings, or null on other databases
	 * @param openSearchSettings (optional) OpenSearch-specific settings, or null on other databases
	 * @param kafkaSettings      (optional) Kafka-specific settings, or null on other databases
	 */
	public record User(String name, UserRole role, String password, MySqlSettings mySqlSettings,
	                   PostgresqlSettings postgresqlSettings, OpenSearchSettings openSearchSettings,
	                   KafkaSettings kafkaSettings)
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param client       the client configuration
		 * @param databaseType the database type
		 * @param json         the JSON representation
		 * @return the user
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static User getByJson(DigitalOceanClient client, DatabaseType databaseType, JsonNode json)
		{
			String name = json.get("name").textValue();
			UserRole role = UserRole.fromJson(json.get("role"));
			String password = json.get("password").textValue();
			MySqlSettings mySqlSettings = switch (databaseType)
			{
				case MYSQL -> MySqlSettings.getByJson(json.get("settings"));
				default -> null;
			};
			PostgresqlSettings postgreSqlSettings = switch (databaseType)
			{
				case POSTGRESQL -> PostgresqlSettings.getByJson(client, json.get("settings"));
				default -> null;
			};
			OpenSearchSettings openSearchSettings = switch (databaseType)
			{
				case OPENSEARCH -> OpenSearchSettings.getByJson(json.get("settings"));
				default -> null;
			};
			KafkaSettings kafkaSettings = switch (databaseType)
			{
				case KAFKA -> KafkaSettings.getByJson(json);
				default -> null;
			};

			return new User(name, role, password, mySqlSettings, postgreSqlSettings, openSearchSettings,
				kafkaSettings);
		}

		/**
		 * Creates a new instance.
		 *
		 * @param name               the name of the user
		 * @param role               the role of the user
		 * @param password           the user's password
		 * @param mySqlSettings      (optional) MySQL-specific settings, or null on other databases
		 * @param postgresqlSettings (optional) PostgreSQL-specific settings, or null on other databases
		 * @param openSearchSettings (optional) OpenSearch-specific settings, or null on other databases
		 * @param kafkaSettings      (optional) Kafka-specific settings, or null on other databases
		 * @throws NullPointerException     if any mandatory arguments are null
		 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
		 *                                  empty
		 */
		public User
		{
			requireThat(name, "name").isStripped().isNotEmpty();
			requireThat(role, "role").isNotNull();
			requireThat(password, "password").isStripped().isNotEmpty();
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(User.class).
				add("name", name).
				add("role", role).
				add("password", password).
				add("mySqlSettings", mySqlSettings).
				add("postgresqlSettings", postgresqlSettings).
				add("openSearchSettings", openSearchSettings).
				add("kafkaSettings", kafkaSettings).
				toString();
		}
	}

	/**
	 * Database user roles.
	 */
	public enum UserRole
	{
		/**
		 * A system administrator.
		 */
		ADMIN,
		/**
		 * A normal user.
		 */
		NORMAL;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static UserRole fromJson(JsonNode json)
		{
			String role = json.textValue();
			if (role.equals("primary"))
				return ADMIN;
			return valueOf(role.toUpperCase(Locale.ROOT));
		}
	}

	/**
	 * MySQL-specific user settings.
	 *
	 * @param authenticationType the authentication method that must be used to connect to the database
	 */
	public record MySqlSettings(MySqlAuthenticationType authenticationType)
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param json the JSON representation, or {@code null} if omitted
		 * @return the settings, or {@code null} if {@code json} is {@code null}
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static MySqlSettings getByJson(JsonNode json)
		{
			if (json == null)
				return null;
			MySqlAuthenticationType authenticationType = MySqlAuthenticationType.fromJson(json.get("auth_plugin"));
			return new MySqlSettings(authenticationType);
		}

		/**
		 * Creates a new instance.
		 *
		 * @param authenticationType the authentication method that must be used to connect to the database
		 * @throws NullPointerException if {@code authenticationType} is null
		 */
		public MySqlSettings
		{
			requireThat(authenticationType, "authenticationType").isNotNull();
		}
	}

	/**
	 * Authentication types for the MySQL database.
	 */
	public enum MySqlAuthenticationType
	{
		/**
		 * The <a
		 * href="https://dev.mysql.com/doc/refman/8.4/en/native-pluggable-authentication.html">mysql_native_password</a>
		 * authentication method.
		 * <p>
		 * In MySQL 8.0, the default authentication plugin has changed from {@code mysql_native_password} to
		 * {@code caching_sha2_password}.
		 * <p>
		 * The {@code mysql_native_password} plugin is deprecated as of MySQL 8.0.34, disabled by default as of
		 * MySQL 8.4.0, and removed as of MySQL 9.0.0.
		 */
		NATIVE_PASSWORD,
		/**
		 * The <a
		 * href="https://dev.mysql.com/doc/refman/9.1/en/caching-sha2-pluggable-authentication.html">caching_sha2_password</a>
		 * authentication method.
		 */
		CACHING_SHA2_PASSWORD;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static MySqlAuthenticationType fromJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
	}

	/**
	 * PostgreSQL-specific user settings.
	 *
	 * @param mayReplicate {@code true} if the user has replication rights
	 */
	public record PostgresqlSettings(boolean mayReplicate)
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param client the client configuration
		 * @param json   the JSON representation, or {@code null} if omitted
		 * @return the settings, or {@code null} if {@code json} is {@code null}
		 * @throws NullPointerException     if {@code client} is null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static PostgresqlSettings getByJson(DigitalOceanClient client, JsonNode json)
		{
			if (json == null)
				return null;
			boolean mayReplicate = client.getBoolean(json, "pg_allow_replication");
			return new PostgresqlSettings(mayReplicate);
		}
	}

	/**
	 * OpenSearch-specific user settings.
	 *
	 * @param indexToPermission a mapping from a regular expression to a permission. The key is a regex pattern
	 *                          that matches index names, and the value specifies the permission level for the
	 *                          matching indexes.
	 */
	public record OpenSearchSettings(Map<String, OpenSearchPermission> indexToPermission)
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param json the JSON representation, or {@code null} if omitted
		 * @return the settings, or {@code null} if {@code json} is {@code null}
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static OpenSearchSettings getByJson(JsonNode json)
		{
			if (json == null)
				return null;
			Map<String, OpenSearchPermission> regexToPermission = new HashMap<>();
			for (JsonNode element : json.get("opensearch_acl"))
			{
				String regex = element.get("index").textValue();
				OpenSearchPermission permission = OpenSearchPermission.fromJson(element.get("permission"));
				OpenSearchPermission oldPermission = regexToPermission.put(regex, permission);
				assert that(oldPermission, "oldPermission").
					withContext(regexToPermission, "regexToPermission").
					withContext(json.toPrettyString(), "json").
					isNull().elseThrow();
			}
			return new OpenSearchSettings(regexToPermission);
		}

		/**
		 * Creates a new instance.
		 *
		 * @param indexToPermission a mapping from a regular expression to a permission. The key is a regex
		 *                          pattern that matches index names, and the value specifies the permission level
		 *                          for the matching indexes.
		 * @throws NullPointerException if {@code indexToPermission} is null
		 */
		public OpenSearchSettings
		{
			requireThat(indexToPermission, "indexToPermission").isNotNull();
		}
	}

	/**
	 * Permissions that may be granted to a user on OpenSearch databases.
	 */
	public enum OpenSearchPermission
	{
		/**
		 * The user cannot access the indexes.
		 */
		DENY,
		/**
		 * The user may read from the indexes.
		 */
		READ,
		/**
		 * The user may write to the indexes.
		 */
		WRITE,
		/**
		 * The user may read and write to the indexes.
		 */
		READ_WRITE,
		/**
		 * The user may read, write and administer the indexes.
		 */
		ADMIN;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static OpenSearchPermission fromJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
	}

	/**
	 * Kafka-specific user settings.
	 *
	 * @param certificate access certificate for TLS client authentication
	 * @param key         access key for TLS client authentication
	 * @param permissions maps topics to the permission that is granted to the user over those topics
	 */
	public record KafkaSettings(String certificate, String key, Set<KafkaTopicToPermission> permissions)
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param json the JSON representation, or {@code null} if omitted
		 * @return the settings, or {@code null} if {@code json} is {@code null}
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static KafkaSettings getByJson(JsonNode json)
		{
			if (json == null)
				return null;
			String certificate = json.get("access_cert").textValue();
			String key = json.get("access_key").textValue();
			JsonNode settingsNode = json.get("settings");
			Set<KafkaTopicToPermission> permissions = new HashSet<>();
			for (JsonNode element : settingsNode.get("acl"))
				permissions.add(KafkaTopicToPermission.getByJson(element));
			return new KafkaSettings(certificate, key, permissions);
		}

		/**
		 * Creates a new instance.
		 *
		 * @param certificate access certificate for TLS client authentication
		 * @param key         access key for TLS client authentication
		 * @param permissions maps topics to the permission that is granted to the user over those topics
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
		 *                                  empty
		 */
		public KafkaSettings
		{
			requireThat(certificate, "certificate").isStripped().isNotEmpty();
			requireThat(key, "key").isStripped().isNotEmpty();
			requireThat(permissions, "permissions").isNotNull();
		}
	}

	/**
	 * Kafka-specific permissions.
	 *
	 * @param id         the ID of the ACL
	 * @param topics     a regex pattern that matches the topic names
	 * @param permission the permission that is granted to the user over the topics
	 */
	public record KafkaTopicToPermission(String id, String topics, KafkaPermission permission)
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param json the JSON representation, or {@code null} if omitted
		 * @return the settings, or {@code null} if {@code json} is {@code null}
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static KafkaTopicToPermission getByJson(JsonNode json)
		{
			if (json == null)
				return null;
			String id = json.get("id").textValue();
			String topics = json.get("topic").textValue();
			KafkaPermission permission = KafkaPermission.fromJson(json.get("permission"));
			return new KafkaTopicToPermission(id, topics, permission);
		}

		/**
		 * Creates a new instance.
		 *
		 * @param id         the ID of the ACL
		 * @param topics     a regex pattern that matches the topic names
		 * @param permission the permission that is granted to the user over the topics
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
		 *                                  empty
		 */
		public KafkaTopicToPermission
		{
			requireThat(id, "id").isStripped().isNotEmpty();
			requireThat(topics, "topics").isStripped().isNotEmpty();
			requireThat(permission, "permission").isNotNull();
		}
	}

	/**
	 * Permissions that may be granted to a user on Kafka databases.
	 */
	public enum KafkaPermission
	{
		/**
		 * The user may consume messages from the topics.
		 */
		CONSUME,
		/**
		 * The user may publish messages to the topics.
		 */
		PRODUCE,
		/**
		 * The user may consume or publish messages to the topics.
		 */
		PRODUCE_CONSUME,
		/**
		 * The user may consume, publish and administer the topics.
		 */
		ADMIN,
		;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static KafkaPermission fromJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
	}

	/**
	 * A firewall rule.
	 */
	public static final class FirewallRule
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param client the client configuration
		 * @param json   the JSON representation
		 * @return the permission for one or more topics
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 */
		private static FirewallRule getByJson(DigitalOceanClient client, JsonNode json)
		{
			String id = json.get("uuid").textValue();
			String clusterId = json.get("cluster_uuid").textValue();
			String resourceTypeAsString = json.get("type").textValue();
			ResourceType resourceType = switch (resourceTypeAsString)
			{
				case "droplet" -> ResourceType.DROPLET;
				case "k8s" -> ResourceType.KUBERNETES;
				case "ip_address" -> ResourceType.IP_ADDRESS;
				case "tag" -> ResourceType.TAG;
				case "app" -> ResourceType.APPLICATION;
				default -> throw new AssertionError("Unsupported resource type: " + resourceTypeAsString);
			};
			String resourceId = json.get("value").textValue();
			Instant createdAt = Instant.parse(json.get("created_at").textValue());
			return new FirewallRule(client, id, clusterId, resourceType, resourceId, createdAt);
		}

		private final DigitalOceanClient client;
		private final String id;
		private final String clusterId;
		private final ResourceType resourceType;
		private final String resourceId;
		private final Instant createdAt;

		/**
		 * Creates a new instance.
		 *
		 * @param client       the client configuration
		 * @param id           the ID of the rule
		 * @param clusterId    the ID of the cluster
		 * @param resourceType the type of resource that is allowed to access the database cluster
		 * @param resourceId   the ID of the specific resource, the name of a tag applied to a group of resources,
		 *                     or the IP address
		 * @param createdAt    the time the firewall rule was created
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                    <li>{@code id} is not a valid UUID per RFC 9562.</li>
		 *                                    <li>any of the arguments contains leading or trailing whitespace
		 *                                    or are empty.</li>
		 *                                  </ul>
		 */
		public FirewallRule(DigitalOceanClient client, String id, String clusterId, ResourceType resourceType,
			String resourceId, Instant createdAt)
		{
			// Regex taken from
			// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_create_cluster
			requireThat(id, "id").matches(Strings.UUID);
			requireThat(clusterId, "clusterId").matches(Strings.UUID);
			requireThat(resourceType, "resourceType").isNotNull();
			requireThat(resourceId, "resourceId").isStripped().isNotEmpty();
			requireThat(createdAt, "createdAt").isNotNull();

			this.client = client;
			this.id = id;
			this.clusterId = clusterId;
			this.resourceType = resourceType;
			this.resourceId = resourceId;
			this.createdAt = createdAt;
		}

		/**
		 * Returns the ID of the rule.
		 *
		 * @return the ID
		 */
		public String getId()
		{
			return id;
		}

		/**
		 * Returns the ID of the cluster
		 *
		 * @return the ID
		 */
		public String getClusterId()
		{
			return clusterId;
		}

		/**
		 * Returns the type of resource that is allowed to access the database cluster.
		 *
		 * @return the type
		 */
		public ResourceType getResourceType()
		{
			return resourceType;
		}

		/**
		 * Returns the ID of the specific resource, the name of a tag applied to a group of resources, or the IP
		 * address.
		 *
		 * @return the ID
		 */
		public String getResourceId()
		{
			return resourceId;
		}

		/**
		 * Returns the time the firewall rule was created.
		 *
		 * @return the time
		 */
		public Instant getCreatedAt()
		{
			return createdAt;
		}

		/**
		 * Converts this object to a type that is accepted by {@code DatabaseCreator}.
		 *
		 * @return the {@code DatabaseCreator.NodePool}
		 */
		public DatabaseCreator.FirewallRule forCreator()
		{
			return new DatabaseCreator.FirewallRule(client, id, resourceType, resourceId);
		}
	}
}