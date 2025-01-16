package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.id.IntegerId;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import com.github.cowwoc.requirements10.annotation.CheckReturnValue;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.digitalocean.resource.DropletFeature.IPV6;
import static com.github.cowwoc.digitalocean.resource.DropletFeature.MONITORING;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;

/**
 * A computer node.
 */
public final class Droplet
{
	/**
	 * Looks up a droplet by its ID.
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
	public static Droplet getById(DigitalOceanClient client, Id id)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_get
		return client.getResource(REST_SERVER.resolve("v2/droplets/" + id.getValue()), body ->
		{
			JsonNode droplet = body.get("droplet");
			return getByJson(client, droplet);
		});
	}

	/**
	 * Returns the all the Droplets.
	 *
	 * @param client the client configuration
	 * @return an empty Set if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Set<Droplet> getAll(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_list
		return client.getElements(REST_SERVER.resolve("v2/droplets"), Map.of(), body ->
		{
			Set<Droplet> droplets = new HashSet<>();
			for (JsonNode droplet : body.get("droplets"))
				droplets.add(getByJson(client, droplet));
			return droplets;
		});
	}

	/**
	 * Returns the first Droplet that matches a predicate.
	 *
	 * @param predicate the predicate
	 * @return null if no match was found
	 * @throws NullPointerException     if {@code predicate} is null
	 * @throws IllegalArgumentException if any of the tags contain leading or trailing whitespace or are empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public Droplet getByPredicate(Predicate<Droplet> predicate)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_list
		return client.getElement(REST_SERVER.resolve("v2/droplets"), Map.of(), body ->
		{
			for (JsonNode droplet : body.get("droplets"))
			{
				Droplet candidate = getByJson(client, droplet);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	/**
	 * Creates a droplet.
	 *
	 * @param client the client configuration
	 * @param name   the name of the droplet
	 * @param type   the machine type of the droplet
	 * @param image  the image ID of a public or private image or the slug identifier for a public image that
	 *               will be used to boot this droplet
	 * @return a new Droplet creator
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>the {@code name} contains any characters other than {@code A-Z},
	 *                                    {@code a-z}, {@code 0-9} and a hyphen.</li>
	 *                                    <li>the {@code name} does not start or end with an alphanumeric
	 *                                    character.</li>
	 *                                    <li>any of the arguments contain leading or trailing whitespace or
	 *                                    are empty.</li>
	 *                                  </ul>
	 * @throws IllegalStateException    if the client is closed
	 * @see Vpc#getDefault(DigitalOceanClient, Zone.Id)
	 */
	public static DropletCreator creator(DigitalOceanClient client, String name, DropletType.Id type,
		DropletImage image)
	{
		return new DropletCreator(client, name, type, image);
	}

	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param client the client configuration
	 * @param json   the JSON representation
	 * @return the droplet
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
	static Droplet getByJson(DigitalOceanClient client, JsonNode json)
		throws IOException, InterruptedException, TimeoutException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_get
		Id id = id(client.getInt(json, "id"));
		String name = json.get("name").textValue();
		Instant createdAt = Instant.parse(json.get("created_at").textValue());
		String dropletTypeSlug = json.get("size_slug").textValue();
		DropletType.Id type = DropletType.id(dropletTypeSlug);

		DropletImage image = DropletImage.getByJson(client, json.get("image"));
		JsonNode zoneNode = json.get("region");
		String zoneSlug = zoneNode.get("slug").textValue();
		Zone.Id zone = Zone.id(zoneSlug);
		JsonNode featuresNode = json.get("features");
		Set<String> featureSet = new HashSet<>();
		featuresNode.forEach(element -> featureSet.add(element.textValue()));
		Set<DropletFeature> features = EnumSet.noneOf(DropletFeature.class);
		if (featureSet.contains("droplet_agent"))
			features.add(MONITORING);
		if (featureSet.contains("ipv6"))
			features.add(IPV6);
		if (featureSet.contains("monitoring"))
			features.add(MONITORING);
		if (featureSet.contains("private_networking"))
			features.add(DropletFeature.PRIVATE_NETWORKING);

