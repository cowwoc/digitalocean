package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.digitalocean.exception.PermissionDeniedException;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import com.github.cowwoc.digitalocean.internal.util.DigitalOceans;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import com.github.cowwoc.digitalocean.scope.DigitalOceanScope;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.util.DigitalOceans.REST_SERVER;
import static com.github.cowwoc.digitalocean.resource.DropletFeature.MONITORING;
import static com.github.cowwoc.digitalocean.resource.DropletFeature.PRIVATE_NETWORKING;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.ACCEPTED_202;
import static org.eclipse.jetty.http.HttpStatus.UNPROCESSABLE_ENTITY_422;

/**
 * The specification of a new droplet.
 */
public final class DropletBuilder
{
	private final DigitalOceanScope scope;
	private final String name;
	private final DropletType type;
	private final DropletImage image;
	private Zone zone;
	private final Set<SshPublicKey> sshKeys = new LinkedHashSet<>();
	private final Set<DropletFeature> features = EnumSet.of(MONITORING, PRIVATE_NETWORKING);
	private final Set<String> tags = new LinkedHashSet<>();
	private final Vpc vpc;
	private String userData = "";

	/**
	 * Creates a new DropletBuilder.
	 *
	 * @param scope the client configuration
	 * @param name  the name of the droplet. Names are case-insensitive.
	 * @param type  the machine type of the droplet
	 * @param image the image ID of a public or private image or the slug identifier for a public image that
	 *              will be used to boot this droplet
	 * @param vpc   the VPC to which the droplet will be assigned
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
	 * @see Vpc#getDefault(DigitalOceanScope, Zone)
	 */
	DropletBuilder(DigitalOceanScope scope, String name, DropletType type, DropletImage image, Vpc vpc)
	{
		requireThat(scope, "scope").isNotNull();
		// Taken from https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_create
		requireThat(name, "name").matches("^[a-zA-Z0-9]?[a-z0-9A-Z.\\-]*[a-z0-9A-Z]$");
		requireThat(type, "type").isNotNull();
		requireThat(image, "image").isNotNull();
		requireThat(vpc, "vpc").isNotNull();
		this.scope = scope;
		this.name = name;
		this.type = type;
		this.image = image;
		this.vpc = vpc;
	}

	/**
	 * Returns the name of the droplet.
	 *
	 * @return the name of the droplet
	 */
	public String name()
	{
		return name;
	}

	/**
	 * Returns the machine type of the droplet.
	 *
	 * @return the machine type of the droplet
	 */
	public DropletType type()
	{
		return type;
	}

	/**
	 * Returns the image ID of a public or private image or the slug identifier for a public image that will be
	 * used to boot this droplet.
	 *
	 * @return the image
	 */
	public DropletImage image()
	{
		return image;
	}

	/**
	 * Returns the VPC that the droplet will be deployed in.
	 *
	 * @return the VPC
	 */
	public Vpc vpc()
	{
		return vpc;
	}

	/**
	 * Sets the zone to create the droplet in.
	 *
	 * @param zone the zone to create the droplet in
	 * @return this
	 * @throws NullPointerException if {@code zone} is null
	 */
	public DropletBuilder zone(Zone zone)
	{
		requireThat(zone, "zone").isNotNull();
		this.zone = zone;
		return this;
	}

	/**
	 * Returns the zone to create the droplet in.
	 *
	 * @return the zone
	 */
	public Zone zone()
	{
		return zone;
	}

	/**
	 * Adds an SSH key that may be used to connect to the droplet.
	 *
	 * @param key a public key
	 * @return this
	 */
	public DropletBuilder sshKey(SshPublicKey key)
	{
		sshKeys.add(key);
		return this;
	}

	/**
	 * Returns the SSH keys that may be used to connect to the droplet.
	 *
	 * @return the SSH keys
	 */
	public Set<SshPublicKey> sshKeys()
	{
		return sshKeys;
	}

	/**
	 * Enables a feature on the droplet.
	 *
	 * @param feature the feature to enable
	 * @return this
	 * @throws NullPointerException if {@code feature} is null
	 */
	public DropletBuilder feature(DropletFeature feature)
	{
		requireThat(feature, "feature").isNotNull();
		this.features.add(feature);
		return this;
	}

