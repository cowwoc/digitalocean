package io.github.cowwoc.digitalocean.database.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cowwoc.digitalocean.core.id.DatabaseDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.DatabaseId;
import io.github.cowwoc.digitalocean.core.id.DatabaseTypeId;
import io.github.cowwoc.digitalocean.core.id.ProjectId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.id.VpcId;
import io.github.cowwoc.digitalocean.core.internal.parser.AbstractParser;
import io.github.cowwoc.digitalocean.core.internal.parser.CoreParser;
import io.github.cowwoc.digitalocean.core.internal.util.Strings;
import io.github.cowwoc.digitalocean.database.resource.Database;
import io.github.cowwoc.digitalocean.database.resource.Database.Connection;
import io.github.cowwoc.digitalocean.database.resource.Database.FirewallRule;
import io.github.cowwoc.digitalocean.database.resource.Database.KafkaPermission;
import io.github.cowwoc.digitalocean.database.resource.Database.KafkaSettings;
import io.github.cowwoc.digitalocean.database.resource.Database.KafkaTopicToPermission;
import io.github.cowwoc.digitalocean.database.resource.Database.MaintenanceSchedule;
import io.github.cowwoc.digitalocean.database.resource.Database.MySqlAuthenticationType;
import io.github.cowwoc.digitalocean.database.resource.Database.MySqlSettings;
import io.github.cowwoc.digitalocean.database.resource.Database.OpenSearchDashboard;
import io.github.cowwoc.digitalocean.database.resource.Database.OpenSearchPermission;
import io.github.cowwoc.digitalocean.database.resource.Database.OpenSearchSettings;
import io.github.cowwoc.digitalocean.database.resource.Database.PostgresqlSettings;
import io.github.cowwoc.digitalocean.database.resource.Database.Status;
import io.github.cowwoc.digitalocean.database.resource.Database.User;
import io.github.cowwoc.digitalocean.database.resource.Database.UserRole;
import io.github.cowwoc.digitalocean.database.resource.DatabaseCreator;
import io.github.cowwoc.digitalocean.database.resource.DatabaseCreator.FirewallRuleBuilder;
import io.github.cowwoc.digitalocean.database.resource.DatabaseCreator.RestoreFrom;
import io.github.cowwoc.digitalocean.database.resource.DatabaseDropletType;
import io.github.cowwoc.digitalocean.database.resource.Endpoint;
import io.github.cowwoc.digitalocean.database.resource.ResourceType;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.that;

/**
 * Parses server responses.
 */
public final class DatabaseParser extends AbstractParser
{
	private final CoreParser coreParser;

	/**
	 * Creates a new DatabaseParser.
	 *
	 * @param client the client configuration
	 */
	public DatabaseParser(DefaultDatabaseClient client)
	{
		super(client);
		this.coreParser = new CoreParser(client);
	}

	@Override
	protected DefaultDatabaseClient getClient()
	{
		return (DefaultDatabaseClient) super.getClient();
	}

