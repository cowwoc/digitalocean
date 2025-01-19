package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.exception.PermissionDeniedException;
import com.github.cowwoc.digitalocean.internal.util.Strings;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import com.github.cowwoc.digitalocean.util.CreateResult;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.UNPROCESSABLE_ENTITY_422;

/**
 * Creates a new database cluster.
 */
public final class DatabaseCreator
{
	private final DigitalOceanClient client;
	private final String name;
	private final DatabaseType databaseType;
	private String version;
	private final int numberOfStandbyNodes;
	private final DropletType.Id dropletType;
	private final Zone.Id zone;
	private Vpc.Id vpc;
	private final Set<String> tags = new HashSet<>();
	private String projectId = "";
	private final Set<FirewallRule> firewallRules = new HashSet<>();
	private int additionalStorageInMiB;
	private RestoreFrom restoreFrom;

	/**
	 * Creates a new instance.
	 *
	 * @param client               the client configuration
	 * @param name                 the name of the cluster
	 * @param databaseType         the type of the database
	 * @param numberOfStandbyNodes the number of nodes in the cluster. The cluster includes one primary node,
	 *                             and may include one or two standby nodes.
	 * @param dropletType          the machine type of the droplet
	 * @param zone                 the zone to create the cluster in
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code name} contains leading or trailing whitespace or is
	 *                                    empty.</li>
	 *                                    <li>{@code numberOfStandbyNodes} is negative, or greater than 2.</li>
	 *                                  </ul>
	 * @throws IllegalStateException    if the client is closed
	 */
	DatabaseCreator(DigitalOceanClient client, String name, DatabaseType databaseType,
		int numberOfStandbyNodes, DropletType.Id dropletType, Zone.Id zone)
	{
		requireThat(client, "client").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(databaseType, "databaseType").isNotNull();
		requireThat(numberOfStandbyNodes, "numberOfStandbyNodes").isBetween(0, true, 2, true);
		requireThat(dropletType, "dropletType").isNotNull();
		requireThat(zone, "zone").isNotNull();
		this.client = client;
		this.name = name;
		this.databaseType = databaseType;
		this.numberOfStandbyNodes = numberOfStandbyNodes;
		this.dropletType = dropletType;
		this.zone = zone;
	}

	/**
	 * Returns the name of the cluster.
	 *
	 * @return the name
	 */
	public String name()
	{
		return name;
	}

	/**
	 * Returns the type of the database.
	 *
	 * @return the type
	 */
	public DatabaseType databaseType()
	{
		return databaseType;
	}

	/**
	 * Returns the version number of the database software.
	 *
	 * @return an empty string if the unspecified default value should be used
	 */
	public String version()
	{
		return version;
	}

	/**
	 * Sets the major version number of the database software.
	 *
	 * @param version an empty string if the unspecified default value should be used
	 * @return this
	 * @throws IllegalArgumentException if {@code version} contains leading or trailing whitespace
	 */
	public DatabaseCreator version(String version)
	{
		requireThat(version, "version").isStripped();
		this.version = version;
		return this;
	}

	/**
	 * Returns the number of nodes in the cluster. The cluster includes one primary node, and may include one or
	 * two standby nodes.
	 *
	 * @return the number of standby nodes
	 */
	public int numberOfStandbyNodes()
	{
		return numberOfStandbyNodes;
	}

	/**
	 * Returns the machine type of the droplet.
	 *
	 * @return the machine type
	 */
	public DropletType.Id dropletType()
	{
		return dropletType;
	}

	/**
	 * Returns the zone to create the cluster in.
	 *
	 * @return the zone
	 */
	public Zone.Id zone()
	{
		return zone;
	}

	/**
	 * Returns the VPC to deploy the cluster in.
	 *
	 * @return {@code null} to deploy the cluster into the zone's
	 *  {@link Vpc#getDefault(DigitalOceanClient, Zone.Id) default} VPC
	 */
	public Vpc.Id vpc()
	{
		return vpc;
	}

	/**
	 * Sets the VPC to deploy the cluster in.
	 *
	 * @param vpc {@code null} to deploy the cluster into the zone's
	 *            {@link Vpc#getDefault(DigitalOceanClient, Zone.Id) default} VPC
	 * @return this
	 */
	public DatabaseCreator vpc(Vpc.Id vpc)
	{
		this.vpc = vpc;
		return this;
	}