	/**
	 * Sets the features that should be enabled on the droplet.
	 *
	 * @param features the features that should be enabled
	 * @return this
	 * @throws NullPointerException     if {@code features} is null
	 * @throws IllegalArgumentException if any of the features are null
	 */
	public DropletBuilder features(Collection<DropletFeature> features)
	{
		requireThat(features, "features").isNotNull().doesNotContain(null);
		this.features.addAll(features);
		return this;
	}

	/**
	 * Returns the features that should be enabled on the droplet.
	 *
	 * @return the features
	 */
	public Set<DropletFeature> features()
	{
		return Set.copyOf(features);
	}

	/**
	 * Adds a tag to the droplet.
	 *
	 * @param tag the tag to add
	 * @return this
	 * @throws NullPointerException     if {@code tag} is null
	 * @throws IllegalArgumentException if the tag:
	 *                                  <ul>
	 *                                    <li>contains any characters other than letters, numbers, colons,
	 *                                    dashes and underscores.</li>
	 *                                    <li>is longer than 255 characters.</li>
	 *                                  </ul>
	 */
	public DropletBuilder tag(String tag)
	{
		// Discovered empirically: DigitalOcean drops all tags silently if any of them contain invalid characters.
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/tags_create
		requireThat(tag, "tag").matches("^[a-zA-Z0-9_\\-:]+$").length().isLessThanOrEqualTo(255);
		this.tags.add(tag);
		return this;
	}

	/**
	 * Adds tags to the droplet.
	 *
	 * @param tags the tags to add
	 * @return this
	 * @throws NullPointerException     if {@code tags} is null
	 * @throws IllegalArgumentException if any of the tags:
	 *                                  <ul>
	 *                                    <li>are null.</li>
	 *                                    <li>contain any characters other than letters, numbers, colons,
	 *                                    dashes and underscores.</li>
	 *                                    <li>is longer than 255 characters.</li>
	 *                                  </ul>
	 */
	public DropletBuilder tags(Collection<String> tags)
	{
		requireThat(tags, "tags").isNotNull().doesNotContain(null);
		for (String tag : tags)
		{
			requireThat(tag, "tag").withContext(tags, "tags").matches("^[a-zA-Z0-9_\\-:]+$").
				length().isLessThanOrEqualTo(255);
			this.tags.add(tag);
		}
		return this;
	}

	/**
	 * Returns the droplet's tags.
	 *
	 * @return the tags
	 */
	public Set<String> tags()
	{
		return Set.copyOf(tags);
	}

	/**
	 * Sets the droplet's "user data" which may be used to configure the Droplet on the first boot, often a
	 * "cloud-config" file or Bash script. It must be plain text and may not exceed 64 KiB in size.
	 *
	 * @param userData the user data
	 * @return this
	 * @throws NullPointerException     if {@code userData} is null
	 * @throws IllegalArgumentException if {@code userData}:
	 *                                  <ul>
	 *                                    <li>contains leading or trailing whitespace.</li>
	 *                                    <li>is empty.</li>
	 *                                    <li>is longer than 64 KiB characters.</li>
	 *                                  </ul>
	 */
	public DropletBuilder userData(String userData)
	{
		requireThat(userData, "userData").isStripped().isNotEmpty().length().isLessThanOrEqualTo(64 * 1024);
		this.userData = userData;
		return this;
	}

	/**
	 * Returns the droplet's "user data" which may be used to configure the Droplet on the first boot, often a
	 * "cloud-config" file or Bash script. It must be plain text and may not exceed 64 KiB in size.
	 *
	 * @return the user data
	 */
	public String userData()
	{
		return userData;
	}

