package io.github.cowwoc.digitalocean.test;

import io.github.cowwoc.digitalocean.core.exception.UnsupportedCombinationException;
import io.github.cowwoc.digitalocean.core.id.DatabaseId;
import io.github.cowwoc.digitalocean.core.id.DatabaseTypeId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.util.CreateResult;
import io.github.cowwoc.digitalocean.database.client.DatabaseClient;
import io.github.cowwoc.digitalocean.database.resource.Database;
import io.github.cowwoc.digitalocean.database.resource.DatabaseDropletType;
import io.github.cowwoc.digitalocean.database.resource.DatabaseType;
import io.github.cowwoc.digitalocean.test.util.Tests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public final class DatabaseIT extends AbstractIT
{
	private final Logger log = LoggerFactory.getLogger(DatabaseIT.class);

	/**
	 * Creates a new DatabaseIT.
	 *
	 * @throws IOException if an I/O error occurs while loading the test configuration
	 */
	public DatabaseIT() throws IOException
	{
	}

	@Test
	public void createDatabase() throws IOException, InterruptedException
	{
		try (DatabaseClient client = DatabaseClient.build())
		{
			login(client);

			CreateResult<Database> database = createDatabase(client, Tests.getCallerName(),
				DatabaseTypeId.POSTGRESQL);
			try
			{
				assert database.created() : database;
			}
			finally
			{
				database.getResource().destroy();
			}
		}
	}

	@AfterSuite
	public void destroyDatabase() throws IOException, InterruptedException
	{
		try (DatabaseClient client = DatabaseClient.build())
		{
			login(client);

			for (Database database : client.getClusters())
				database.destroy();
		}
	}

	@Test
	public void deleteMissingDatabase() throws IOException, InterruptedException
	{
		try (DatabaseClient client = DatabaseClient.build())
		{
			login(client);

			client.destroyCluster(DatabaseId.of("missing-database"));
		}
	}

	@Test
	public void createExistingDatabase() throws IOException, InterruptedException
	{
		try (DatabaseClient client = DatabaseClient.build())
		{
			login(client);
			String name = Tests.getCallerName();
			DatabaseTypeId typeId = DatabaseTypeId.POSTGRESQL;

			CreateResult<Database> database1 = createDatabase(client, name, typeId);
			try
			{
				assert database1.created() : database1;
				CreateResult<Database> database2 = createDatabase(client, name, typeId);
				try
				{
					assert database2.conflicted() : database2;
					assert database2.getResource().getId().equals(database1.getResource().getId()) : "database1: " +
						database1 + ", database2: " + database2;
				}
				finally
				{
					database2.getResource().destroy();
				}
			}
			finally
			{
				database1.getResource().destroy();
			}
		}
	}

	/**
	 * Creates a database.
	 *
	 * @param client the client configuration
	 * @param name   the database name
	 * @param typeId the desired database type
	 * @return the result of the database operation
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	private CreateResult<Database> createDatabase(DatabaseClient client, String name, DatabaseTypeId typeId)
		throws IOException, InterruptedException
	{
		DatabaseType type = client.getType(typeId);
		for (Entry<Integer, Set<DatabaseDropletType>> nodeCountToDropletTypes : type.nodeCountToDropletTypes().
			entrySet())
		{
			int numberOfNodes = nodeCountToDropletTypes.getKey();
			for (DatabaseDropletType dropletType : sortByPrice(nodeCountToDropletTypes.getValue()))
			{
				for (RegionId regionId : getCommonRegions(type.regionIds(), dropletType.getRegionIds()))
				{
					try
					{
						return client.createCluster(name, typeId, numberOfNodes, dropletType.getId(),
							regionId).apply();
					}
					catch (UnsupportedCombinationException _)
					{
						// Try the next combination
					}
				}
			}
		}
		throw new IOException("All configuration types were rejected");
	}

	/**
	 * @param dropletTypes the available droplet types
	 * @return the droplet types sorted from cheapest to most expensive
	 */
	private static List<DatabaseDropletType> sortByPrice(Set<DatabaseDropletType> dropletTypes)
	{
		return dropletTypes.stream().sorted(Comparator.comparing(DatabaseDropletType::getCpus).
			thenComparing(DatabaseDropletType::getRamInMiB)).toList();
	}
}