	/**
	 * Adds a tag to the cluster.
	 *
	 * @param tag the tag to add
	 * @return this
	 * @throws NullPointerException     if {@code tag} is null
	 * @throws IllegalArgumentException if the tag:
	 *                                  <ul>
	 *                                    <li>contains any characters other than letters, numbers, colons,
	 *                                    dashes and underscores.</li>
	 *                                    <li>is longer than 255 characters.</li>
	 *                                  </ul>
	 */
	public DatabaseCreator tag(String tag)
	{
		// Discovered empirically: DigitalOcean drops all tags silently if any of them contain invalid characters.
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/tags_create
		requireThat(tag, "tag").matches("^[a-zA-Z0-9_\\-:]+$").length().isLessThanOrEqualTo(255);
		this.tags.add(tag);
		return this;
	}

	/**
	 * Sets the tags of the cluster.
	 *
	 * @param tags the tags
	 * @return this
	 * @throws NullPointerException     if {@code tags} is null
	 * @throws IllegalArgumentException if any of the tags:
	 *                                  <ul>
	 *                                    <li>are null.</li>
	 *                                    <li>contain any characters other than letters, numbers, colons,
	 *                                    dashes and underscores.</li>
	 *                                    <li>is longer than 255 characters.</li>
	 *                                  </ul>
	 */
	public DatabaseCreator tags(Collection<String> tags)
	{
		requireThat(tags, "tags").isNotNull().doesNotContain(null);
		this.tags.clear();
		for (String tag : tags)
		{
			requireThat(tag, "tag").withContext(tags, "tags").matches("^[a-zA-Z0-9_\\-:]+$").
				length().isLessThanOrEqualTo(255);
			this.tags.add(tag);
		}
		return this;
	}

	/**
	 * Returns the cluster's tags.
	 *
	 * @return the tags
	 */
	public Set<String> tags()
	{
		return Set.copyOf(tags);
	}

	/**
	 * Returns the ID of the project that the cluster is assigned to.
	 *
	 * @return an empty string if the cluster is assigned to the default project
	 */
	public String projectId()
	{
		return projectId;
	}

	/**
	 * Sets the ID of the project that the cluster is assigned to.
	 *
	 * @param projectId an empty string to assign the cluster to the default project
	 * @return this
	 * @throws NullPointerException     if {@code projectId} is null
	 * @throws IllegalArgumentException if {@code projectId} contains leading or trailing whitespace
	 */
	public DatabaseCreator projectId(String projectId)
	{
		requireThat(projectId, "projectId").isStripped();
		this.projectId = projectId;
		return this;
	}

	/**
	 * Adds a firewall rule to the cluster.
	 *
	 * @param firewallRule the rule
	 * @return this
	 * @throws NullPointerException if {@code rule} is null
	 */
	public DatabaseCreator firewallRule(FirewallRule firewallRule)
	{
		requireThat(firewallRule, "firewallRule").isNotNull();
		this.firewallRules.add(firewallRule);
		return this;
	}

	/**
	 * Sets the firewall rules of the cluster.
	 *
	 * @param firewallRules the firewall rules
	 * @return this
	 * @throws NullPointerException     if {@code firewallRules} is null
	 * @throws IllegalArgumentException if any of the rules are null
	 */
	public DatabaseCreator firewallRules(Collection<FirewallRule> firewallRules)
	{
		requireThat(firewallRules, "firewallRules").isNotNull().doesNotContain(null);
		this.firewallRules.clear();
		this.firewallRules.addAll(firewallRules);
		return this;
	}

	/**
	 * Returns the cluster's tags.
	 *
	 * @return the tags
	 */
	public Set<FirewallRule> firewallRules()
	{
		return Set.copyOf(firewallRules);
	}

	/**
	 * Returns the amount of additional storage that is accessible to the cluster.
	 *
	 * @return the amount in MiB
	 */
	public int additionalStorageInMiB()
	{
		return additionalStorageInMiB;
	}

	/**
	 * Sets the amount of additional storage that is accessible to the cluster.
	 *
	 * @param additionalStorageInMiB the amount in MiB
	 * @throws IllegalArgumentException if {@code additionalStorageInMiB} is negative
	 */
	public void additionalStorageInMiB(int additionalStorageInMiB)
	{
		requireThat(additionalStorageInMiB, "additionalStorageInMiB").isNotNegative();
		this.additionalStorageInMiB = additionalStorageInMiB;
	}