	/**
	 * Creates a new droplet.
	 *
	 * @return the new droplet
	 * @throws PermissionDeniedException if the request exceeded the client's droplet limit
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws TimeoutException          if the request times out before receiving a response. This might
	 *                                   indicate network latency or server overload.
	 * @throws InterruptedException      if the thread is interrupted while waiting for a response. This can
	 *                                   happen due to shutdown signals.
	 */
	public Droplet create()
		throws PermissionDeniedException, IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_create
		ObjectMapper om = scope.getObjectMapper();
		ObjectNode requestBody = om.createObjectNode().
			put("name", name).
			put("size", type.toString()).
			put("image", image.getId());
		if (zone != null)
			requestBody.put("region", zone.toString());
		if (!sshKeys.isEmpty())
		{
			ArrayNode sshKeysNode = requestBody.putArray("ssh_keys");
			for (SshPublicKey key : sshKeys)
				sshKeysNode.add(key.getId());
		}
		requestBody.put("vpc_uuid", vpc.getId());
		for (DropletFeature feature : features)
		{
			String name = switch (feature)
			{
				// Enabled by default and cannot be disabled
				case MONITORING -> "";
				case METRICS_AGENT -> "monitoring";
				default -> feature.getJsonName();
			};
			if (name.isEmpty())
				continue;
			requestBody.put("monitoring", true);
			requestBody.put(name, true);
		}
		if (!tags.isEmpty())
		{
			ArrayNode tagsNode = requestBody.putArray("tags");
			for (String tag : tags)
				tagsNode.add(tag);
		}
		if (!userData.isEmpty())
			requestBody.put("user_data", userData);

		String uri = REST_SERVER + "/v2/droplets";
		Request request = DigitalOceans.createRequest(scope, uri, requestBody).
			method(POST);
		ClientRequests clientRequests = scope.getClientRequests();
		ContentResponse serverResponse = clientRequests.send(request);
		String responseAsString = serverResponse.getContentAsString();
		switch (serverResponse.getStatus())
		{
			case ACCEPTED_202 ->
			{
				// success
			}
			case UNPROCESSABLE_ENTITY_422 ->
			{
				// Example: creating this/these droplet(s) will exceed your droplet limit
				JsonNode json = DigitalOceans.getResponseBody(scope, serverResponse);
				throw new PermissionDeniedException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " +
				clientRequests.toString(serverResponse) + "\n" +
				"Request: " + clientRequests.toString(request));
		}
		JsonNode body = scope.getObjectMapper().readTree(responseAsString);
		JsonNode dropletNode = body.get("droplet");
		if (dropletNode == null)
		{
			throw new AssertionError("Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
				"Request: " + clientRequests.toString(request));
		}
		return Droplet.getByJson(scope, dropletNode);
	}

//	/**
//	 * Updates a digest based on the requested droplet state.
//	 *
//	 * @param md a digest representing the state of the droplet after its creation and environment
//	 *           configuration. This typically includes scripts or configurations that will be executed on the
//	 *           droplet to set up its environment.<p>
//	 *           <b>Note</b>: Only include key properties that determine the final state of the
//	 *           droplet. Avoid including transient or dynamic values, such as temporary passwords that change
//	 *           on each run, to ensure the digest remains consistent and reflective of the intended droplet
//	 *           configuration.</p>
//	 * @throws NullPointerException if {@code md} is null
//	 */
//	public void updateDigest(MessageDigest md)
//	{
//		md.update(name.getBytes(UTF_8));
//
//		ByteBuffer intToBytes = ByteBuffer.allocate(4);
//		md.update(intToBytes.putInt(type.ordinal()).array());
//
//		intToBytes.clear();
//		md.update(intToBytes.putInt(zone.ordinal()).array());
//
//		intToBytes.clear();
//		md.update(intToBytes.putInt(image.getId()).array());
//
//		for (Object sshKey : sshKeys)
//			md.update(sshKey.toString().getBytes(UTF_8));
//		for (DropletFeature feature : features)
//		{
//			intToBytes.clear();
//			md.update(intToBytes.putInt(feature.ordinal()).array());
//		}
//		for (String tag : tags)
//			md.update(tag.getBytes(UTF_8));
//		md.update(vpc.getId().getBytes(UTF_8));
//		md.update(userData.getBytes(UTF_8));
//	}
//
//	/**
//	 * Returns a String representation of a {@code MessageDigest} that can be set as a droplet tag.
//	 *
//	 * @param md a digest
//	 * @return the base64 encoded digest of the droplet state
//	 * @throws NullPointerException if {@code md} is null
//	 */
//	public String asTag(MessageDigest md)
//	{
//		// Use URL-safe encoding without padding to ensure compatibility with DigitalOcean's supported characters
//		return md.getAlgorithm() + ":" + Base64.getUrlEncoder().withoutPadding().
//			encodeToString(md.digest());
//	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DropletBuilder.class).
			add("name", name).
			add("type", type).
			add("image", image).
			add("zone", zone).
			add("vpc", vpc).
			add("features", features).
			add("tags", tags).
			add("sshKeys", sshKeys).
			add("userData", userData).
			toString();
	}
}