package io.github.cowwoc.digitalocean.compute.internal.parser;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cowwoc.digitalocean.compute.internal.client.DefaultComputeClient;
import io.github.cowwoc.digitalocean.compute.internal.resource.DefaultComputeDropletType;
import io.github.cowwoc.digitalocean.compute.internal.resource.DefaultDroplet;
import io.github.cowwoc.digitalocean.compute.internal.resource.DefaultDropletImage;
import io.github.cowwoc.digitalocean.compute.internal.resource.DefaultSshPublicKey;
import io.github.cowwoc.digitalocean.compute.resource.ComputeDropletType;
import io.github.cowwoc.digitalocean.compute.resource.ComputeDropletType.DiskConfiguration;
import io.github.cowwoc.digitalocean.compute.resource.ComputeDropletType.GpuConfiguration;
import io.github.cowwoc.digitalocean.compute.resource.Droplet;
import io.github.cowwoc.digitalocean.compute.resource.DropletCreator.BackupFrequency;
import io.github.cowwoc.digitalocean.compute.resource.DropletFeature;
import io.github.cowwoc.digitalocean.compute.resource.DropletImage;
import io.github.cowwoc.digitalocean.compute.resource.SshPublicKey;
import io.github.cowwoc.digitalocean.core.exception.AccessDeniedException;
import io.github.cowwoc.digitalocean.core.id.ComputeDropletTypeId;
import io.github.cowwoc.digitalocean.core.id.DropletId;
import io.github.cowwoc.digitalocean.core.id.DropletImageId;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.id.SshPublicKeyId;
import io.github.cowwoc.digitalocean.core.id.VpcId;
import io.github.cowwoc.digitalocean.core.internal.parser.AbstractParser;
import io.github.cowwoc.digitalocean.core.internal.parser.CoreParser;
import io.github.cowwoc.digitalocean.network.internal.resource.DefaultRegion;
import io.github.cowwoc.digitalocean.network.resource.Region;
import io.github.cowwoc.digitalocean.network.resource.Region.Feature;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static io.github.cowwoc.digitalocean.compute.resource.DropletFeature.IPV6;
import static io.github.cowwoc.digitalocean.compute.resource.DropletFeature.MONITORING;
import static io.github.cowwoc.digitalocean.core.internal.client.AbstractInternalClient.TIB_TO_GIB;
import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpStatus.ACCEPTED_202;
import static org.eclipse.jetty.http.HttpStatus.UNPROCESSABLE_ENTITY_422;

/**
 * Parses server responses.
 */
public final class ComputeParser extends AbstractParser
{
	private static final String DISK_TOO_SMALL = "Cannot create a droplet with a smaller disk than the image.";
	private final CoreParser coreParser;

	/**
	 * Creates a ComputeParser.
	 *
	 * @param client the client configuration
	 */
	public ComputeParser(DefaultComputeClient client)
	{
		super(client);
		this.coreParser = new CoreParser(client);
	}

	@Override
	protected DefaultComputeClient getClient()
	{
		return (DefaultComputeClient) client;
	}

