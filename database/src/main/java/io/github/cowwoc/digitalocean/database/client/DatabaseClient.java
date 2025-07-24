package io.github.cowwoc.digitalocean.database.client;

import io.github.cowwoc.digitalocean.core.client.Client;
import io.github.cowwoc.digitalocean.core.id.DatabaseDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.DatabaseId;
import io.github.cowwoc.digitalocean.core.id.DatabaseTypeId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.database.internal.client.DefaultDatabaseClient;
import io.github.cowwoc.digitalocean.database.resource.Database;
import io.github.cowwoc.digitalocean.database.resource.DatabaseCreator;
import io.github.cowwoc.digitalocean.database.resource.DatabaseType;
import io.github.cowwoc.requirements12.annotation.CheckReturnValue;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

/**
 * A DigitalOcean database client.
 */
public interface DatabaseClient extends Client
{
	/**
	 * Returns a client.
	 *
	 * @return the client
	 */
	static DatabaseClient build()
	{
		return new DefaultDatabaseClient();
	}

	/**
	 * Looks up a database type.
	 *
	 * @param typeId the ID of the type
	 * @return the matching value
	 * @throws IllegalArgumentException if no match is found
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	DatabaseType getType(DatabaseTypeId typeId) throws IOException, InterruptedException;

	/**
	 * Returns the database clusters.
	 *
	 * @return an empty list if no match is found
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	List<Database> getClusters() throws IOException, InterruptedException;

	/**
	 * Returns the databases that matches a predicate.
	 *
	 * @param predicate the predicate
	 * @return null if no match is found
	 * @throws NullPointerException  if {@code predicate} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	List<Database> getClusters(Predicate<Database> predicate) throws IOException, InterruptedException;

	/**
	 * Looks up a database cluster by its ID.
	 *
	 * @param id the ID
	 * @return null if no match is found
	 * @throws NullPointerException  if {@code id} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	Database getCluster(DatabaseId id) throws IOException, InterruptedException;

	/**
	 * Returns the first database that matches a predicate.
	 *
	 * @param predicate the predicate
	 * @return null if no match is found
	 * @throws NullPointerException  if {@code predicate} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	Database getCluster(Predicate<Database> predicate) throws IOException, InterruptedException;

	/**
	 * Creates a database cluster.
	 *
	 * @param name          the name of the cluster
	 * @param typeId        the type of the database
	 * @param numberOfNodes the number of nodes in the cluster
	 * @param dropletTypeId the machine type of the droplet
	 * @param regionId      the region to create the cluster in. To create a cluster that spans multiple
	 *                      regions, add DatabaseReplica to the cluster.
	 * @return a new database creator
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
	@CheckReturnValue
	DatabaseCreator createCluster(String name, DatabaseTypeId typeId, int numberOfNodes,
		DatabaseDropletTypeId dropletTypeId, RegionId regionId);

	/**
	 * Destroys a database cluster. If the cluster does not exist, this method does nothing.
	 *
	 * @param id the ID of the cluster
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	void destroyCluster(DatabaseId id) throws IOException, InterruptedException;
}