	/**
	 * Returns the backup to restore data from.
	 *
	 * @return {@code null} if no backup will be restored
	 */
	public RestoreFrom restoreFrom()
	{
		return restoreFrom;
	}

	/**
	 * Sets the backup to restore data from.
	 *
	 * @param restoreFrom {@code null} to create an empty cluster
	 */
	public void restoreFrom(RestoreFrom restoreFrom)
	{
		this.restoreFrom = restoreFrom;
	}

	/**
	 * Creates a new database cluster.
	 *
	 * @return the new or conflicting cluster
	 * @throws IllegalArgumentException  if the {@code dropletType} is not supported by managed databases
	 * @throws IllegalStateException     if the client is closed
	 * @throws PermissionDeniedException if the request exceeded the client's droplet limit
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws TimeoutException          if the request times out before receiving a response. This might
	 *                                   indicate network latency or server overload.
	 * @throws InterruptedException      if the thread is interrupted while waiting for a response. This can
	 *                                   happen due to shutdown signals.
	 */
	public CreateResult<Database> create()
		throws PermissionDeniedException, IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_create_cluster
		JsonMapper jm = client.getJsonMapper();
		ObjectNode requestBody = jm.createObjectNode().
			put("name", name).
			put("engine", databaseType.getSlug()).
			put("num_nodes", numberOfStandbyNodes + 1).
			put("size", dropletType.toString()).
			put("region", zone.getValue());
		if (!version.isEmpty())
			requestBody.put("version", version);
		if (vpc != null)
			requestBody.put("private_network_uuid", vpc.getValue());
		if (!tags.isEmpty())
		{
			ArrayNode tagsNode = requestBody.putArray("tags");
			for (String tag : tags)
				tagsNode.add(tag);
		}
		if (!projectId.isEmpty())
			requestBody.put("project_id", projectId);
		if (!firewallRules.isEmpty())
		{
			ArrayNode firewallRulesNode = requestBody.putArray("rules");
			for (FirewallRule firewallRule : firewallRules)
				firewallRulesNode.add(firewallRule.toJson());
		}
		if (additionalStorageInMiB > 0)
			requestBody.put("storage_size_mib", additionalStorageInMiB);
		if (restoreFrom != null)
			requestBody.set("backup_restore", restoreFrom.toJson());

