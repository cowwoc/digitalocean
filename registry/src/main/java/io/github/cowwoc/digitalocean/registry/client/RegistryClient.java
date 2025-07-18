package io.github.cowwoc.digitalocean.registry.client;

import io.github.cowwoc.digitalocean.core.client.Client;
import io.github.cowwoc.digitalocean.core.exception.AccessDeniedException;
import io.github.cowwoc.digitalocean.registry.internal.client.DefaultRegistryClient;
import io.github.cowwoc.digitalocean.registry.resource.Registry;
import io.github.cowwoc.digitalocean.registry.resource.Repository;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

/**
 * A DigitalOcean's container registry client.
 */
public interface RegistryClient extends Client
{
	/**
	 * Returns a client.
	 *
	 * @return the client
	 * @throws IOException if an I/O error occurs while building the client
	 */
	static RegistryClient build() throws IOException
	{
		return new DefaultRegistryClient();
	}

	/**
	 * Returns the account's container registry.
	 *
	 * @return null if the account does not have a container registry
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 * @throws AccessDeniedException if the client does not have sufficient privileges to execute this request
	 */
	Registry getRegistry() throws IOException, InterruptedException;

	/**
	 * Returns all the repositories in this registry.
	 *
	 * @param registry the container registry
	 * @return an empty list if no match is found
	 * @throws NullPointerException  if {@code registry} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	List<Repository> getRepositories(Registry registry) throws IOException, InterruptedException;

	/**
	 * Returns the repositories that match a predicate.
	 *
	 * @param registry  the container registry
	 * @param predicate the predicate
	 * @return an empty list if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	List<Repository> getRepositories(Registry registry, Predicate<Repository> predicate)
		throws IOException, InterruptedException;

	/**
	 * Returns the first repository that matches a predicate.
	 *
	 * @param registry  the container registry
	 * @param predicate the predicate
	 * @return null if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	Repository getRepository(Registry registry, Predicate<Repository> predicate)
		throws IOException, InterruptedException;

	/**
	 * Looks up a repository by its name.
	 *
	 * @param registry the container registry
	 * @param name     the name
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	Repository getRepository(Registry registry, String name) throws IOException, InterruptedException;
}