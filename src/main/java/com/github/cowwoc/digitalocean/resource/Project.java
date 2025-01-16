package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.id.StringId;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A group of resources.
 */
public final class Project
{
	/**
	 * Returns all the projects.
	 *
	 * @param client the client configuration
	 * @return an empty set if no match is found
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Set<Project> getAll(DigitalOceanClient client)
		throws IOException, InterruptedException, TimeoutException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/projects_list
		return client.getElements(REST_SERVER.resolve("v2/projects"), Map.of(), body ->
		{
			Set<Project> projects = new HashSet<>();
			for (JsonNode projectNode : body.get("projects"))
				projects.add(getByJson(client, projectNode));
			return projects;
		});
	}

	/**
	 * Returns the first project that matches a predicate.
	 *
	 * @param client    the client configuration
	 * @param predicate the predicate
	 * @return null if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Project getByPredicate(DigitalOceanClient client, Predicate<Project> predicate)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/projects_list
		return client.getElement(REST_SERVER.resolve("v2/projects"), Map.of(), body ->
		{
			for (JsonNode projectNode : body.get("projects"))
			{
				Project candidate = getByJson(client, projectNode);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	/**
	 * Returns the default project.
	 *
	 * @param client the client configuration
	 * @return null if no match is found
	 * @throws NullPointerException if {@code client} is null
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public static Project getDefault(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/projects_get_default
		return client.getElement(REST_SERVER.resolve("v2/projects/default"), Map.of(), body ->
			getByJson(client, body.get("project")));
	}

	/**
	 * Looks up a project by its ID.
	 *
	 * @param client the client configuration
	 * @param id     the ID
	 * @return null if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Project getById(DigitalOceanClient client, Id id)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/projects_get
		return client.getResource(REST_SERVER.resolve("v2/projects/" + id.getValue()), body ->
		{
			JsonNode project = body.get("project");
			return getByJson(client, project);
		});
	}

	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param client the client configuration
	 * @param json   the JSON representation
	 * @return the project
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	static Project getByJson(DigitalOceanClient client, JsonNode json)
		throws IOException, InterruptedException, TimeoutException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/projects_get
		Id id = id(json.get("id").textValue());
		String ownerUuid = json.get("owner_uuid").textValue();
		int ownerId = client.getInt(json, "owner_id");
		String name = json.get("name").textValue();
		String description = json.get("description").textValue();
		String purpose = json.get("purpose").textValue();
		Environment environment = Environment.getByJson(json.get("environment"));
		boolean isDefault = client.getBoolean(json, "is_default");
		Instant createdAt = Instant.parse(json.get("created_at").textValue());
		Instant updatedAt = Instant.parse(json.get("updated_at").textValue());
		return new Project(client, id, ownerUuid, ownerId, name, description, purpose, environment, isDefault,
			createdAt, updatedAt);
	}

	/**
	 * Creates a new ID.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 * @throws NullPointerException     if {@code value} is null
	 * @throws IllegalArgumentException if {@code value} contains leading or trailing whitespace or is empty
	 */
	public static Id id(String value)
	{
		return new Id(value);
	}

	private final DigitalOceanClient client;
	private final Id id;
	private final String name;
	private final String ownerUuid;
	private final int ownerId;
	private final String description;
	private final String purpose;
	private final Environment environment;
	private final boolean isDefault;
	private final Instant createdAt;
	private final Instant updatedAt;

	/**
	 * Creates a new project.
	 *
	 * @param client      the client configuration
	 * @param id          the ID of the project
	 * @param ownerUuid   the UUID of the project owner
	 * @param ownerId     the UUID of the project id
	 * @param name        the name of the project
	 * @param description a description of the project
	 * @param purpose     the purpose of the project
	 * @param environment the project's environment
	 * @param isDefault   {@code true} if this is the default project
	 * @param createdAt   the time the project was created
	 * @param updatedAt   the time the project was last updated
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code ownerUuid}, {@code name} or {@code purpose} contain leading or
	 *                                  trailing whitespace or are empty
	 * @see Vpc#getDefault(DigitalOceanClient, Zone.Id)
	 */
	private Project(DigitalOceanClient client, Id id, String ownerUuid, int ownerId, String name,
		String description, String purpose, Environment environment, boolean isDefault, Instant createdAt,
		Instant updatedAt)
	{
		requireThat(client, "client").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(ownerUuid, "ownerUuid").isStripped().isNotEmpty();
		requireThat(description, "description").isStripped();
		requireThat(purpose, "purpose").isStripped().isNotEmpty();
		requireThat(environment, "environment").isNotNull();
		requireThat(createdAt, "createdAt").isNotNull();
		requireThat(updatedAt, "updatedAt").isNotNull();

		this.client = client;
		this.id = id;
		this.name = name;
		this.ownerUuid = ownerUuid;
		this.ownerId = ownerId;
		this.description = description;
		this.purpose = purpose;
		this.environment = environment;
		this.isDefault = isDefault;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns the project's ID.
	 *
	 * @return the ID
	 */
	public Id getId()
	{
		return id;
	}

	/**
	 * Returns the name of the project.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the UUID of the project owner.
	 *
	 * @return the UUID
	 */
	public String getOwnerUuid()
	{
		return ownerUuid;
	}

	/**
	 * Returns the ID of the project owner.
	 *
	 * @return the ID
	 */
	public int getOwnerId()
	{
		return ownerId;
	}

	/**
	 * Returns a description of the project.
	 *
	 * @return the description
	 */
	public String getDescription()
	{
		return description;
	}

	/**
	 * Returns the purpose of the project.
	 *
	 * @return the purpose
	 */
	public String getPurpose()
	{
		return purpose;
	}

	/**
	 * Returns the project's environment.
	 *
	 * @return the environment
	 */
	public Environment getEnvironment()
	{
		return environment;
	}

	/**
	 * Determines if this is the default project.
	 *
	 * @return {@code true} if this is the default project
	 */
	public boolean isDefault()
	{
		return isDefault;
	}

	/**
	 * Returns the time the project was created.
	 *
	 * @return the time
	 */
	public Instant getCreatedAt()
	{
		return createdAt;
	}

	/**
	 * Returns the time the project was last updated.
	 *
	 * @return the time
	 */
	public Instant getUpdatedAt()
	{
		return updatedAt;
	}

	/**
	 * Destroys the project.
	 *
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public void destroy() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/projects_delete
		client.destroyResource(REST_SERVER.resolve("v2/projects/" + id));
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Project.class).
			add("id", id).
			add("name", name).
			add("ownerUuid", ownerUuid).
			add("ownerId", ownerId).
			add("description", description).
			add("purpose", purpose).
			add("environment", environment).
			add("isDefault", isDefault).
			add("createdAt", createdAt).
			add("updatedAt", updatedAt).
			toString();
	}

	/**
	 * A type-safe identifier for this type of resource.
	 * <p>
	 * This adds type-safety to API methods by ensuring that IDs specific to one class cannot be used in place
	 * of IDs belonging to another class.
	 */
	public static final class Id extends StringId
	{
		/**
		 * @param value a server-side identifier
		 * @throws NullPointerException     if {@code value} is null
		 * @throws IllegalArgumentException if {@code value} contains leading or trailing whitespace or is empty
		 */
		private Id(String value)
		{
			super(value);
		}
	}

	/**
	 * A project's deployment environment.
	 */
	public enum Environment
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
		PRODUCTION;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		public static Environment getByJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
	}
}