	/**
	 * Convert a Region from its server representation.
	 *
	 * @param json the JSON representation
	 * @return the region
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public Region regionFromServer(JsonNode json)
	{
		String name = json.get("name").textValue();
		RegionId id = coreParser.regionIdFromServer(json.get("slug"));
		try
		{
			Set<Feature> features = getElements(json, "features", this::regionFeatureFromServer);

			boolean canCreateDroplets = getBoolean(json, "available");
			Set<ComputeDropletTypeId> dropletTypes = getElements(json, "sizes", node ->
				ComputeDropletTypeId.of(node.textValue()));
			return new DefaultRegion(id, name, features, canCreateDroplets, dropletTypes);
		}
		catch (IOException | InterruptedException e)
		{
			// Exceptions never thrown by getRegionFeature() or DropletType.id()
			throw new AssertionError(e);
		}
	}

	/**
	 * Convert a Feature from its server representation.
	 *
	 * @param json the JSON representation
	 * @return the region feature
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public Feature regionFeatureFromServer(JsonNode json)
	{
		String name = json.textValue();
		requireThat(name, "name").isStripped().isNotEmpty();
		return Feature.valueOf(name.toUpperCase(Locale.ROOT));
	}

	/**
	 * Convert a DropletFeature to its server representation.
	 *
	 * @param feature the feature
	 * @return the server representation
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public String dropletFeatureToServer(DropletFeature feature)
	{
		return feature.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Convert a DiskConfiguration from its server representation.
	 *
	 * @param json the JSON representation
	 * @return the disk configuration
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public DiskConfiguration diskConfigurationFromServer(JsonNode json)
	{
		String type = json.get("type").textValue();
		boolean persistent = switch (type)
		{
			case "local" -> true;
			case "scratch" -> false;
			default -> throw new IllegalStateException("Unexpected value: " + type);
		};
		JsonNode sizeNode = json.get("size");
		int amount = getInt(sizeNode, "amount");
		String unit = sizeNode.get("unit").textValue();
		int sizeInMiB = switch (unit)
		{
			case "gib" -> amount * 1024;
			case "tib" -> amount * 1024 * 1024;
			default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
		};
		return new DiskConfiguration(persistent, sizeInMiB);
	}

	/**
	 * Convert a GpuConfiguration from its server representation.
	 *
	 * @param json the JSON representation
	 * @return the GPU configuration
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public GpuConfiguration gpuConfigurationFromServer(JsonNode json)
	{
		int count = getInt(json, "count");
		String model = json.get("model").textValue();
		JsonNode ramNode = json.get("vram");
		int amount = getInt(ramNode, "amount");
		String unit = ramNode.get("unit").textValue();
		int ramInMiB = switch (unit)
		{
			case "gib" -> amount * 1024;
			case "tib" -> amount * 1024 * 1024;
			default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
		};
		return new GpuConfiguration(count, model, ramInMiB);
	}

	/**
	 * Convert a ComputeDropletType from its server representation.
	 *
	 * @param json the JSON representation
	 * @return the droplet type
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public ComputeDropletType dropletTypeFromServer(JsonNode json)
	{
		try
		{
			ComputeDropletTypeId id = ComputeDropletTypeId.of(json.get("slug").textValue());
			int ramInMiB = getInt(json, "memory");
			int vCpus = getInt(json, "vcpus");
			int diskInGiB = getInt(json, "disk");
			BigDecimal transferInGiB = json.get("transfer").decimalValue().multiply(TIB_TO_GIB);
			BigDecimal costPerHour = json.get("price_hourly").decimalValue();
			BigDecimal costPerMonth = json.get("price_monthly").decimalValue();
			Set<RegionId> regions = getElements(json, "regions", coreParser::regionIdFromServer);
			boolean available = getBoolean(json, "available");
			String description = json.get("description").textValue();
			Set<DiskConfiguration> diskConfiguration = getElements(json, "disk_info",
				this::diskConfigurationFromServer);
			JsonNode gpuInfoNode = json.get("gpu_info");
			GpuConfiguration gpuConfiguration;
			if (gpuInfoNode == null)
				gpuConfiguration = null;
			else
				gpuConfiguration = gpuConfigurationFromServer(gpuInfoNode);
			return new DefaultComputeDropletType(id, ramInMiB, vCpus, diskInGiB, transferInGiB, costPerMonth,
				costPerHour, regions, available, description, diskConfiguration, gpuConfiguration);
		}
		catch (IOException | InterruptedException e)
		{
			// Exceptions never thrown by getRegionId() or getDiskConfiguration()
			throw new AssertionError(e);
		}
	}

	/**
	 * Convert a Droplet from its server representation.
	 *
	 * @param json the JSON representation
	 * @return the droplet
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public Droplet dropletFromServer(JsonNode json)
	{
		try
		{
			// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/droplets_get
			DropletId id = DropletId.of(getInt(json, "id"));
			String name = json.get("name").textValue();
			Instant createdAt = Instant.parse(json.get("created_at").textValue());
			ComputeDropletTypeId type = ComputeDropletTypeId.of(json.get("size_slug").textValue());

			DropletImage image = dropletImageFromServer(json.get("image"));
			JsonNode regionNode = json.get("region");
			RegionId regionId = coreParser.regionIdFromServer(regionNode.get("slug"));
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

			DefaultComputeClient client = getClient();
			JsonNode vpcNode = json.get("vpc_uuid");
			VpcId vpcId;
			if (vpcNode == null)
				vpcId = client.getDefaultVpcId(regionId);
			else
				vpcId = coreParser.vpcIdFromServer(json.get("vpc_uuid"));
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
			Set<String> tags = getElements(json, "tags", JsonNode::textValue);
			return new DefaultDroplet(client, id, name, type, image, regionId, vpcId, addresses, features,
				tags, createdAt);
		}
		catch (IOException | InterruptedException e)
		{
			// JsonNode::textValue never throws any exceptions
			throw new AssertionError(e);
		}
		catch (RuntimeException e)
		{
			log.warn("Response body: {}", json.toPrettyString(), e);
			throw e;
		}
	}

	/**
	 * Convert a DropletImage from its server representation.
	 *
	 * @param json the JSON representation
	 * @return the droplet image
	 * @throws NullPointerException if {@code json} is null
	 */
	public DropletImage dropletImageFromServer(JsonNode json)
	{
		try
		{
			DropletImageId id = DropletImageId.of(getInt(json, "id"));
			String name = json.get("name").textValue();
			String distribution = json.get("distribution").textValue();
			String slug = json.get("slug").textValue();
			boolean isPublic = getBoolean(json, "public");
			Set<RegionId> regions = getElements(json, "regions", coreParser::regionIdFromServer);
			DropletImage.Type type = dropletImageTypeFromServer(json.get("type"));
			int minDiskSizeInGiB = json.get("min_disk_size").intValue();
			float sizeInGiB = json.get("size_gigabytes").floatValue();
			String description = json.get("description").textValue();
			Set<String> tags = getElements(json, "tags", JsonNode::textValue);
			DropletImage.Status status = dropletImageStatusFromServer(json.get("status"));
			String errorMessage = getOptionalString(json, "error_message");
			Instant createdAt = Instant.parse(json.get("created_at").textValue());
			return new DefaultDropletImage(id, slug, name, distribution, isPublic, regions, type, minDiskSizeInGiB,
				sizeInGiB, description, tags, status, errorMessage, createdAt);
		}
		catch (IOException | InterruptedException e)
		{
			// Exceptions never thrown by getRegionId() or JsonNode::textValue
			throw new AssertionError(e);
		}
	}

