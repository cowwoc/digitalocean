package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.exception.AccessDeniedException;
import com.github.cowwoc.digitalocean.internal.util.Strings;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.digitalocean.resource.DropletFeature.MONITORING;
import static com.github.cowwoc.digitalocean.resource.DropletFeature.PRIVATE_NETWORKING;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.ACCEPTED_202;
import static org.eclipse.jetty.http.HttpStatus.UNPROCESSABLE_ENTITY_422;

/**
 * Creates a new droplet.
 */
public final class DropletCreator
{
	private final DigitalOceanClient client;
	private final String name;
	private final DropletType.Id type;
	private final DropletImage image;
	private Zone.Id zone;
	private final Set<SshPublicKey> sshKeys = new LinkedHashSet<>();
	private final Set<DropletFeature> features = EnumSet.of(MONITORING, PRIVATE_NETWORKING);
	private final Set<String> tags = new LinkedHashSet<>();
	private Vpc.Id vpc;
	private String userData = "";
	private BackupSchedule backupSchedule;
	//	private Set<Volume> volumes;
	private boolean failOnUnsupportedOperatingSystem;

	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @param name   the name of the droplet. Names are case-insensitive.
	 * @param type   the machine type of the droplet
	 * @param image  the image ID of a public or private image or the slug identifier for a public image to use
	 *               to boot this droplet
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
	public DropletCreator(DigitalOceanClient client, String name, DropletType.Id type, DropletImage image)
	{
		requireThat(client, "client").isNotNull();
		// Taken from https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_create
		requireThat(name, "name").matches("^[a-zA-Z0-9]?[a-z0-9A-Z.\\-]*[a-z0-9A-Z]$");
		requireThat(type, "type").isNotNull();
		requireThat(image, "image").isNotNull();
		this.client = client;
		this.name = name;
		this.type = type;
		this.image = image;
	}

	/**
	 * Returns the name of the droplet.
	 *
	 * @return the name
	 */
	public String name()
	{
		return name;
	}

	/**
	 * Returns the machine type of the droplet.
	 *
	 * @return the machine type
	 */
	public DropletType.Id type()
	{
		return type;
	}

	/**
	 * Returns the image ID of a public or private image or the slug identifier for a public image to use to
	 * boot this droplet.
	 *
	 * @return the image
	 */
	public DropletImage image()
	{
		return image;
	}

	/**
	 * Returns the VPC to deploy the droplet in.
	 *
	 * @return {@code null} to deploy the droplet into the zone's
	 *  {@link Vpc#getDefault(DigitalOceanClient, Zone.Id) default} VPC
	 */
	public Vpc.Id vpc()
	{
		return vpc;
	}

	/***
	 * Sets the VPC to deploy the droplet in.
	 *
	 * @param vpc    {@code null} to deploy the droplet into the zone's
	 * {@link Vpc#getDefault(DigitalOceanClient, Zone.Id) default} VPC
	 * @return this
	 */
	public DropletCreator vpc(Vpc.Id vpc)
	{
		this.vpc = vpc;
		return this;
	}

	/**
	 * Sets the zone to create the droplet in.
	 *
	 * @param zone the zone to create the droplet in
	 * @return this
	 * @throws NullPointerException if {@code zone} is null
	 */
	public DropletCreator zone(Zone.Id zone)
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
	public Zone.Id zone()
	{
		return zone;
	}

	/**
	 * Adds an SSH key that may be used to connect to the droplet.
	 *
	 * @param key a public key
	 * @return this
	 */
	public DropletCreator sshKey(SshPublicKey key)
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
	 * Returns the Droplet's backup schedule.
	 *
	 * @return {@code null} if backups are disabled
	 */
	public BackupSchedule backupSchedule()
	{
		return backupSchedule;
	}

	/**
	 * Sets the Droplet's backup schedule.
	 *
	 * @param backupSchedule {@code null} to disable backups
	 * @return this
	 */
	public DropletCreator backupSchedule(BackupSchedule backupSchedule)
	{
		requireThat(backupSchedule, "backupSchedule").isNotNull();
		this.backupSchedule = backupSchedule;
		return this;
	}

	/**
	 * Enables a feature on the droplet.
	 *
	 * @param feature the feature to enable
	 * @return this
	 * @throws NullPointerException if {@code feature} is null
	 */
	public DropletCreator feature(DropletFeature feature)
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
	public DropletCreator features(Collection<DropletFeature> features)
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
	public DropletCreator tag(String tag)
	{
		// Discovered empirically: DigitalOcean drops all tags silently if any of them contain invalid characters.
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/tags_create
		requireThat(tag, "tag").matches("^[a-zA-Z0-9_\\-:]+$").length().isLessThanOrEqualTo(255);
		this.tags.add(tag);
		return this;
	}

