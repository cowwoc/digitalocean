package io.github.cowwoc.digitalocean.compute.internal.resource;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cowwoc.digitalocean.compute.internal.client.DefaultComputeClient;
import io.github.cowwoc.digitalocean.compute.resource.Droplet;
import io.github.cowwoc.digitalocean.compute.resource.DropletCreator;
import io.github.cowwoc.digitalocean.compute.resource.DropletFeature;
import io.github.cowwoc.digitalocean.compute.resource.DropletImage;
import io.github.cowwoc.digitalocean.compute.resource.DropletType;
import io.github.cowwoc.digitalocean.compute.resource.SshPublicKey;
import io.github.cowwoc.digitalocean.core.internal.util.Strings;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;
import io.github.cowwoc.digitalocean.network.internal.resource.NetworkParser;
import io.github.cowwoc.digitalocean.network.resource.Region;
import io.github.cowwoc.digitalocean.network.resource.Vpc;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static io.github.cowwoc.digitalocean.compute.resource.DropletFeature.MONITORING;
import static io.github.cowwoc.digitalocean.compute.resource.DropletFeature.PRIVATE_NETWORKING;
import static io.github.cowwoc.digitalocean.core.internal.client.AbstractInternalClient.REST_SERVER;
import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.POST;

public final class DefaultDropletCreator implements DropletCreator
{
	private final DefaultComputeClient client;
	private final String name;
	private final DropletType.Id typeId;
	private final DropletImage.Id imageId;
	private Region.Id regionId;
	private final Set<SshPublicKey> sshKeys = new LinkedHashSet<>();
	private final Set<DropletFeature> features = EnumSet.of(MONITORING, PRIVATE_NETWORKING);
	private final Set<String> tags = new LinkedHashSet<>();
	private Vpc.Id vpcId;
	private String userData = "";
	private BackupSchedule backupSchedule;
	//	private Set<Volume> volumes;
	private boolean failOnUnsupportedOperatingSystem;

	/**
	 * Creates a new instance.
	 *
	 * @param client  the client configuration
	 * @param name    the name of the droplet. Names are case-insensitive.
	 * @param typeId  the machine type of the droplet
	 * @param imageId the image ID of a public or private image or the slug identifier for a public image to use
	 *                to boot this droplet
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
	 */
	public DefaultDropletCreator(DefaultComputeClient client, String name, DropletType.Id typeId,
		DropletImage.Id imageId)
	{
		requireThat(client, "client").isNotNull();
		// Taken from https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/droplets_create
		requireThat(name, "name").matches("^[a-zA-Z0-9]?[a-z0-9A-Z.\\-]*[a-z0-9A-Z]$");
		requireThat(typeId, "typeId").isNotNull();
		requireThat(imageId, "imageId").isNotNull();
		this.client = client;
		this.name = name;
		this.typeId = typeId;
		this.imageId = imageId;
	}

	@Override
	public DropletCreator vpcId(Vpc.Id vpcId)
	{
		this.vpcId = vpcId;
		return this;
	}

	@Override
	public DropletCreator regionId(Region.Id region)
	{
		requireThat(region, "region").isNotNull();
		this.regionId = region;
		return this;
	}

	@Override
	public DropletCreator sshKey(SshPublicKey key)
	{
		sshKeys.add(key);
		return this;
	}

	@Override
	public DropletCreator backupSchedule(BackupSchedule backupSchedule)
	{
		requireThat(backupSchedule, "backupSchedule").isNotNull();
		this.backupSchedule = backupSchedule;
		return this;
	}

	@Override
	public DropletCreator feature(DropletFeature feature)
	{
		requireThat(feature, "feature").isNotNull();
		this.features.add(feature);
		return this;
	}

	@Override
	public DropletCreator features(Collection<DropletFeature> features)
	{
		requireThat(features, "features").isNotNull().doesNotContain(null);
		this.features.addAll(features);
		return this;
	}

	@Override
	public DropletCreator tag(String tag)
	{
		// Discovered empirically: DigitalOcean drops all tags silently if any of them contain invalid characters.
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/tags_create
		requireThat(tag, "tag").matches("^[a-zA-Z0-9_\\-:]+$").length().isLessThanOrEqualTo(255);
		this.tags.add(tag);
		return this;
	}

