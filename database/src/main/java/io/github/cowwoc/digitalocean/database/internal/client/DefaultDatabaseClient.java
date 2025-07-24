package io.github.cowwoc.digitalocean.database.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cowwoc.digitalocean.core.id.DatabaseDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.DatabaseId;
import io.github.cowwoc.digitalocean.core.id.DatabaseTypeId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.internal.client.AbstractInternalClient;
import io.github.cowwoc.digitalocean.core.internal.parser.CoreParser;
import io.github.cowwoc.digitalocean.database.client.DatabaseClient;
import io.github.cowwoc.digitalocean.database.resource.Database;
import io.github.cowwoc.digitalocean.database.resource.DatabaseCreator;
import io.github.cowwoc.digitalocean.database.resource.DatabaseDropletType;
import io.github.cowwoc.digitalocean.database.resource.DatabaseType;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class DefaultDatabaseClient extends AbstractInternalClient
	implements DatabaseClient
{
	private final DatabaseParser databaseParser = new DatabaseParser(this);
	private final CoreParser coreParser = new CoreParser(this);

	/**
	 * Creates a new DefaultDatabaseClient.
	 */
	public DefaultDatabaseClient()
	{
	}

	/**
	 * @return a {@code DatabaseParser}
	 */
	public DatabaseParser getDatabaseParser()
	{
		return databaseParser;
	}

	/**
	 * @return a {@code CoreParser}
	 */
	public CoreParser getCoreParser()
	{
		return coreParser;
	}

	@Override
	public DatabaseType getType(DatabaseTypeId typeId) throws IOException, InterruptedException
	{
		String typeIdToServer = databaseParser.databaseTypeIdToServer(typeId);

		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Databases/operation/databases_list_options
		URI uri = REST_SERVER.resolve("v2/databases/options");
		JsonNode response = getResource(uri, body -> body);
		JsonNode options = response.get("options").get(typeIdToServer);

		Set<RegionId> regionIds = databaseParser.getElements(options, "regions",
			coreParser::regionIdFromServer);
		Set<String> versions = databaseParser.getElements(options, "versions", JsonNode::textValue);

		JsonNode layoutsNode = options.get("layouts");
		Map<Integer, Set<DatabaseDropletType>> nodeCountToDropletTypes = new HashMap<>();
		for (JsonNode layout : layoutsNode)
		{
			int nodeCount = databaseParser.getInt(layout, "num_nodes");
			JsonNode dropletTypesNode = layout.get("sizes");
			Set<DatabaseDropletType> dropletTypes = new HashSet<>();
			for (JsonNode node : dropletTypesNode)
			{
				DatabaseDropletTypeId dropletTypeId = DatabaseDropletTypeId.of(node.textValue());
				dropletTypes.add(new DefaultDatabaseDropletType(dropletTypeId, regionIds));
			}
			nodeCountToDropletTypes.put(nodeCount, dropletTypes);
		}

		JsonNode versionAvailability = response.get("version_availability").get(typeIdToServer);
		Map<String, Instant> versionToEndOfLife = new HashMap<>();
		Map<String, Instant> versionToEndOfAvailability = new HashMap<>();
		for (JsonNode node : versionAvailability)
		{
			String version = node.get("version").textValue();
			Instant endOfLife = Instant.parse(node.get("end_of_life").textValue());
			versionToEndOfLife.put(version, endOfLife);

			Instant endOfAvailability = Instant.parse(node.get("end_of_availability").textValue());
			versionToEndOfAvailability.put(version, endOfAvailability);
		}

		return new DatabaseType(typeId, regionIds, versions, nodeCountToDropletTypes, versionToEndOfLife,
			versionToEndOfAvailability);
	}

	@Override
	public List<Database> getClusters() throws IOException, InterruptedException
	{
		return getClusters(_ -> true);
	}

	@Override
	public List<Database> getClusters(Predicate<Database> predicate)
		throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Databases/operation/databases_list_clusters
		return getElements(REST_SERVER.resolve("v2/databases"), Map.of(), body ->
		{
			List<Database> databases = new ArrayList<>();
			for (JsonNode database : body.get("databases"))
			{
				Database candidate = databaseParser.databaseFromServer(database);
				if (predicate.test(candidate))
					databases.add(candidate);
			}
			return databases;
		});
	}

	@Override
	public Database getCluster(Predicate<Database> predicate) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Databases/operation/databases_list_clusters
		return getElement(REST_SERVER.resolve("v2/databases"), Map.of(), body ->
		{
			for (JsonNode database : body.get("databases"))
			{
				Database candidate = databaseParser.databaseFromServer(database);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	@Override
	public Database getCluster(DatabaseId id) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Databases/operation/databases_get_cluster
		return getResource(REST_SERVER.resolve("v2/databases/" + id), body ->
		{
			JsonNode database = body.get("database");
			return databaseParser.databaseFromServer(database);
		});
	}

	@Override
	public DatabaseCreator createCluster(String name, DatabaseTypeId typeId, int numberOfNodes,
		DatabaseDropletTypeId dropletTypeId, RegionId regionId)
	{
		return new DefaultDatabaseCreator(this, name, typeId, numberOfNodes, dropletTypeId, regionId);
	}

	@Override
	public void destroyCluster(DatabaseId id) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Databases/operation/databases_destroy_cluster
		destroyResource(REST_SERVER.resolve("v2/databases/" + id));
	}
}