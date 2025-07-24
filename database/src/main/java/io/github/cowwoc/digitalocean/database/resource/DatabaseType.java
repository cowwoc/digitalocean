package io.github.cowwoc.digitalocean.database.resource;

import io.github.cowwoc.digitalocean.core.id.DatabaseTypeId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

/**
 * Database <a href="https://slugs.do-api.dev/">types</a>.
 *
 * @param id                         this type's ID
 * @param regionIds                  the regions that are available for this database type
 * @param versions                   the versions that are available for this database type
 * @param nodeCountToDropletTypes    the number and types of droplets supported by this database type. For
 *                                   each number of nodes (key), it shows the types of droplets that can be
 *                                   used.
 * @param versionToEndOfLife         a map from each version to the day that the version will no longer be
 *                                   supported
 * @param versionToEndOfAvailability a map from each version to the day that the version will no longer be
 *                                   available for creating new clusters
 */
public record DatabaseType(DatabaseTypeId id, Set<RegionId> regionIds, Set<String> versions,
                           Map<Integer, Set<DatabaseDropletType>> nodeCountToDropletTypes,
                           Map<String, Instant> versionToEndOfLife,
                           Map<String, Instant> versionToEndOfAvailability)
{
	/**
	 * Creates a new DatabaseType.
	 *
	 * @param id                         this type's ID
	 * @param regionIds                  the regions that are available for this database type
	 * @param versions                   the versions that are available for this database type
	 * @param nodeCountToDropletTypes    the number and types of droplets supported by this database type. For
	 *                                   each number of nodes (key), it shows the types of droplets that can be
	 *                                   used.
	 * @param versionToEndOfLife         a map from each version to the day that the version will no longer be
	 *                                   supported
	 * @param versionToEndOfAvailability a map from each version to the day that the version will no longer be
	 *                                   available for creating new clusters
	 * @throws NullPointerException if any of the arguments are null
	 */
	public DatabaseType(DatabaseTypeId id, Set<RegionId> regionIds, Set<String> versions,
		Map<Integer, Set<DatabaseDropletType>> nodeCountToDropletTypes, Map<String, Instant> versionToEndOfLife,
		Map<String, Instant> versionToEndOfAvailability)
	{
		requireThat(id, "id").isNotNull();
		requireThat(regionIds, "regionIds").isNotNull();
		requireThat(versions, "versions").isNotNull();
		requireThat(nodeCountToDropletTypes, "nodeCountToDropletTypes").isNotNull();
		requireThat(versionToEndOfLife, "versionToEndOfLife").isNotNull();
		requireThat(versionToEndOfAvailability, "versionToEndOfAvailability").isNotNull();

		this.id = id;
		this.regionIds = Set.copyOf(regionIds);
		this.versions = Set.copyOf(versions);
		this.nodeCountToDropletTypes = Map.copyOf(nodeCountToDropletTypes);
		this.versionToEndOfLife = Map.copyOf(versionToEndOfLife);
		this.versionToEndOfAvailability = Map.copyOf(versionToEndOfAvailability);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DatabaseType.class).
			add("id", id).
			add("regions", regionIds).
			add("versions", versions).
			add("nodeCountToDropletTypes", nodeCountToDropletTypes).
			add("versionToEndOfLife", versionToEndOfLife).
			add("versionToEndOfAvailability", versionToEndOfAvailability).
			toString();
	}
}