	/**
	 * Convert a DropletImage.Type from its server representation.
	 *
	 * @param json the JSON representation
	 * @return the droplet image type
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public DropletImage.Type dropletImageTypeFromServer(JsonNode json)
	{
		return DefaultDropletImage.Type.valueOf(json.textValue().toUpperCase(Locale.ROOT));
	}

	/**
	 * Convert a DropletImage.Status from its server representation.
	 *
	 * @param json the JSON representation
	 * @return the droplet image status
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	public DropletImage.Status dropletImageStatusFromServer(JsonNode json)
	{
		return DefaultDropletImage.Status.valueOf(json.textValue().toUpperCase(Locale.ROOT));
	}

	/**
	 * Convert a BackupFrequency from its server representation.
	 *
	 * @param frequency the frequency
	 * @return the server representation
	 * @throws NullPointerException if {@code frequency} is null
	 */
	public String backupFrequencyToServer(BackupFrequency frequency)
	{
		return frequency.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Convert a SshPublicKey from its server representation.
	 *
	 * @param json the JSON representation
	 * @return the SSH public key
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public SshPublicKey sshPublicKeyFromServer(JsonNode json)
	{
		SshPublicKeyId id = SshPublicKeyId.of(getInt(json, "id"));
		String name = json.get("name").textValue();
		String fingerprint = json.get("fingerprint").textValue();
		return new DefaultSshPublicKey(getClient(), id, name, fingerprint);
	}

	/**
	 * Creates a new droplet.
	 *
	 * @param typeId   the droplet type
	 * @param imageId  the droplet image
	 * @param request  the client request
	 * @param response the server response
	 * @return the droplet
	 * @throws IllegalArgumentException if the droplet image is bigger than the droplet disk
	 * @throws AccessDeniedException    if the client does not have sufficient privileges to execute this
	 *                                  request
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public Droplet createDroplet(ComputeDropletTypeId typeId, DropletImageId imageId, Request request,
		Response response) throws IOException, InterruptedException
	{
		ContentResponse contentResponse = (ContentResponse) response;
		String responseAsString = contentResponse.getContentAsString();
		@SuppressWarnings("PMD.CloseResource")
		DefaultComputeClient client = getClient();
		switch (contentResponse.getStatus())
		{
			case ACCEPTED_202 ->
			{
				// success
			}
			case UNPROCESSABLE_ENTITY_422 ->
			{
				// Known variants: Cannot create a droplet with a smaller disk than the image.
				JsonNode json = client.getResponseBody(contentResponse);
				String message = json.get("message").textValue();
				if (message.equals(DISK_TOO_SMALL))
				{
					ComputeDropletType type = client.getDropletType(candidate -> candidate.getId().equals(typeId));
					DropletImage image = client.getDropletImage(imageId);
					throw new IllegalArgumentException(DISK_TOO_SMALL + "\n" +
						"DropletType.id   : " + type.getId() + "\n" +
						"DropletType.disk : " + type.getDiskInGiB() + " GB\n" +
						"DropletImage.id  : " + image.getId() + "\n" +
						"DropletImage.size: " + image.getMinDiskSizeInGiB() + " GB");
				}
				throw new AssertionError("Unexpected response: " + client.toString(response) + "\n" +
					"Request: " + client.toString(request));
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(response) + "\n" +
				"Request: " + client.toString(request));
		}
		JsonNode body = client.getJsonMapper().readTree(responseAsString);
		JsonNode dropletNode = body.get("droplet");
		if (dropletNode == null)
		{
			throw new AssertionError("Unexpected response: " + client.toString(response) + "\n" +
				"Request: " + client.toString(request));
		}
		return dropletFromServer(dropletNode);
	}
}