	/**
	 * Convert a DatabaseDropletType from its server representation.
	 *
	 * @param json      the JSON representation
	 * @param regionIds the regions that the droplets types are in
	 * @return the droplet type
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public DatabaseDropletType dropletTypeFromServer(JsonNode json, Set<RegionId> regionIds)
	{
		DatabaseDropletTypeId id = DatabaseDropletTypeId.of(json.textValue());
		return new DefaultDatabaseDropletType(id, regionIds);
	}

	/**
	 * Converts DatabaseType.Id from its server representation.
	 *
	 * @param json the server representation
	 * @return the database
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public DatabaseTypeId databaseTypeIdFromServer(JsonNode json)
	{
		String value = json.textValue();
		return switch (value)
		{
			case "pg" -> DatabaseTypeId.POSTGRESQL;
			case "mysql" -> DatabaseTypeId.MYSQL;
			case "redis" -> DatabaseTypeId.REDIS;
			case "mongodb" -> DatabaseTypeId.MONGODB;
			case "kafka" -> DatabaseTypeId.KAFKA;
			case "opensearch" -> DatabaseTypeId.OPENSEARCH;
			default -> throw new IllegalArgumentException("Unsupported value: " + value);
		};
	}

	/**
	 * Converts DatabaseType.Id to its server representation.
	 *
	 * @param typeId the ID
	 * @return the server representation
	 * @throws NullPointerException     if {@code typeId} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public String databaseTypeIdToServer(DatabaseTypeId typeId)
	{
		return switch (typeId)
		{
			case POSTGRESQL -> "pg";
			case MYSQL -> "mysql";
			case REDIS -> "redis";
			case MONGODB -> "mongodb";
			case KAFKA -> "kafka";
			case OPENSEARCH -> "opensearch";
		};
	}

	/**
	 * Converts Database from its server representation.
	 *
	 * @param json the server representation
	 * @return the database
	 * @throws NullPointerException  if {@code json} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Database databaseFromServer(JsonNode json)
		throws IOException, InterruptedException
	{
		try
		{
			DatabaseId id = DatabaseId.of(json.get("id").textValue());
			String name = json.get("name").textValue();
			DatabaseTypeId databaseTypeId = databaseTypeIdFromServer(json.get("engine"));
			String version = json.get("version").textValue();
			String semanticVersion = json.get("semantic_version").textValue();
			int numberOfNodes = getInt(json, "num_nodes");
			DatabaseDropletTypeId dropletType = DatabaseDropletTypeId.of(json.get("size").textValue());

			RegionId regionId = coreParser.regionIdFromServer(json.get("region"));
			Status status = statusFromServer(json.get("status"));

			DefaultDatabaseClient client = getClient();
			JsonNode vpcNode = json.get("private_network_uuid");
			VpcId vpcId;
			if (vpcNode == null)
				vpcId = client.getDefaultVpcId(regionId);
			else
				vpcId = VpcId.of(vpcNode.textValue());

			Set<String> tags = getElements(json, "tags", JsonNode::textValue);
			Set<String> databaseNames = getElements(json, "db_names", JsonNode::textValue);

			OpenSearchDashboard openSearchDashboard = openSearchDashboardFromServer(json.get("ui_connection"));
			Connection publicConnection = connectionFromServer(json.get("connection"));
			Connection privateConnection = connectionFromServer(json.get("private_connection"));
			Connection standbyPublicConnection = connectionFromServer(json.get("standby_connection"));
			Connection standbyPrivateConnection = connectionFromServer(json.get("standby_private_connection"));
			Set<User> users = getElements(json, "users", element -> userFromServer(databaseTypeId, element));
			MaintenanceSchedule maintenanceSchedule = maintenanceScheduleFromServer(json.get("maintenance_window"));
			ProjectId projectId = ProjectId.of(json.get("project_id").textValue());
			Set<FirewallRule> firewallRules = getElements(json, "rules", this::firewallRuleFromServer);

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
			Set<Endpoint> metricsEndpoints = getElements(json, "metrics_endpoints", this::endpointFromServer);

			Instant createdAt = Instant.parse(json.get("created_at").textValue());
			return new DefaultDatabase(client, id, name, databaseTypeId, version, semanticVersion,
				numberOfNodes, dropletType, regionId, status, vpcId, tags, databaseNames, openSearchDashboard,
				publicConnection, privateConnection, standbyPublicConnection, standbyPrivateConnection, users,
				maintenanceSchedule, projectId, firewallRules, versionEndOfLife, versionEndOfAvailability,
				additionalStorageInMiB, metricsEndpoints, createdAt);
		}
		catch (RuntimeException e)
		{
			LoggerFactory.getLogger(Database.class).warn(json.toPrettyString(), e);
			throw e;
		}
	}

	/**
	 * Converts FirewallRule from its server representation.
	 *
	 * @param json the server representation
	 * @return the permission for one or more topics
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private FirewallRule firewallRuleFromServer(JsonNode json)
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
		return new FirewallRule(id, clusterId, resourceType, resourceId, createdAt);
	}

	/**
	 * Converts FirewallRule to its server representation.
	 *
	 * @param value the FirewallRule
	 * @return the server representation
	 * @throws NullPointerException if {@code value} is null
	 */
	public JsonNode firewallRuleToServer(FirewallRuleBuilder value)
	{
		ObjectNode json = getClient().getJsonMapper().createObjectNode();
		if (!value.id().isEmpty())
			json.put("uuid", value.id());
		json.put("type", value.resourceType().name().toLowerCase(Locale.ROOT));
		json.put("value", value.resourceId());
		return json;
	}

