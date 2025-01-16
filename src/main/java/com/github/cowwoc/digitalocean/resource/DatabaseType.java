package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.internal.util.JsonToObject;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Database <a href="https://slugs.do-api.dev/">types</a>.
 */
public enum DatabaseType
{
	/**
	 * <a href="https://www.postgresql.org/">PostgreSQL</a>.
	 */
	POSTGRESQL("pg"),
	/**
	 * <a href="https://www.mysql.com/">MySQL</a>.
	 */
	MYSQL("mysql"),
	/**
	 * <a href="https://redis.io/">Redis</a>.
	 */
	REDIS("redis"),
	/**
	 * <a href="https://www.mongodb.com/">MongoDB</a>.
	 */
	MONGODB("mongodb"),
	/**
	 * <a href="https://kafka.apache.org/">Kafka</a>.
	 */
	KAFKA("kafka"),
	/**
	 * <a href="https://opensearch.org/">OpenSearch</a>.
	 */
	OPENSEARCH("opensearch");

	private final String slug;

	/**
	 * @param slug the slug associated with this type
	 * @throws NullPointerException if {@code slug} is null
	 */
	DatabaseType(String slug)
	{
		requireThat(slug, "slug").isNotNull();
		this.slug = slug;
	}

	/**
	 * Looks up the database type by its slug.
	 *
	 * @param slug the slug to look up
	 * @return the matching value
	 * @throws IllegalArgumentException if no match is found
	 */
	public static DatabaseType getBySlug(String slug)
	{
		for (DatabaseType type : values())
			if (type.slug.equals(slug))
				return type;
		throw new IllegalArgumentException("Slug not found: " + slug);
	}

	/**
	 * Returns the slug of the type.
	 *
	 * @return the slug
	 */
	public String getSlug()
	{
		return slug;
	}

	/**
	 * Returns the zones that are available for this database type.
	 *
	 * @param client the client configuration
	 * @return an empty set if no zones are available
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Set<Zone.Id> getZones(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		return getOptions(client, database ->
			client.getElements(database, "regions", zone -> Zone.id(zone.textValue())));
	}

	/**
	 * Returns the options that are available for this database type.
	 *
	 * @param <T>    the type that the options will be converted to
	 * @param client the client configuration
	 * @param mapper converts the database options from JSON to a set of Java objects
	 * @return an empty set if no matches are found
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	private <T> T getOptions(DigitalOceanClient client, JsonToObject<T> mapper)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_list_options
		URI uri = REST_SERVER.resolve("v2/databases/options");
		return client.getResource(uri, body ->
		{
			JsonNode optionsNode = body.get("options");
			JsonNode databaseNode = optionsNode.get(getSlug());
			return mapper.map(databaseNode);
		});
	}

	/**
	 * Returns the versions that are available for this database type.
	 *
	 * @param client the client configuration
	 * @return an empty set if no versions are available
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Set<String> getVersions(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		return getOptions(client, database ->
			client.getElements(database, "versions", JsonNode::textValue));
	}

	/**
	 * Returns the number and types of droplets supported by this database type. For each number of nodes (key),
	 * it shows the types of droplets that can be used.
	 *
	 * @param client the client configuration
	 * @return an empty map if no versions are available
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Map<Integer, Set<DropletType.Id>> getNodeCountToDropletTypes(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		return getOptions(client, database ->
		{
			JsonNode layoutsNode = database.get("layouts");
			Map<Integer, Set<DropletType.Id>> nodeCountToDropletTypes = new HashMap<>();
			for (JsonNode layout : layoutsNode)
			{
				int nodeCount = client.getInt(layout, "num_nodes");
				JsonNode dropletTypesNode = layout.get("sizes");
				Set<DropletType.Id> dropletTypes = new HashSet<>();
				for (JsonNode node : dropletTypesNode)
					dropletTypes.add(DropletType.id(node.textValue()));
				nodeCountToDropletTypes.put(nodeCount, dropletTypes);
			}
			return nodeCountToDropletTypes;
		});
	}

	/**
	 * Returns a map from each version to the day that the version will no longer be supported.
	 *
	 * @param client the client configuration
	 * @return an empty set if no versions are available
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Map<String, Instant> getVersionToEndOfLife(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		return getVersionAvailability(client, versions ->
		{
			Map<String, Instant> versionToEndOfLife = new HashMap<>();
			for (JsonNode node : versions)
			{
				String version = node.get("version").textValue();
				Instant endOfLife = Instant.parse(node.get("end_of_life").textValue());
				versionToEndOfLife.put(version, endOfLife);
			}
			return versionToEndOfLife;
		});
	}

	/**
	 * Returns the options that are available for this database type.
	 *
	 * @param <K>    the type of keys in the map
	 * @param <V>    the type of values in the map
	 * @param client the client configuration
	 * @param mapper converts the database options from JSON to a set of Java objects
	 * @return an empty map if no matches are found
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	private <K, V> Map<K, V> getVersionAvailability(DigitalOceanClient client, JsonToObject<Map<K, V>> mapper)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/databases_list_options
		URI uri = REST_SERVER.resolve("v2/databases/options");
		return client.getResource(uri, body ->
		{
			JsonNode optionsNode = body.get("version_availability");
			JsonNode databaseNode = optionsNode.get(getSlug());
			return mapper.map(databaseNode);
		});
	}

	/**
	 * Returns a map from each version to the day that the version will no longer be available for creating new
	 * clusters.
	 *
	 * @param client the client configuration
	 * @return an empty map if no versions are available
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Map<String, Instant> getVersionToEndOfAvailability(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		return getVersionAvailability(client, versions ->
		{
			Map<String, Instant> versionToEndOfAvailability = new HashMap<>();
			for (JsonNode node : versions)
			{
				String version = node.get("version").textValue();
				Instant endOfAvailability = Instant.parse(node.get("end_of_availability").textValue());
				versionToEndOfAvailability.put(version, endOfAvailability);
			}
			return versionToEndOfAvailability;
		});
	}

	@Override
	public String toString()
	{
		return slug;
	}
}