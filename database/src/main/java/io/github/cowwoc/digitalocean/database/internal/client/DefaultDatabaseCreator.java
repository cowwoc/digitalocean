package io.github.cowwoc.digitalocean.database.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cowwoc.digitalocean.core.exception.AccessDeniedException;
import io.github.cowwoc.digitalocean.core.exception.PendingDeletionException;
import io.github.cowwoc.digitalocean.core.exception.UnsupportedCombinationException;
import io.github.cowwoc.digitalocean.core.id.DatabaseDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.DatabaseTypeId;
import io.github.cowwoc.digitalocean.core.id.ProjectId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.id.VpcId;
import io.github.cowwoc.digitalocean.core.internal.parser.CoreParser;
import io.github.cowwoc.digitalocean.core.internal.util.ParameterValidator;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;
import io.github.cowwoc.digitalocean.core.util.CreateResult;
import io.github.cowwoc.digitalocean.database.client.DatabaseClient;
import io.github.cowwoc.digitalocean.database.resource.Database;
import io.github.cowwoc.digitalocean.database.resource.DatabaseCreator;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static io.github.cowwoc.digitalocean.core.internal.client.AbstractInternalClient.REST_SERVER;
import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.PRECONDITION_FAILED_412;
import static org.eclipse.jetty.http.HttpStatus.UNAUTHORIZED_401;
import static org.eclipse.jetty.http.HttpStatus.UNPROCESSABLE_ENTITY_422;

public final class DefaultDatabaseCreator implements DatabaseCreator
{
	private final DefaultDatabaseClient client;
	private final String name;
	private final DatabaseTypeId databaseTypeId;
	private final int numberOfNodes;
	private final DatabaseDropletTypeId dropletTypeId;
	private final RegionId regionId;
	private String version = "";
	private VpcId vpcId;
	private final Set<String> tags = new HashSet<>();
	private ProjectId projectId;
	private final Set<FirewallRuleBuilder> firewallRules = new HashSet<>();
	private int additionalStorageInMiB = -1;
	private RestoreFrom restoreFrom;

	/**
	 * Creates a DefaultDatabaseCreator.
	 *
	 * @param client         the client configuration
	 * @param name           the name of the cluster
	 * @param databaseTypeId the type of the database
	 * @param numberOfNodes  the number of nodes in the cluster
	 * @param dropletTypeId  the machine type of the droplet
	 * @param regionId       the region to create the cluster in
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code name} contains characters other than lowercase letters
	 *                                    ({@code a-z}), digits ({@code 0-9}), and dashes ({@code -}).</li>
	 *                                    <li>{@code name} begins or ends with a character other than a letter
	 *                                    or digit.</li>
	 *                                    <li>{@code name} is shorter than 3 characters or longer than 63
	 *                                    characters.</li>
	 *                                    <li>{@code numberOfNodes} is less than 1 or greater than 3.</li>
	 *                                  </ul>
	 * @throws IllegalStateException    if the client is closed
	 */
	public DefaultDatabaseCreator(DatabaseClient client, String name, DatabaseTypeId databaseTypeId,
		int numberOfNodes, DatabaseDropletTypeId dropletTypeId, RegionId regionId)
	{
		requireThat(client, "client").isNotNull();
		ParameterValidator.validateName(name, "name");
		requireThat(databaseTypeId, "databaseTypeId").isNotNull();
		requireThat(numberOfNodes, "numberOfNodes").isBetween(1, true, 3, true);
		requireThat(dropletTypeId, "dropletTypeId").isNotNull();
		requireThat(regionId, "regionId").isNotNull();
		this.client = (DefaultDatabaseClient) client;
		this.name = name;
		this.databaseTypeId = databaseTypeId;
		this.numberOfNodes = numberOfNodes;
		this.dropletTypeId = dropletTypeId;
		this.regionId = regionId;
	}

	@Override
	public DatabaseCreator version(String version)
	{
		requireThat(version, "version").isStripped();
		this.version = version;
		return this;
	}

	@Override
	public DatabaseCreator vpcId(VpcId vpcId)
	{
		this.vpcId = vpcId;
		return this;
	}

	@Override
	public DatabaseCreator tag(String tag)
	{
		// Discovered empirically: DigitalOcean drops all tags silently if any of them contain invalid characters.
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Databases/operation/tags_create
		requireThat(tag, "tag").matches("^[a-zA-Z0-9_\\-:]+$").length().isLessThanOrEqualTo(255);
		this.tags.add(tag);
		return this;
	}

	@Override
	public DatabaseCreator tags(Collection<String> tags)
	{
		requireThat(tags, "tags").isNotNull().doesNotContain(null);
		for (String tag : tags)
		{
			requireThat(tag, "tag").withContext(tags, "tags").matches("^[a-zA-Z0-9_\\-:]+$").
				length().isLessThanOrEqualTo(255);
			this.tags.add(tag);
		}
		return this;
	}

