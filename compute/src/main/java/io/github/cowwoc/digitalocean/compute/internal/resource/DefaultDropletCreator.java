package io.github.cowwoc.digitalocean.compute.internal.resource;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cowwoc.digitalocean.compute.internal.client.DefaultComputeClient;
import io.github.cowwoc.digitalocean.compute.internal.parser.ComputeParser;
import io.github.cowwoc.digitalocean.compute.resource.Droplet;
import io.github.cowwoc.digitalocean.compute.resource.DropletCreator;
import io.github.cowwoc.digitalocean.compute.resource.DropletFeature;
import io.github.cowwoc.digitalocean.compute.resource.SshPublicKey;
import io.github.cowwoc.digitalocean.core.id.ComputeDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.DropletImageId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.id.VpcId;
import io.github.cowwoc.digitalocean.core.internal.parser.CoreParser;
import io.github.cowwoc.digitalocean.core.internal.util.Strings;
import io.github.cowwoc.digitalocean.core.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.IOException;
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
	private final ComputeDropletTypeId typeId;
	private final DropletImageId imageId;
	private RegionId regionId;
	private final Set<SshPublicKey> sshKeys = new LinkedHashSet<>();
	private final Set<DropletFeature> features = EnumSet.of(MONITORING, PRIVATE_NETWORKING);
	private final Set<String> tags = new LinkedHashSet<>();
	private VpcId vpcId;
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
	public DefaultDropletCreator(DefaultComputeClient client, String name, ComputeDropletTypeId typeId,
		DropletImageId imageId)
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
	public DropletCreator vpcId(VpcId vpcId)
	{
		this.vpcId = vpcId;
		return this;
	}

	@Override
	public DropletCreator regionId(RegionId region)
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
	public Droplet apply() throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/droplets_create
		JsonMapper jm = client.getJsonMapper();
		ObjectNode requestBody = jm.createObjectNode().
			put("name", name).
			put("size", typeId.toString()).
			put("image", imageId.getValue());
		CoreParser coreParser = client.getCoreParser();
		if (regionId != null)
			requestBody.put("region", coreParser.regionIdToServer(regionId));
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
		ComputeParser computeParser = client.getComputeParser();
		for (DropletFeature feature : features)
		{
			String name = computeParser.dropletFeatureToServer(feature);
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
		return computeParser.createDroplet(typeId, imageId, request, serverResponse);
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
		ComputeParser computeParser = client.getComputeParser();
		json.put("plan", computeParser.backupFrequencyToServer(schedule.frequency()));
		return json;
	}

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