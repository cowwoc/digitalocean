package io.github.cowwoc.digitalocean.database.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cowwoc.digitalocean.compute.resource.ComputeRegion;
import io.github.cowwoc.digitalocean.compute.resource.DropletType;
import io.github.cowwoc.digitalocean.core.internal.client.AbstractInternalClient;
import io.github.cowwoc.digitalocean.database.client.DatabaseClient;
import io.github.cowwoc.digitalocean.database.resource.Database;
import io.github.cowwoc.digitalocean.database.resource.Database.Id;
import io.github.cowwoc.digitalocean.database.resource.DatabaseCreator;
import io.github.cowwoc.digitalocean.database.resource.DatabaseType;
import io.github.cowwoc.digitalocean.network.internal.resource.NetworkParser;
import io.github.cowwoc.digitalocean.network.resource.Region;

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
	private final NetworkParser networkParser = new NetworkParser(this);

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
	 * @return a {@code NetworkParser}
	 */
	public NetworkParser getNetworkParser()
	{
		return networkParser;
	}

	@Override
	public DatabaseType getDatabaseType(DatabaseType.Id id) throws IOException, InterruptedException
	{
		JsonNode options = getOptions(id);

		Set<ComputeRegion.Id> regions = databaseParser.getElements(options, "regions",
			networkParser::regionIdFromServer);
		Set<String> versions = databaseParser.getElements(options, "versions", JsonNode::textValue);

		JsonNode layoutsNode = options.get("layouts");
		Map<Integer, Set<DropletType.Id>> nodeCountToDropletTypes = new HashMap<>();
		for (JsonNode layout : layoutsNode)
		{
			int nodeCount = databaseParser.getInt(layout, "num_nodes");
			JsonNode dropletTypesNode = layout.get("sizes");
			Set<DropletType.Id> dropletTypes = new HashSet<>();
			for (JsonNode node : dropletTypesNode)
				dropletTypes.add(DropletType.id(node.textValue()));
			nodeCountToDropletTypes.put(nodeCount, dropletTypes);
		}

		Map<String, Instant> versionToEndOfLife = new HashMap<>();
		Map<String, Instant> versionToEndOfAvailability = new HashMap<>();
		JsonNode versionAvailability = options.get("version_availability");
		for (JsonNode node : versionAvailability)
		{
			String version = node.get("version").textValue();
			Instant endOfLife = Instant.parse(node.get("end_of_life").textValue());
			versionToEndOfLife.put(version, endOfLife);

			Instant endOfAvailability = Instant.parse(node.get("end_of_availability").textValue());
			versionToEndOfAvailability.put(version, endOfAvailability);
		}

		return new DatabaseType(id, regions, versions, nodeCountToDropletTypes, versionToEndOfLife,
			versionToEndOfAvailability);
	}

	/**
	 * Returns the options that are available for this database type.
	 *
	 * @param id the database type
	 * @return the options
	 * @throws NullPointerException  if {@code id} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	private JsonNode getOptions(DatabaseType.Id id) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Databases/operation/databases_list_options
		URI uri = REST_SERVER.resolve("v2/databases/options");
		return getResource(uri, body ->
		{
			JsonNode optionsNode = body.get("options");
			return optionsNode.get(databaseParser.databaseTypeIdToServer(id));
		});
	}

	@Override
	public List<Database> getDatabaseClusters() throws IOException, InterruptedException
	{
		return getDatabaseClusters(_ -> true);
	}

	@Override
	public List<Database> getDatabaseClusters(Predicate<Database> predicate)
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
	public Database getDatabaseCluster(Predicate<Database> predicate) throws IOException, InterruptedException
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
	public Database getDatabaseCluster(Id id) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Databases/operation/databases_get_cluster
		return getResource(REST_SERVER.resolve("v2/databases/" + id), body ->
		{
			JsonNode database = body.get("database");
			return databaseParser.databaseFromServer(database);
		});
	}

	@Override
	public DatabaseCreator createDatabaseCluster(String name, DatabaseType databaseType,
		int numberOfStandbyNodes, DropletType.Id dropletType, Region.Id region)
	{
		return new DefaultDatabaseCreator(this, name, databaseType, numberOfStandbyNodes, dropletType, region);
	}
}