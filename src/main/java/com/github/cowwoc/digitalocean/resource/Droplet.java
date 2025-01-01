package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import com.github.cowwoc.digitalocean.internal.util.DigitalOceans;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import com.github.cowwoc.requirements10.annotation.CheckReturnValue;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.util.DigitalOceans.REST_SERVER;
import static com.github.cowwoc.digitalocean.resource.DropletFeature.IPV6;
import static com.github.cowwoc.digitalocean.resource.DropletFeature.MONITORING;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.DELETE;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;

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
	public static Droplet getById(DigitalOceanClient client, int id)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_get
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		ClientRequests clientRequests = client.getClientRequests();
		String uri = REST_SERVER + "/v2/droplets/" + id;
		ContentResponse serverResponse = clientRequests.send(httpClient.newRequest(uri).
			method(GET).
			headers(headers -> headers.put("Content-Type", "application/json").
				put("Authorization", "Bearer " + client.getAccessToken())));
		JsonNode body = DigitalOceans.getResponseBody(client, serverResponse);
		JsonNode droplet = body.get("droplet");
		return getByJson(client, droplet);
	}

	/**
	 * Looks up droplets by their name.
	 *
	 * @param client the client configuration
	 * @param name   a name
	 * @return an empty list if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static List<Droplet> getByName(DigitalOceanClient client, String name)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(name, "name").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_list
		String uri = REST_SERVER + "/v2/droplets";
		return DigitalOceans.getElements(client, uri, Map.of(), body ->
		{
			// https://docs.digitalocean.com/reference/api/intro/#links--pagination
			List<Droplet> droplets = new ArrayList<>();
			for (JsonNode droplet : body.get("droplets"))
			{
				String actualName = droplet.get("name").textValue();
				if (actualName.equals(name))
					droplets.add(getByJson(client, droplet));
			}
			return droplets;
		});
	}

	/**
	 * Looks up a droplet by its address.
	 *
	 * @param client  the client configuration
	 * @param address the address
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code address} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static Droplet getByAddress(DigitalOceanClient client, String address)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(address, "address").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_list
		String uri = REST_SERVER + "/v2/droplets";
		return DigitalOceans.getElement(client, uri, Map.of(), body ->
		{
			// https://docs.digitalocean.com/reference/api/intro/#links--pagination
			for (JsonNode droplet : body.get("droplets"))
			{
				JsonNode networks = droplet.get("networks");
				JsonNode v4 = networks.get("v4");
				for (JsonNode addressNode : v4)
				{
					String value = addressNode.get("ip_address").textValue();
					if (value.equals(address))
						return getByJson(client, droplet);
				}
				JsonNode v6 = networks.get("v6");
				for (JsonNode addressNode : v6)
				{
					String value = addressNode.get("ip_address").textValue();
					if (value.equals(address))
						return getByJson(client, droplet);
				}
			}
			return null;
		});
	}

	/**
	 * Looks up a droplet by its tags.
	 *
	 * @param client the client configuration
	 * @param tags   one or more tags
	 * @return an empty list if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the tags contain leading or trailing whitespace or are empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static List<Droplet> getByTags(DigitalOceanClient client, Collection<String> tags)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(tags, "tags").isNotNull();
		for (String tag : tags)
			requireThat(tag, "tag").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_list
		String uri = REST_SERVER + "/v2/droplets";
		return DigitalOceans.getElements(client, uri, Map.of("tag_name", tags), body ->
		{
			List<Droplet> droplets = new ArrayList<>();
			for (JsonNode droplet : body.get("droplets"))
				droplets.add(getByJson(client, droplet));
			return droplets;
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
	 * @param vpc    the VPC that the droplet will be deployed into
	 * @return a new Droplet builder
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
	 * @see Vpc#getDefault(DigitalOceanClient, Zone)
	 */
	public static DropletCreator creator(DigitalOceanClient client, String name, DropletType type,
		DropletImage image, Vpc vpc)
	{
		return new DropletCreator(client, name, type, image, vpc);
	}

	/**
	 * @param client the client configuration
	 * @param json   the JSON representation of the droplet
	 * @return the droplet
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	static Droplet getByJson(DigitalOceanClient client, JsonNode json)
		throws IOException, InterruptedException, TimeoutException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_get
		int id = DigitalOceans.toInt(json, "id");
		String name = json.get("name").textValue();
		Instant createdAt = Instant.parse(json.get("created_at").textValue());
		String sizeSlug = json.get("size_slug").textValue();
		DropletType type = DropletType.getBySlug(sizeSlug);

		JsonNode imageNode = json.get("image");
		int imageId = imageNode.get("id").intValue();
		String imageSlug = imageNode.get("slug").textValue();
		DropletImage image = new DropletImage(imageId, imageSlug);

		JsonNode zoneNode = json.get("region");
		String zoneSlug = zoneNode.get("slug").textValue();
		Zone zone = Zone.getBySlug(zoneSlug);
		JsonNode featuresNode = json.get("features");
		Set<String> featureSet = new HashSet<>();
		featuresNode.forEach(element -> featureSet.add(element.textValue()));
		Set<DropletFeature> features = EnumSet.noneOf(DropletFeature.class);
		if (featureSet.contains("droplet_agent"))
			features.add(DropletFeature.METRICS_AGENT);
		if (featureSet.contains("ipv6"))
			features.add(IPV6);
		if (featureSet.contains("monitoring"))
			features.add(MONITORING);
		if (featureSet.contains("private_networking"))
			features.add(DropletFeature.PRIVATE_NETWORKING);
		JsonNode vpcNode = json.get("vpc_uuid");
		Vpc vpc;
		if (vpcNode != null)
			vpc = Vpc.getById(client, vpcNode.textValue());
		else
			vpc = Vpc.getDefault(client, zone);

		List<InetAddress> addresses = new ArrayList<>();
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

		JsonNode tagsNode = json.get("tags");
		Set<String> tags = new HashSet<>();
		if (tagsNode != null)
			for (JsonNode element : tagsNode)
				tags.add(element.textValue());
		return new Droplet(client, id, name, createdAt, type, image, zone, vpc, addresses, features, tags);
	}

	private final DigitalOceanClient client;
	private final int id;
	private final String name;
	private final Instant createdAt;
	private final DropletType type;
	private final DropletImage image;
	private final Zone zone;
	private final Vpc vpc;
	private final List<InetAddress> addresses;
	private final Set<DropletFeature> features;
	private final Set<String> tags;

	/**
	 * Creates a new droplet.
	 *
	 * @param client    the client configuration
	 * @param id        the ID of the droplet
	 * @param name      the name of the droplet
	 * @param createdAt the time the droplet was created
	 * @param type      the machine type
	 * @param image     the image ID of a public or private image or the slug identifier for a public image that
	 *                  will be used to boot this droplet
	 * @param zone      the zone that the droplet is deployed in
	 * @param vpc       the VPC that the droplet is deployed in
	 * @param addresses the droplet's IP addresses
	 * @param features  the features that are enabled on the droplet
	 * @param tags      the tags that are associated with the droplet
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 * @see Vpc#getDefault(DigitalOceanClient, Zone)
	 */
	private Droplet(DigitalOceanClient client, int id, String name, Instant createdAt, DropletType type,
		DropletImage image, Zone zone, Vpc vpc, List<InetAddress> addresses, Set<DropletFeature> features,
		Set<String> tags)
	{
		requireThat(client, "client").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(createdAt, "createdAt").isNotNull();
		requireThat(type, "type").isNotNull();
		requireThat(image, "image").isNotNull();
		requireThat(zone, "zone").isNotNull();
		requireThat(vpc, "vpc").isNotNull();
		requireThat(addresses, "addresses").isNotNull();
		requireThat(features, "features").isNotNull();
		requireThat(tags, "tags").isNotNull();
		for (String tag : tags)
			requireThat(tag, "tag").isStripped().isNotEmpty();

		this.client = client;
		this.id = id;
		this.name = name;
		this.createdAt = createdAt;
		this.type = type;
		this.image = image;
		this.zone = zone;
		this.vpc = vpc;
		this.addresses = List.copyOf(addresses);
		this.features = EnumSet.copyOf(features);
		this.tags = Set.copyOf(tags);
	}

	/**
	 * Returns the name of the droplet.
	 *
	 * @return the name of the droplet
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the time the droplet was created.
	 *
	 * @return the time the droplet was created
	 */
	public Instant getCreatedAt()
	{
		return createdAt;
	}

	/**
	 * Returns the type of the droplet.
	 *
	 * @return the type of the droplet
	 */
	public DropletType getType()
	{
		return type;
	}

	/**
	 * Returns the image used to boot this droplet.
	 *
	 * @return the image used to boot this droplet
	 */
	public DropletImage getImage()
	{
		return image;
	}

	/**
	 * Returns the zone that the droplet is deployed in.
	 *
	 * @return the zone that the droplet is deployed in
	 */
	public Zone getZone()
	{
		return zone;
	}

	/**
	 * Returns the VPC that the droplet is deployed in.
	 *
	 * @return the VPC
	 */
	public Vpc getVpc()
	{
		return vpc;
	}

	/**
	 * Returns the droplet's IP addresses.
	 *
	 * @return the droplet's IP addresses
	 */
	public List<InetAddress> getAddresses()
	{
		return addresses;
	}

	/**
	 * Returns the features that are enabled on the droplet.
	 *
	 * @return the features that are enabled on the droplet
	 */
	public Set<DropletFeature> getFeatures()
	{
		return features;
	}

	/**
	 * Returns the droplet's tags.
	 *
	 * @return the droplet's tags
	 */
	public Set<String> getTags()
	{
		return tags;
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
	public boolean matchesState(DropletCreator state)
	{
		return state.type().equals(type) && state.image().equals(image) &&
			state.zone().equals(zone) && state.features().equals(features) &&
			state.tags().equals(tags) && state.vpc().equals(vpc);
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
		ClientRequests clientRequests = client.getClientRequests();
		String uri = REST_SERVER + "/v2/droplets/" + id + "/actions";
		ObjectMapper om = client.getObjectMapper();
		ObjectNode requestBody = om.createObjectNode().
			put("type", "rename").
			put("name", newName);
		Request request = DigitalOceans.createRequest(client, uri, requestBody);
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				// success
			}
			default -> throw new AssertionError(
				"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
					"Request: " + clientRequests.toString(request));
		}
		JsonNode responseBody = DigitalOceans.getResponseBody(client, serverResponse);
		String status = responseBody.get("status").textValue();
		while (status.equals("in-progress"))
		{
			// https://docs.digitalocean.com/reference/api/api-reference/#operation/dropletActions_get
			int actionId = DigitalOceans.toInt(responseBody, "id");
			uri = REST_SERVER + "/v2/droplets/" + id + "/actions/" + actionId;
			request = DigitalOceans.createRequest(client, uri);
			serverResponse = clientRequests.send(request);
			status = responseBody.get("status").textValue();
		}
		return switch (status)
		{
			case "completed" -> reload();
			case "errored" -> throw new IOException("Failed to rename droplet " + id + " to " + newName);
			default -> throw new AssertionError(
				"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
					"Request: " + clientRequests.toString(request));
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
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		ClientRequests clientRequests = client.getClientRequests();
		String uri = REST_SERVER + "/v2/droplets/" + id;
		ContentResponse serverResponse = clientRequests.send(httpClient.newRequest(uri).
			method(DELETE).
			headers(headers -> headers.put("Content-Type", "application/json").
				put("Authorization", "Bearer " + client.getAccessToken())));
		requireThat(serverResponse.getStatus(), "responseCode").
			withContext(serverResponse.getContentAsString(), "serverResponse").
			isEqualTo(NO_CONTENT_204);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Droplet.class).
			add("id", id).
			add("name", name).
			add("createdAt", createdAt).
			add("type", type).
			add("image", image).
			add("zone", zone).
			add("vpc", vpc).
			add("addresses", addresses).
			add("features", features).
			add("tags", tags).
			toString();
	}
}