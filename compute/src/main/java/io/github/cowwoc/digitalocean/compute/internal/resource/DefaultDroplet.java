package io.github.cowwoc.digitalocean.compute.internal.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cowwoc.digitalocean.compute.internal.client.DefaultComputeClient;
import io.github.cowwoc.digitalocean.compute.internal.parser.ComputeParser;
import io.github.cowwoc.digitalocean.compute.resource.Droplet;
import io.github.cowwoc.digitalocean.compute.resource.DropletFeature;
import io.github.cowwoc.digitalocean.compute.resource.DropletImage;
import io.github.cowwoc.digitalocean.core.id.ComputeDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.DropletId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.id.VpcId;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;
import io.github.cowwoc.digitalocean.network.client.NetworkClient;
import io.github.cowwoc.requirements12.annotation.CheckReturnValue;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static io.github.cowwoc.digitalocean.core.internal.client.AbstractInternalClient.REST_SERVER;
import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;

public final class DefaultDroplet implements Droplet
{
	private final DefaultComputeClient client;
	private final DropletId id;
	private final String name;
	private final ComputeDropletTypeId typeId;
	private final DropletImage image;
	private final RegionId regionId;
	private final VpcId vpcId;
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
	 * @param typeId    the machine type
	 * @param image     the ID of the image of a public or private image or the slug identifier for a public
	 *                  image that will be used to boot this droplet
	 * @param regionId  the ID of the region that the droplet is deployed in
	 * @param vpcId     the ID of the VPC that the droplet is deployed in
	 * @param addresses the droplet's IP addresses
	 * @param features  the features that are enabled on the droplet
	 * @param tags      the tags that are associated with the droplet
	 * @param createdAt the time the droplet was created
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 * @see NetworkClient#getDefaultVpcId(RegionId)
	 */
	public DefaultDroplet(DefaultComputeClient client, DropletId id, String name, ComputeDropletTypeId typeId,
		DropletImage image, RegionId regionId, VpcId vpcId, Set<InetAddress> addresses,
		Set<DropletFeature> features, Set<String> tags, Instant createdAt)
	{
		requireThat(client, "client").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(typeId, "typeId").isNotNull();
		requireThat(image, "image").isNotNull();
		requireThat(regionId, "regionId").isNotNull();
		requireThat(vpcId, "vpcId").isNotNull();
		requireThat(addresses, "addresses").isNotNull();
		requireThat(features, "features").isNotNull();
		requireThat(tags, "tags").isNotNull();
		for (String tag : tags)
			requireThat(tag, "tag").withContext(tags, "tags").isStripped().isNotEmpty();
		requireThat(createdAt, "createdAt").isNotNull();

		this.client = client;
		this.id = id;
		this.name = name;
		this.typeId = typeId;
		this.image = image;
		this.regionId = regionId;
		this.vpcId = vpcId;
		this.addresses = Set.copyOf(addresses);
		this.features = EnumSet.copyOf(features);
		this.tags = Set.copyOf(tags);
		this.createdAt = createdAt;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public ComputeDropletTypeId getTypeId()
	{
		return typeId;
	}

	@Override
	public DropletImage getImage()
	{
		return image;
	}

	@Override
	public RegionId getRegionId()
	{
		return regionId;
	}

	@Override
	public VpcId getVpcId()
	{
		return vpcId;
	}

	@Override
	public Set<InetAddress> getAddresses()
	{
		return addresses;
	}

	@Override
	public Set<DropletFeature> getFeatures()
	{
		return features;
	}

	@Override
	public Set<String> getTags()
	{
		return tags;
	}

	@Override
	public Instant getCreatedAt()
	{
		return createdAt;
	}

	@Override
	@CheckReturnValue
	public Droplet reload() throws IOException, InterruptedException
	{
		return client.getDroplet(id);
	}

	@Override
	public Droplet renameTo(String newName) throws IOException, InterruptedException
	{
		requireThat(newName, "newName").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/dropletActions_post
		JsonMapper jm = client.getJsonMapper();
		ObjectNode requestBody = jm.createObjectNode().
			put("type", "rename").
			put("name", newName);
		Request request = client.createRequest(REST_SERVER.resolve("v2/droplets/" + id + "/actions"),
			requestBody);
		Response serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		ContentResponse contentResponse = (ContentResponse) serverResponse;
		JsonNode responseBody = client.getResponseBody(contentResponse);
		String status = responseBody.get("status").textValue();
		ComputeParser parser = client.getComputeParser();
		while (status.equals("in-progress"))
		{
			// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/dropletActions_get
			int actionId = parser.getInt(responseBody, "id");
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

	@Override
	public void destroy() throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/droplets_destroy
		client.destroyResource(REST_SERVER.resolve("v2/droplets/" + id));
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DefaultDroplet.class).
			add("id", id).
			add("name", name).
			add("typeId", typeId).
			add("image", image).
			add("regionId", regionId).
			add("vpcId", vpcId).
			add("addresses", addresses).
			add("features", features).
			add("tags", tags).
			add("createdAt", createdAt).
			toString();
	}
}