		Vpc.Id vpc = Vpc.id(json.get("vpc_uuid").textValue());
		Set<InetAddress> addresses = new HashSet<>();
		JsonNode networks = json.get("networks");
		JsonNode v4 = networks.get("v4");
		for (JsonNode address : v4)
		{
			String value = address.get("ip_address").textValue();
			addresses.add(InetAddress.ofLiteral(value));
		}
		JsonNode v6 = networks.get("v6");
		for (JsonNode address : v6)
		{
			String value = address.get("ip_address").textValue();
			addresses.add(InetAddress.ofLiteral(value));
		}
		Set<String> tags = client.getElements(json, "tags", JsonNode::textValue);
		return new Droplet(client, id, name, type, image, zone, vpc, addresses, features, tags, createdAt);
	}

	/**
	 * Creates a new ID.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 */
	public static Id id(int value)
	{
		return new Id(value);
	}

	private final DigitalOceanClient client;
	private final Id id;
	private final String name;
	private final DropletType.Id type;
	private final DropletImage image;
	private final Zone.Id zone;
	private final Vpc.Id vpc;
	private final Set<InetAddress> addresses;
	private final Set<DropletFeature> features;
	private final Set<String> tags;
	private final Instant createdAt;

	/**
	 * Creates a new droplet.
	 *
	 * @param client    the client configuration
	 * @param id        the ID of the droplet
	 * @param name      the name of the droplet
	 * @param type      the machine type
	 * @param image     the image ID of a public or private image or the slug identifier for a public image that
	 *                  will be used to boot this droplet
	 * @param zone      the zone that the droplet is deployed in
	 * @param vpc       the VPC that the droplet is deployed in
	 * @param addresses the droplet's IP addresses
	 * @param features  the features that are enabled on the droplet
	 * @param tags      the tags that are associated with the droplet
	 * @param createdAt the time the droplet was created
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 * @see Vpc#getDefault(DigitalOceanClient, Zone.Id)
	 */
	private Droplet(DigitalOceanClient client, Id id, String name, DropletType.Id type, DropletImage image,
		Zone.Id zone, Vpc.Id vpc, Set<InetAddress> addresses, Set<DropletFeature> features, Set<String> tags,
		Instant createdAt)
	{
		requireThat(client, "client").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(type, "type").isNotNull();
		requireThat(image, "image").isNotNull();
		requireThat(zone, "zone").isNotNull();
		requireThat(vpc, "vpc").isNotNull();
		requireThat(addresses, "addresses").isNotNull();
		requireThat(features, "features").isNotNull();
		requireThat(tags, "tags").isNotNull();
		for (String tag : tags)
			requireThat(tag, "tag").withContext(tags, "tags").isStripped().isNotEmpty();
		requireThat(createdAt, "createdAt").isNotNull();

		this.client = client;
		this.id = id;
		this.name = name;
		this.type = type;
		this.image = image;
		this.zone = zone;
		this.vpc = vpc;
		this.addresses = Set.copyOf(addresses);
		this.features = EnumSet.copyOf(features);
		this.tags = Set.copyOf(tags);
		this.createdAt = createdAt;
	}

	/**
	 * Returns the name of the droplet.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the machine type of the droplet.
	 *
	 * @return the machine type
	 */
	public DropletType.Id getType()
	{
		return type;
	}

	/**
	 * Returns the image used to boot this droplet.
	 *
	 * @return the image
	 */
	public DropletImage getImage()
	{
		return image;
	}

	/**
	 * Returns the zone that the droplet is deployed in.
	 *
	 * @return the zone
	 */
	public Zone.Id getZone()
	{
		return zone;
	}

	/**
	 * Returns the VPC that the droplet is deployed in.
	 *
	 * @return the VPC
	 */
	public Vpc.Id getVpc()
	{
		return vpc;
	}

	/**
	 * Returns the droplet's IP addresses.
	 *
	 * @return the IP addresses
	 */
	public Set<InetAddress> getAddresses()
	{
		return addresses;
	}

	/**
	 * Returns the features that are enabled on the droplet.
	 *
	 * @return the features that are enabled
	 */
	public Set<DropletFeature> getFeatures()
	{
		return features;
	}

	/**
	 * Returns the droplet's tags.
	 *
	 * @return the tags
	 */
	public Set<String> getTags()
	{
		return tags;
	}

	/**
	 * Returns the time the droplet was created.
	 *
	 * @return the time
	 */
	public Instant getCreatedAt()
	{
		return createdAt;
	}

	/**
	 * Reloads the droplet's state.
	 *
	 * @return the updated state
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	@CheckReturnValue
	public Droplet reload() throws IOException, TimeoutException, InterruptedException
	{
		return getById(client, id);
	}

	/**
	 * Determines if the Droplet matches the desired state.
	 *
	 * @param state the desired state
	 * @return {@code true} if the droplet matches the desired state; otherwise, {@code false}
	 * @throws NullPointerException if {@code state} is null
	 */
	public boolean matches(DropletCreator state)
	{
		return state.type().equals(type) && state.image().equals(image) && state.zone().equals(zone) &&
			state.features().equals(features) && Objects.equals(state.vpc(), vpc) && state.tags().equals(tags);
	}

	/**
	 * Renames a droplet.
	 *
	 * @param newName the new name
	 * @return the updated droplet
	 * @throws NullPointerException     if {@code newName} is null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>the {@code name} contains any characters other than {@code A-Z},
	 *                                    {@code a-z}, {@code 0-9} and a hyphen.</li>
	 *                                    <li>the {@code name} does not start or end with an alphanumeric
	 *                                    character.</li>
	 *                                    <li>any of the arguments contain leading or trailing whitespace or
	 *                                    are empty.</li>
	 *                                  </ul>
	 * @throws NullPointerException     if {@code newName} is null
	 * @throws IllegalArgumentException if {@code newName} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public Droplet renameTo(String newName) throws IOException, TimeoutException, InterruptedException
	{
		requireThat(newName, "newName").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/dropletActions_post
		ObjectMapper om = client.getObjectMapper();
		ObjectNode requestBody = om.createObjectNode().
			put("type", "rename").
			put("name", newName);
		Request request = client.createRequest(REST_SERVER.resolve("v2/droplets/" + id + "/actions"),
			requestBody);
		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		JsonNode responseBody = client.getResponseBody(serverResponse);
		String status = responseBody.get("status").textValue();
		while (status.equals("in-progress"))
		{
			// https://docs.digitalocean.com/reference/api/api-reference/#operation/dropletActions_get
			int actionId = client.getInt(responseBody, "id");
			URI uri = REST_SERVER.resolve("v2/droplets/" + id + "/actions/" + actionId);
			request = client.createRequest(uri);
			serverResponse = client.send(request);
			status = responseBody.get("status").textValue();
		}
		return switch (status)
		{
			case "completed" -> reload();
			case "errored" -> throw new IOException("Failed to rename droplet " + id + " to " + newName);
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		};
	}

	/**
	 * Destroys the droplet.
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
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_destroy
		client.destroyResource(REST_SERVER.resolve("v2/droplets/" + id));
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Droplet.class).
			add("id", id).
			add("name", name).
			add("type", type).
			add("image", image).
			add("zone", zone).
			add("vpc", vpc).
			add("addresses", addresses).
			add("features", features).
			add("tags", tags).
			add("createdAt", createdAt).
			toString();
	}

	/**
	 * A type-safe identifier for this type of resource.
	 * <p>
	 * This adds type-safety to API methods by ensuring that IDs specific to one class cannot be used in place
	 * of IDs belonging to another class.
	 */
	public static final class Id extends IntegerId
	{
		/**
		 * @param value a server-side identifier
		 */
		private Id(int value)
		{
			super(value);
		}
	}
}