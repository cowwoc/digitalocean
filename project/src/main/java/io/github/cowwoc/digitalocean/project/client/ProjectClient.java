package io.github.cowwoc.digitalocean.project.client;

import io.github.cowwoc.digitalocean.core.client.Client;
import io.github.cowwoc.digitalocean.core.id.ProjectId;
import io.github.cowwoc.digitalocean.project.internal.client.DefaultProjectClient;
import io.github.cowwoc.digitalocean.project.resource.Project;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

/**
 * A DigitalOcean project client.
 */
public interface ProjectClient extends Client
{
	/**
	 * Returns a client.
	 *
	 * @return the client
	 */
	static ProjectClient build()
	{
		return new DefaultProjectClient();
	}

	/**
	 * Returns all the projects.
	 *
	 * @return an empty list if no match is found
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	List<Project> getProjects() throws IOException, InterruptedException;

	/**
	 * Returns the projects that match a predicate.
	 *
	 * @param predicate the predicate
	 * @return an empty list if no match is found
	 * @throws NullPointerException  if {@code predicate} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	List<Project> getProjects(Predicate<Project> predicate) throws IOException, InterruptedException;

	/**
	 * Looks up a project by its ID.
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
	Project getProject(ProjectId id) throws IOException, InterruptedException;

	/**
	 * Returns the first project that matches a predicate.
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
	Project getProject(Predicate<Project> predicate) throws IOException, InterruptedException;

	/**
	 * Returns the default project.
	 *
	 * @return null if no match is found
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	Project getDefaultProject() throws IOException, InterruptedException;
}