		Request request = client.createRequest(REST_SERVER.resolve("v2/databases"), requestBody).
			method(POST);
		ContentResponse serverResponse = client.send(request);
		return switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				JsonNode body = client.getResponseBody(serverResponse);
				yield CreateResult.created(Database.getByJson(client, body.get("database")));
			}
			case UNPROCESSABLE_ENTITY_422 ->
			{
				// Discovered empirically: not all droplet types are acceptable.
				// BUG: https://ideas.digitalocean.com/managed-database/p/the-api-should-specify-what-resources-may-be-used-with-managed-databases
				LoggerFactory.getLogger(DatabaseCreator.class).warn("Unexpected response: {}\nRequest: {}",
					client.toString(serverResponse), client.toString(request));
				JsonNode json = client.getResponseBody(serverResponse);
				String message = json.get("message").textValue();
				switch (message)
				{
					case "invalid size" -> throw new IllegalArgumentException("dropletType may not be " + dropletType);
					case "cluster name is not available" ->
					{
						Database conflict = Database.getByPredicate(client, cluster -> cluster.getName().equals(name));
						if (conflict != null)
							yield CreateResult.conflictedWith(conflict);
						throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
							"Request: " + client.toString(request));
					}
					default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) +
						"\n" +
						"Request: " + client.toString(request));
				}
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		};
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DatabaseCreator.class).
			add("name", name).
			add("databaseType", databaseType).
			add("version", version).
			add("numberOfStandbyNodes", numberOfStandbyNodes).
			add("dropletType", dropletType).
			add("zone", zone).
			add("vpc", vpc).
			add("tags", tags).
			add("projectId", projectId).
			add("firewallRules", firewallRules).
			add("additionalStorageInMiB", additionalStorageInMiB).
			add("restoreFrom", restoreFrom).
			toString();
	}

	/**
	 * A firewall rule.
	 */
	public static final class FirewallRule
	{
		private final DigitalOceanClient client;
		private final String id;
		private final ResourceType resourceType;
		private final String resourceId;

		/**
		 * Creates a new instance.
		 *
		 * @param client       the client configuration
		 * @param id           (optional) the ID of the rule, or an empty string to omit
		 * @param resourceType the type of resource that is allowed to access the database cluster
		 * @param resourceId   the ID of the specific resource, the name of a tag applied to a group of resources,
		 *                     or the IP address
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                    <li>{@code id} is not a valid UUID per RFC 9562.</li>
		 *                                    <li>any of the arguments contains leading or trailing whitespace
		 *                                    or are empty.</li>
		 *                                  </ul>
		 */
		public FirewallRule(DigitalOceanClient client, String id, ResourceType resourceType, String resourceId)
		{
			requireThat(client, "client").isNotNull();
			// Regex taken from
			// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_create_cluster
			requireThat(id, "id").isStripped();
			if (!id.isEmpty())
				requireThat(id, "id").matches(Strings.UUID);
			requireThat(resourceType, "resourceType").isNotNull();
			requireThat(resourceId, "resourceId").isStripped().isNotEmpty();
			this.client = client;
			this.id = id;
			this.resourceType = resourceType;
			this.resourceId = resourceId;
		}

		/**
		 * Returns the ID of the rule.
		 *
		 * @return an empty string if undefined
		 */
		public String getId()
		{
			return id;
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
		 * @return the ID, tag or IP address
		 */
		public String getResourceId()
		{
			return resourceId;
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
			if (!id.isEmpty())
				json.put("uuid", id);
			json.put("type", resourceType.name().toLowerCase(Locale.ROOT));
			json.put("value", resourceId);
			return json;
		}
	}

	/**
	 * Identifies a backup to restore from.
	 */
	public static final class RestoreFrom
	{
		private final DigitalOceanClient client;
		private final String databaseName;
		private final Instant createdAt;

		/**
		 * Creates a new instance.
		 *
		 * @param client       the client configuration
		 * @param databaseName the name of an existing database cluster from which the backup will be restored
		 * @param createdAt    the time that the backup was created
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code databaseName} contains leading or trailing whitespace or is
		 *                                  empty
		 */
		public RestoreFrom(DigitalOceanClient client, String databaseName, Instant createdAt)
		{
			requireThat(client, "client").isNotNull();
			requireThat(databaseName, "databaseName").isStripped().isNotEmpty();
			requireThat(createdAt, "createdAt").isNotNull();
			this.client = client;
			this.databaseName = databaseName;
			this.createdAt = createdAt;
		}

		/**
		 * Returns the name of an existing database cluster from which the backup will be restored.
		 *
		 * @return the name
		 */
		public String getDatabaseName()
		{
			return databaseName;
		}

		/**
		 * Returns the time that the backup was created.
		 *
		 * @return the time
		 */
		public Instant getCreatedAt()
		{
			return createdAt;
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
			json.put("database_name", databaseName);
			json.put("backup_created_at", createdAt.toString());
			return json;
		}
	}

	/**
	 * The schedule for when maintenance activities may be performed on the cluster.
	 */
	public static final class MaintenanceSchedule
	{
		private final DigitalOceanClient client;
		private final OffsetTime hour;
		private final DayOfWeek day;

		/**
		 * Creates a new schedule.
		 *
		 * @param client the client configuration
		 * @param hour   the start hour when maintenance may take place
		 * @param day    the day of the week when maintenance may take place
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code hour} contains a non-zero minute, second or nano component
		 */
		public MaintenanceSchedule(DigitalOceanClient client, OffsetTime hour, DayOfWeek day)
		{
			requireThat(client, "client").isNotNull();
			requireThat(hour, "hour").isNotNull();
			requireThat(hour.getMinute(), "hour.getMinute()").isZero();
			requireThat(hour.getSecond(), "hour.getSecond()").isZero();
			requireThat(hour.getNano(), "hour.getNano()").isZero();
			requireThat(day, "day").isNotNull();

			this.client = client;
			this.hour = hour;
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
			OffsetTime hourAtUtc = hour.withOffsetSameInstant(ZoneOffset.UTC);
			json.put("hour", Strings.HOUR_MINUTE.format(hourAtUtc));
			json.put("day", day.name().toLowerCase(Locale.ROOT));
			return json;
		}

		/**
		 * Returns the start time when maintenance may take place.
		 *
		 * @return the start time
		 */
		public OffsetTime hour()
		{
			return hour;
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
			return Objects.hash(hour, day);
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof MaintenanceSchedule other && other.hour.isEqual(hour) && other.day.equals(day);
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(MaintenanceSchedule.class).
				add("hour", hour).
				add("day", day).
				toString();
		}
	}
}