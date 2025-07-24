package io.github.cowwoc.digitalocean.project.resource;

import io.github.cowwoc.digitalocean.core.id.ProjectId;

import java.io.IOException;
import java.time.Instant;

/**
 * A group of resources.
 */
public interface Project
{
	/**
	 * Returns the project's ID.
	 *
	 * @return the ID
	 */
	ProjectId getId();

	/**
	 * Returns the name of the project.
	 *
	 * @return the name
	 */
	String getName();

	/**
	 * Returns the UUID of the project owner.
	 *
	 * @return the UUID
	 */
	String getOwnerUuid();

	/**
	 * Returns the ID of the project owner.
	 *
	 * @return the ID
	 */
	int getOwnerId();

	/**
	 * Returns a description of the project.
	 *
	 * @return the description
	 */
	String getDescription();

	/**
	 * Returns the purpose of the project.
	 *
	 * @return the purpose
	 */
	String getPurpose();

	/**
	 * Returns the project's environment.
	 *
	 * @return the environment
	 */
	Environment getEnvironment();

	/**
	 * Determines if this is the default project.
	 *
	 * @return {@code true} if this is the default project
	 */
	boolean isDefault();

	/**
	 * Returns the time the project was created.
	 *
	 * @return the time
	 */
	Instant getCreatedAt();

	/**
	 * Returns the time the project was last updated.
	 *
	 * @return the time
	 */
	Instant getUpdatedAt();

	/**
	 * Destroys the project. If the project does not exist, this method does nothing.
	 *
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	void destroy() throws IOException, InterruptedException;

	/**
	 * A project's deployment environment.
	 */
	enum Environment
	{
		/**
		 * The development environment.
		 */
		DEVELOPMENT,
		/**
		 * The staging environment.
		 */
		STAGING,
		/**
		 * The production environment.
		 */
		PRODUCTION
	}
}