	/**
	 * Converts User from its server representation.
	 *
	 * @param databaseTypeId the database type
	 * @param json           the server representation
	 * @return the user
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private User userFromServer(DatabaseTypeId databaseTypeId, JsonNode json)
	{
		String name = json.get("name").textValue();
		UserRole role = userRoleFromServer(json.get("role"));
		String password = json.get("password").textValue();
		MySqlSettings mySqlSettings;
		PostgresqlSettings postgreSqlSettings;
		OpenSearchSettings openSearchSettings;
		KafkaSettings kafkaSettings;

		switch (databaseTypeId)
		{
			case MYSQL ->
			{
				mySqlSettings = mySqlSettingsFromServer(json.get("settings"));
				postgreSqlSettings = null;
				openSearchSettings = null;
				kafkaSettings = null;
			}
			case POSTGRESQL ->
			{
				mySqlSettings = null;
				postgreSqlSettings = postgresqlSettingsFromServer(json.get("settings"));
				openSearchSettings = null;
				kafkaSettings = null;
			}
			case OPENSEARCH ->
			{
				mySqlSettings = null;
				postgreSqlSettings = null;
				openSearchSettings = openSearchSettingsFromServer(json.get("settings"));
				kafkaSettings = null;
			}
			case KAFKA ->
			{
				mySqlSettings = null;
				postgreSqlSettings = null;
				openSearchSettings = null;
				kafkaSettings = kafkaSettingsFromServer(json.get("settings"));
			}
			default -> throw new AssertionError("Unsupported value: " + databaseTypeId);
		}

		return new User(name, role, password, mySqlSettings, postgreSqlSettings, openSearchSettings,
			kafkaSettings);
	}

	/**
	 * Converts MySqlSettings from its server representation.
	 *
	 * @param json the server representation, or {@code null} if omitted
	 * @return the settings, or {@code null} if {@code json} is {@code null}
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private MySqlSettings mySqlSettingsFromServer(JsonNode json)
	{
		if (json == null)
			return null;
		MySqlAuthenticationType authenticationType = mySqlAuthenticationTypeFromServer(json.get("auth_plugin"));
		return new MySqlSettings(authenticationType);
	}

	/**
	 * Converts PostgresqlSettings from its server representation.
	 *
	 * @param json the server representation, or {@code null} if omitted
	 * @return the settings, or {@code null} if {@code json} is {@code null}
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private PostgresqlSettings postgresqlSettingsFromServer(JsonNode json)
	{
		if (json == null)
			return null;
		boolean mayReplicate = getBoolean(json, "pg_allow_replication");
		return new PostgresqlSettings(mayReplicate);
	}

	/**
	 * Converts KafkaSettingsFromServer from its server representation.
	 *
	 * @param json the server representation, or {@code null} if omitted
	 * @return the settings, or {@code null} if {@code json} is {@code null}
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private KafkaSettings kafkaSettingsFromServer(JsonNode json)
	{
		if (json == null)
			return null;
		String certificate = json.get("access_cert").textValue();
		String key = json.get("access_key").textValue();
		JsonNode settingsNode = json.get("settings");
		Set<KafkaTopicToPermission> permissions = new HashSet<>();
		for (JsonNode element : settingsNode.get("acl"))
			permissions.add(kafkaTopicToPermissionFromServer(element));
		return new KafkaSettings(certificate, key, permissions);
	}

	/**
	 * Converts OpenSearchSettings from its server representation.
	 *
	 * @param json the server representation, or {@code null} if omitted
	 * @return the settings, or {@code null} if {@code json} is {@code null}
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private OpenSearchSettings openSearchSettingsFromServer(JsonNode json)
	{
		if (json == null)
			return null;
		Map<String, OpenSearchPermission> regexToPermission = new HashMap<>();
		for (JsonNode element : json.get("opensearch_acl"))
		{
			String regex = element.get("index").textValue();
			OpenSearchPermission permission = openSearchPermissionFromServer(element.get("permission"));
			OpenSearchPermission oldPermission = regexToPermission.put(regex, permission);
			assert that(oldPermission, "oldPermission").
				withContext(regexToPermission, "regexToPermission").
				withContext(json.toPrettyString(), "json").
				isNull().elseThrow();
		}
		return new OpenSearchSettings(regexToPermission);
	}

	/**
	 * Converts OpenSearchPermission from its server representation.
	 *
	 * @param json the server representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	private static OpenSearchPermission openSearchPermissionFromServer(JsonNode json)
	{
		return OpenSearchPermission.valueOf(json.textValue().toUpperCase(Locale.ROOT));
	}

	/**
	 * Converts MySqlAuthenticationType from its server representation.
	 *
	 * @param json the server representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	private MySqlAuthenticationType mySqlAuthenticationTypeFromServer(JsonNode json)
	{
		return MySqlAuthenticationType.valueOf(json.textValue().toUpperCase(Locale.ROOT));
	}

	/**
	 * Converts UserRole from its server representation.
	 *
	 * @param json the server representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	private UserRole userRoleFromServer(JsonNode json)
	{
		String role = json.textValue();
		if (role.equals("primary"))
			return UserRole.ADMIN;
		return UserRole.valueOf(role.toUpperCase(Locale.ROOT));
	}

	/**
	 * Converts Connection from its server representation.
	 *
	 * @param json the server representation, or {@code null} if omitted
	 * @return the connection details, or {@code null} if {@code json} is {@code null}
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private Connection connectionFromServer(JsonNode json)
	{
		if (json == null)
			return null;
		URI uri = URI.create(json.get("uri").textValue());
		String databaseName = json.get("database").textValue();
		String hostname = json.get("host").textValue();
		int port = getInt(json, "port");
		String username = json.get("user").textValue();
		String password = json.get("password").textValue();
		boolean ssl = getBoolean(json, "ssl");
		return new Connection(uri, databaseName, hostname, port, username, password, ssl);
	}

	/**
	 * Converts OpenSearchDashboard from its server representation.
	 *
	 * @param json the server representation, or {@code null} if omitted
	 * @return the connection details, or {@code null} if {@code json} is {@code null}
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private OpenSearchDashboard openSearchDashboardFromServer(JsonNode json)
	{
		if (json == null)
			return null;
		URI uri = URI.create(json.get("uri").textValue());
		String hostname = json.get("host").textValue();
		int port = getInt(json, "port");
		String username = json.get("user").textValue();
		String password = json.get("password").textValue();
		boolean ssl = getBoolean(json, "ssl");
		return new OpenSearchDashboard(uri, hostname, port, username, password, ssl);
	}

	/**
	 * Converts Status from its server representation.
	 *
	 * @param json the server representation
	 * @return the status
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private Status statusFromServer(JsonNode json)
	{
		return Status.valueOf(json.textValue().toUpperCase(Locale.ROOT));
	}

	/**
	 * Converts Database.MaintenanceSchedule from its server representation.
	 *
	 * @param json the server representation
	 * @return the maintenance schedule
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public MaintenanceSchedule maintenanceScheduleFromServer(JsonNode json)
	{
		// The server returns a value with a minute, second, nano component, but they are zeroed out by the
		// DigitalOcean web interface.
		OffsetTime startTime = LocalTime.parse(json.get("hour").textValue(), Strings.HOUR_MINUTE_SECOND).
			atOffset(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
		DayOfWeek day = DayOfWeek.valueOf(json.get("day").textValue().toUpperCase(Locale.ROOT));
		boolean pending = getBoolean(json, "pending");
		List<String> descriptions;
		try
		{
			descriptions = toList(json, "description", JsonNode::textValue);
		}
		catch (IOException | InterruptedException e)
		{
			// JsonNode::textValue never throws any exceptions
			throw new AssertionError(e);
		}
		return new MaintenanceSchedule(startTime, day, pending, descriptions);
	}

	/**
	 * Converts DatabaseCreator.MaintenanceSchedule to its server representation.
	 *
	 * @param client the client configuration
	 * @param value  the MaintenanceSchedule
	 * @return the server representation
	 * @throws NullPointerException if any of the arguments are null
	 */
	public JsonNode maintenanceScheduleToServer(DefaultDatabaseClient client,
		DatabaseCreator.MaintenanceSchedule value)
	{
		ObjectNode json = client.getJsonMapper().createObjectNode();
		OffsetTime hourAtUtc = value.hour().withOffsetSameInstant(ZoneOffset.UTC);
		json.put("hour", Strings.HOUR_MINUTE.format(hourAtUtc));
		json.put("day", value.day().name().toLowerCase(Locale.ROOT));
		return json;
	}