	@Override
	public DatabaseCreator projectId(ProjectId projectId)
	{
		requireThat(projectId, "projectId").isNotNull();
		this.projectId = projectId;
		return this;
	}

	@Override
	public DatabaseCreator firewallRule(FirewallRuleBuilder firewallRule)
	{
		requireThat(firewallRule, "firewallRule").isNotNull();
		this.firewallRules.add(firewallRule);
		return this;
	}

	@Override
	public DatabaseCreator firewallRules(Collection<FirewallRuleBuilder> firewallRules)
	{
		requireThat(firewallRules, "firewallRules").isNotNull().doesNotContain(null);
		this.firewallRules.addAll(firewallRules);
		return this;
	}

	@Override
	public void additionalStorageInMiB(int additionalStorageInMiB)
	{
		requireThat(additionalStorageInMiB, "additionalStorageInMiB").isNotNegative();
		this.additionalStorageInMiB = additionalStorageInMiB;
	}

	@Override
	public DatabaseCreator restoreFrom(RestoreFrom restoreFrom)
	{
		this.restoreFrom = restoreFrom;
		return this;
	}

	@Override
	public CreateResult<Database> apply() throws IOException, InterruptedException, UnsupportedCombinationException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Databases/operation/databases_create_cluster
		JsonMapper jm = client.getJsonMapper();
		DatabaseParser databaseParser = client.getDatabaseParser();
		CoreParser coreParser = client.getCoreParser();
		ObjectNode requestBody = jm.createObjectNode().
			put("name", name).
			put("engine", databaseParser.databaseTypeIdToServer(databaseTypeId)).
			put("num_nodes", numberOfNodes).
			put("size", dropletTypeId.toString()).
			put("region", coreParser.regionIdToServer(regionId));
		if (!version.isEmpty())
			requestBody.put("version", version);
		if (vpcId != null)
			requestBody.put("private_network_uuid", vpcId.getValue());
		if (!tags.isEmpty())
		{
			ArrayNode tagsNode = requestBody.putArray("tags");
			for (String tag : tags)
				tagsNode.add(tag);
		}
		if (projectId != null)
			requestBody.put("project_id", projectId.getValue());
		if (!firewallRules.isEmpty())
		{
			ArrayNode firewallRulesNode = requestBody.putArray("rules");
			for (FirewallRuleBuilder firewallRuleBuilder : firewallRules)
				firewallRulesNode.add(databaseParser.firewallRuleToServer(firewallRuleBuilder));
		}
		if (additionalStorageInMiB != -1)
			requestBody.put("storage_size_mib", additionalStorageInMiB);
		if (restoreFrom != null)
			requestBody.set("backup_restore", databaseParser.restoreFromToServer(restoreFrom));

		Request request = client.createRequest(REST_SERVER.resolve("v2/databases"), requestBody).
			method(POST);
		Response serverResponse = client.send(request);
		return switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				ContentResponse contentResponse = (ContentResponse) serverResponse;
				JsonNode body = client.getResponseBody(contentResponse);
				yield CreateResult.created(databaseParser.databaseFromServer(body.get("database")));
			}
			case UNAUTHORIZED_401 ->
			{
				ContentResponse contentResponse = (ContentResponse) serverResponse;
				JsonNode json = client.getResponseBody(contentResponse);
				throw new AccessDeniedException(json.get("message").textValue());
			}
			case PRECONDITION_FAILED_412 ->
			{
				// Discovered empirically
				ContentResponse contentResponse = (ContentResponse) serverResponse;
				JsonNode json = client.getResponseBody(contentResponse);
				String message = json.get("message").textValue();
				if (message.equals("At this time, we're unable to create a cluster of that size in that region"))
					throw new UnsupportedCombinationException(message);
				throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
					"Request: " + client.toString(request));
			}
			case UNPROCESSABLE_ENTITY_422 ->
			{
				ContentResponse contentResponse = (ContentResponse) serverResponse;
				JsonNode json = client.getResponseBody(contentResponse);
				String message = json.get("message").textValue();
				switch (message)
				{
					case "invalid size" -> throw new IllegalArgumentException("dropletType may not be " +
						dropletTypeId);
					case "cluster name is not available" ->
					{
						Database conflict = client.getCluster(cluster -> cluster.getName().equals(name));
						if (conflict != null)
							yield CreateResult.conflictedWith(conflict);
						throw new PendingDeletionException("A cluster with the same name is pending deletion. Its name " +
							"cannot be reused until the operation completes, nor can the existing instance be returned.");
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
		return new ToStringBuilder(DefaultDatabaseCreator.class).
			add("name", name).
			add("databaseTypeId", databaseTypeId).
			add("version", version).
			add("numberOfNodes", numberOfNodes).
			add("dropletType", dropletTypeId).
			add("region", regionId).
			add("vpc", vpcId).
			add("tags", tags).
			add("projectId", projectId).
			add("firewallRules", firewallRules).
			add("additionalStorageInMiB", additionalStorageInMiB).
			add("restoreFrom", restoreFrom).
			toString();
	}
}