	@Override
	public DropletCreator tags(Collection<String> tags)
	{
		requireThat(tags, "tags").isNotNull().doesNotContain(null);
		this.tags.clear();
		for (String tag : tags)
		{
			requireThat(tag, "tag").withContext(tags, "tags").matches("^[a-zA-Z0-9_\\-:]+$").
				length().isLessThanOrEqualTo(255);
			this.tags.add(tag);
		}
		return this;
	}

	@Override
	public DropletCreator userData(String userData)
	{
		requireThat(userData, "userData").isStripped().isNotEmpty().length().isLessThanOrEqualTo(64 * 1024);
		this.userData = userData;
		return this;
	}

	@Override
	public DropletCreator failOnUnsupportedOperatingSystem(boolean failOnUnsupportedOperatingSystem)
	{
		this.failOnUnsupportedOperatingSystem = failOnUnsupportedOperatingSystem;
		return this;
	}

	@Override
	public Droplet apply() throws AccessDeniedException, IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/droplets_create
		JsonMapper jm = client.getJsonMapper();
		ObjectNode requestBody = jm.createObjectNode().
			put("name", name).
			put("size", typeId.toString()).
			put("image", imageId.getValue());
		NetworkParser networkParser = client.getNetworkParser();
		if (regionId != null)
			requestBody.put("region", networkParser.regionIdToServer(regionId));
		if (!sshKeys.isEmpty())
		{
			ArrayNode sshKeysNode = requestBody.putArray("ssh_keys");
			for (SshPublicKey key : sshKeys)
				sshKeysNode.add(key.getId().getValue());
		}
		if (backupSchedule != null)
		{
			requestBody.put("backups", true);
			requestBody.set("backups_policy", getBackupScheduleAsJson(backupSchedule));
		}
		if (vpcId != null)
			requestBody.put("vpc_uuid", vpcId.getValue());
		ComputeParser parser = client.getComputeParser();
		for (DropletFeature feature : features)
		{
			String name = parser.dropletFeatureToServer(feature);
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
		if (failOnUnsupportedOperatingSystem)
			requestBody.put("with_droplet_agent", true);

		Request request = client.createRequest(REST_SERVER.resolve("v2/droplets"), requestBody).
			method(POST);
		Response serverResponse = client.send(request);
		return parser.createDroplet(typeId, imageId, request, serverResponse);
	}

	/**
	 * Returns the JSON representation of a BackupSchedule.
	 *
	 * @return the JSON representation
	 * @throws IllegalStateException if the client is closed
	 */
	public ObjectNode getBackupScheduleAsJson(BackupSchedule schedule)
	{
		ObjectNode json = client.getJsonMapper().createObjectNode();
		OffsetTime hourAtUtc = schedule.hour().withOffsetSameInstant(ZoneOffset.UTC);
		json.put("hour", Strings.HOUR_MINUTE_SECOND.format(hourAtUtc));
		json.put("day", schedule.day().name().toLowerCase(Locale.ROOT));
		json.put("plan", getBackupFrequencyAsJson(schedule.frequency()));
		return json;
	}

	/**
	 * Returns the JSON representation of a BackupFrequency.
	 *
	 * @return the JSON representation
	 */
	public String getBackupFrequencyAsJson(BackupFrequency frequency)
	{
		return frequency.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Returns the JSON representation of a DropletFeature.
	 *
	 * @return the JSON representation
	 */
	public String getBackupFrequencyAsJson(DropletFeature feature)
	{
		return feature.name().toLowerCase(Locale.ROOT);
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
//		md.update(intToBytes.putInt(region.ordinal()).array());
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
		return new ToStringBuilder(DropletCreator.class).
			add("name", name).
			add("typeId", typeId).
			add("imageId", imageId).
			add("region", regionId).
			add("vpcId", vpcId).
			add("features", features).
			add("tags", tags).
			add("sshKeys", sshKeys).
			add("userData", userData).
			toString();
	}
}