	/**
	 * Converts Database.MaintenanceSchedule to a DatabaseCreator.MaintenanceSchedule.
	 *
	 * @param value the Database.MaintenanceSchedule
	 * @return the DatabaseCreator.MaintenanceSchedule
	 * @throws NullPointerException if {@code value} is null
	 */
	public DatabaseCreator.MaintenanceSchedule maintenanceScheduleToCreator(
		Database.MaintenanceSchedule value)
	{
		return new DatabaseCreator.MaintenanceSchedule(value.hour(), value.day());
	}

	/**
	 * Converts Endpoint from its server representation.
	 *
	 * @param json the server representation
	 * @return the Endpoint
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public Endpoint endpointFromServer(JsonNode json)
	{
		String hostname = json.get("host").textValue();
		int port = getInt(json, "port");
		return new Endpoint(hostname, port);
	}

	/**
	 * Converts KafkaTopicToPermission from its server representation.
	 *
	 * @param json the server representation, or {@code null} if omitted
	 * @return the settings, or {@code null} if {@code json} is {@code null}
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private KafkaTopicToPermission kafkaTopicToPermissionFromServer(JsonNode json)
	{
		if (json == null)
			return null;
		String id = json.get("id").textValue();
		String topics = json.get("topic").textValue();
		KafkaPermission permission = kafkaPermissionFromServer(json.get("permission"));
		return new KafkaTopicToPermission(id, topics, permission);
	}

	/**
	 * Converts KafkaPermission from its server representation.
	 *
	 * @param json the server representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	private static KafkaPermission kafkaPermissionFromServer(JsonNode json)
	{
		return KafkaPermission.valueOf(json.textValue().toUpperCase(Locale.ROOT));
	}

	/**
	 * Converts RestoreFrom to its server representation.
	 *
	 * @param value the FirewallRule
	 * @return the server representation
	 * @throws NullPointerException if {@code value} is null
	 */
	public JsonNode restoreFromToServer(RestoreFrom value)
	{
		ObjectNode json = getClient().getJsonMapper().createObjectNode();
		json.put("database_name", value.databaseName());
		json.put("backup_created_at", value.createdAt().toString());
		return json;
	}
}