	/**
	 * Sets the tags of the droplet.
	 *
	 * @param tags the tags
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
	public DropletCreator userData(String userData)
	{
		requireThat(userData, "userData").isStripped().isNotEmpty().length().isLessThanOrEqualTo(64 * 1024);
		this.userData = userData;
		return this;
	}

	/**
	 * Determines if the deployment should fail if the web console does not support the Droplet operating
	 * system. The default value is {@code false}.
	 *
	 * @param failOnUnsupportedOperatingSystem {@code true} if the deployment should fail on unsupported
	 *                                         operating systems
	 * @return this
	 */
	public DropletCreator failOnUnsupportedOperatingSystem(boolean failOnUnsupportedOperatingSystem)
	{
		this.failOnUnsupportedOperatingSystem = failOnUnsupportedOperatingSystem;
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
	 * @throws IllegalStateException if the client is closed
	 * @throws AccessDeniedException if the request exceeded the client's droplet limit
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Droplet create()
		throws AccessDeniedException, IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_create
		JsonMapper jm = client.getJsonMapper();
		ObjectNode requestBody = jm.createObjectNode().
			put("name", name).
			put("size", type.toString()).
			put("image", image.getId().getValue());
		if (zone != null)
			requestBody.put("region", zone.toString());
		if (!sshKeys.isEmpty())
		{
			ArrayNode sshKeysNode = requestBody.putArray("ssh_keys");
			for (SshPublicKey key : sshKeys)
				sshKeysNode.add(key.getId().getValue());
		}
		if (backupSchedule != null)
		{
			requestBody.put("backups", true);
			requestBody.set("backups_policy", backupSchedule.toJson());
		}
		if (vpc != null)
			requestBody.put("vpc_uuid", vpc.getValue());
		for (DropletFeature feature : features)
		{
			String name = feature.toJson();
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
		ContentResponse contentResponse = (ContentResponse) serverResponse;
		String responseAsString = contentResponse.getContentAsString();
		switch (serverResponse.getStatus())
		{
			case ACCEPTED_202 ->
			{
				// success
			}
			case UNPROCESSABLE_ENTITY_422 ->
			{
				// Example: creating this/these droplet(s) will exceed your droplet limit
				JsonNode json = client.getResponseBody(contentResponse);
				throw new AccessDeniedException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		JsonNode body = client.getJsonMapper().readTree(responseAsString);
		JsonNode dropletNode = body.get("droplet");
		if (dropletNode == null)
		{
			throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		return Droplet.getByJson(client, dropletNode);
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
		return new ToStringBuilder(DropletCreator.class).
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

	/**
	 * The schedule for when backup activities may be performed on the droplet.
	 */
	public static final class BackupSchedule
	{
		private final DigitalOceanClient client;
		private final OffsetTime hour;
		private final DayOfWeek day;
		private final BackupFrequency frequency;

		/**
		 * Creates a new schedule.
		 *
		 * @param client    the client configuration
		 * @param hour      the start hour when maintenance may take place
		 * @param day       (optional) the day of the week when maintenance may take place (ignored if
		 *                  {@code frequency} is daily})
		 * @param frequency determines how often backups take place
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code hour} contains a non-zero minute, second or nano component
		 */
		public BackupSchedule(DigitalOceanClient client, OffsetTime hour, DayOfWeek day,
			BackupFrequency frequency)
		{
			requireThat(client, "client").isNotNull();
			requireThat(hour, "hour").isNotNull();
			requireThat(hour.getMinute(), "hour.getMinute()").isZero();
			requireThat(hour.getSecond(), "hour.getSecond()").isZero();
			requireThat(hour.getNano(), "hour.getNano()").isZero();

			this.client = client;
			this.hour = hour;
			this.day = day;
			this.frequency = frequency;
		}

		/**
		 * Returns the JSON representation of this object.
		 *
		 * @return the JSON representation
		 * @throws IllegalStateException if the client is closed
		 */
		public ObjectNode toJson()
		{
			ObjectNode json = client.getJsonMapper().createObjectNode();
			OffsetTime hourAtUtc = hour.withOffsetSameInstant(ZoneOffset.UTC);
			json.put("hour", Strings.HOUR_MINUTE_SECOND.format(hourAtUtc));
			json.put("day", day.name().toLowerCase(Locale.ROOT));
			json.put("plan", frequency.toJson());
			return json;
		}

		/**
		 * Returns the start hour when maintenance may take place.
		 *
		 * @return the start hour
		 */
		public OffsetTime hour()
		{
			return hour;
		}

		/**
		 * Returns the day of the week when maintenance may take place.
		 *
		 * @return the day
		 */
		public DayOfWeek day()
		{
			return day;
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder(Kubernetes.MaintenanceSchedule.class).
				add("hour", hour).
				add("day", day).
				add("frequency", frequency).
				toString();
		}
	}

	/**
	 * Determines how often a backup is created.
	 */
	public enum BackupFrequency
	{
		/**
		 * Every day.
		 */
		DAILY,
		/**
		 * Every week.
		 */
		WEEKLY;

		/**
		 * Returns the version's JSON representation (slug).
		 *
		 * @return the JSON representation
		 */
		public String toJson()
		{
			return name().toLowerCase(Locale.ROOT);
		}
	}
}