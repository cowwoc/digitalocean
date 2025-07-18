package io.github.cowwoc.digitalocean.compute.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cowwoc.digitalocean.compute.client.ComputeClient;
import io.github.cowwoc.digitalocean.compute.internal.resource.ComputeParser;
import io.github.cowwoc.digitalocean.compute.internal.resource.DefaultDropletCreator;
import io.github.cowwoc.digitalocean.compute.internal.resource.DefaultSshPublicKey;
import io.github.cowwoc.digitalocean.compute.internal.util.SshKeys;
import io.github.cowwoc.digitalocean.compute.resource.ComputeRegion;
import io.github.cowwoc.digitalocean.compute.resource.Droplet;
import io.github.cowwoc.digitalocean.compute.resource.DropletCreator;
import io.github.cowwoc.digitalocean.compute.resource.DropletImage;
import io.github.cowwoc.digitalocean.compute.resource.DropletType;
import io.github.cowwoc.digitalocean.compute.resource.SshPublicKey;
import io.github.cowwoc.digitalocean.core.internal.client.AbstractInternalClient;
import io.github.cowwoc.digitalocean.network.internal.resource.NetworkParser;
import io.github.cowwoc.digitalocean.network.resource.Region;
import io.github.cowwoc.digitalocean.network.resource.Region.Id;
import io.github.cowwoc.digitalocean.network.resource.Vpc;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.that;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

public class DefaultComputeClient extends AbstractInternalClient
	implements ComputeClient
{
	private static final String DROPLET_METADATA = "http://169.254.169.254";
	@SuppressWarnings("this-escape")
	private final ComputeParser computeParser = new ComputeParser(this);
	@SuppressWarnings("this-escape")
	private final NetworkParser networkParser = new NetworkParser(this);

	/**
	 * Returns a {@code ComputeParser}.
	 *
	 * @return the parser
	 */
	public ComputeParser getComputeParser()
	{
		return computeParser;
	}

	/**
	 * Returns a {@code NetworkParser}.
	 *
	 * @return the parser
	 */
	public NetworkParser getNetworkParser()
	{
		return networkParser;
	}

	@Override
	public Vpc getDefaultVpc(Region.Id regionId) throws IOException, InterruptedException
	{
		requireThat(regionId, "regionId").isNotNull();

		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/VPCs/operation/vpcs_list
		return getElement(REST_SERVER.resolve("v2/vpcs"), Map.of(), body ->
		{
			for (JsonNode vpc : body.get("vpcs"))
			{
				boolean isDefault = computeParser.getBoolean(vpc, "default");
				if (!isDefault)
					continue;
				Region.Id actualRegion = networkParser.regionIdFromServer(vpc.get("region"));
				if (actualRegion.equals(regionId))
					return networkParser.vpcFromServer(vpc);
			}
			return null;
		});
	}

	@Override
	public List<ComputeRegion> getRegions() throws IOException, InterruptedException
	{
		return getRegions(true);
	}

	@Override
	public List<ComputeRegion> getRegions(boolean canCreateDroplets) throws IOException, InterruptedException
	{
		return getRegions(region -> !canCreateDroplets || region.canCreateDroplets());
	}

	@Override
	public List<ComputeRegion> getRegions(Predicate<ComputeRegion> predicate)
		throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Regions/operation/regions_list
		return getElements(REST_SERVER.resolve("v2/regions"), Map.of(), body ->
		{
			List<ComputeRegion> regions = new ArrayList<>();
			for (JsonNode region : body.get("regions"))
			{
				ComputeRegion.Id candidateId = networkParser.regionIdFromServer(region);
				ComputeRegion candidate = getRegion(candidateId);
				if (predicate.test(candidate))
					regions.add(candidate);
			}
			return regions;
		});
	}

	@Override
	public ComputeRegion getRegion(
		Predicate<ComputeRegion> predicate) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Regions/operation/regions_list
		return getElement(REST_SERVER.resolve("v2/regions"), Map.of(), body ->
		{
			for (JsonNode regionNode : body.get("regions"))
			{
				ComputeRegion candidate = computeParser.regionFromServer(regionNode);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	@Override
	public ComputeRegion getRegion(Id id) throws IOException, InterruptedException
	{
		return getRegion(region -> region.getId().equals(id));
	}

	@Override
	public List<DropletType> getDropletTypes() throws IOException, InterruptedException
	{
		return getDropletTypes(true);
	}

	@Override
	public List<DropletType> getDropletTypes(boolean canCreateDroplets) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/sizes_list
		return getElements(REST_SERVER.resolve("v2/sizes"), Map.of(), body ->
		{
			List<DropletType> types = new ArrayList<>();
			for (JsonNode typeNode : body.get("sizes"))
			{
				DropletType candidate = computeParser.dropletTypeFromServer(typeNode);
				if (candidate.isAvailable() || !canCreateDroplets)
					types.add(candidate);
			}
			return types;
		});
	}

	@Override
	public DropletType getDropletType(Predicate<DropletType> predicate) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/sizes_list
		return getElement(REST_SERVER.resolve("v2/sizes"), Map.of(), body ->
		{
			for (JsonNode typeNode : body.get("sizes"))
			{
				DropletType candidate = computeParser.dropletTypeFromServer(typeNode);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	@Override
	public Droplet getDroplet(Droplet.Id id) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/droplets_get
		return getResource(REST_SERVER.resolve("v2/droplets/" + id.getValue()), body ->
		{
			JsonNode droplet = body.get("droplet");
			return computeParser.dropletFromServer(droplet);
		});
	}

	@Override
	public Droplet getDroplet(Predicate<Droplet> predicate) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/droplets_list
		return getElement(REST_SERVER.resolve("v2/droplets"), Map.of(), body ->
		{
			for (JsonNode droplet : body.get("droplets"))
			{
				Droplet candidate = computeParser.dropletFromServer(droplet);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	@Override
	public List<Droplet> getDroplets() throws IOException, InterruptedException
	{
		return getDroplets(_ -> true);
	}

	@Override
	public List<Droplet> getDroplets(Predicate<Droplet> predicate) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Droplets/operation/droplets_list
		return getElements(REST_SERVER.resolve("v2/droplets"), Map.of(), body ->
		{
			List<Droplet> droplets = new ArrayList<>();
			for (JsonNode droplet : body.get("droplets"))
			{
				Droplet candidate = computeParser.dropletFromServer(droplet);
				if (predicate.test(candidate))
					droplets.add(candidate);
			}
			return droplets;
		});
	}

	@Override
	public DropletImage getDropletImage(DropletImage.Id id)
		throws IOException, InterruptedException
	{
		return getDropletImageImpl(String.valueOf(id.getValue()));
	}

	@Override
	public DropletImage getDropletImage(String slug) throws IOException, InterruptedException
	{
		return getDropletImageImpl(slug);
	}

	private DropletImage getDropletImageImpl(String idOrSlug) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Images/operation/images_get
		return getElement(REST_SERVER.resolve("v2/images/" + idOrSlug), Map.of(), body ->
		{
			JsonNode droplet = body.get("image");
			return computeParser.dropletImageFromServer(droplet);
		});
	}

	@Override
	public DropletImage getDropletImage(Predicate<DropletImage> predicate)
		throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Images/operation/images_list
		return getElement(REST_SERVER.resolve("v2/images"), Map.of(), body ->
		{
			for (JsonNode droplet : body.get("images"))
			{
				DropletImage candidate = computeParser.dropletImageFromServer(droplet);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	@Override
	public List<DropletImage> getDropletImages() throws IOException, InterruptedException
	{
		return getDropletImages(_ -> true);
	}

	@Override
	public List<DropletImage> getDropletImages(Predicate<DropletImage> predicate)
		throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/Images/operation/images_list
		return getElement(REST_SERVER.resolve("v2/images"), Map.of(), body ->
		{
			List<DropletImage> dropletImages = new ArrayList<>();
			for (JsonNode droplet : body.get("images"))
			{
				DropletImage candidate = computeParser.dropletImageFromServer(droplet);
				if (predicate.test(candidate))
					dropletImages.add(candidate);
			}
			return dropletImages;
		});
	}

	@Override
	public DropletCreator createDroplet(String name, DropletType.Id typeId, DropletImage.Id imageId)
	{
		return new DefaultDropletCreator(this, name, typeId, imageId);
	}

	@Override
	public List<SshPublicKey> getSshPublicKeys() throws IOException, InterruptedException
	{
		return getSshPublicKeys(_ -> true);
	}

	@Override
	public List<SshPublicKey> getSshPublicKeys(
		Predicate<SshPublicKey> predicate) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/SSH-Keys/operation/sshKeys_list
		return getElements(REST_SERVER.resolve("v2/account/keys"), Map.of(), body ->
		{
			List<SshPublicKey> keys = new ArrayList<>();
			for (JsonNode sshKey : body.get("ssh_keys"))
			{
				SshPublicKey candidate = computeParser.sshPublicKeyFromServer(sshKey);
				if (predicate.test(candidate))
					keys.add(candidate);
			}
			return keys;
		});
	}

	@Override
	public SshPublicKey getSshPublicKey(Predicate<SshPublicKey> predicate)
		throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/SSH-Keys/operation/projects_list
		return getElement(REST_SERVER.resolve("v2/account/keys"), Map.of(), body ->
		{
			for (JsonNode sshKey : body.get("ssh_keys"))
			{
				SshPublicKey candidate = computeParser.sshPublicKeyFromServer(sshKey);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	@Override
	public SshPublicKey getSshPublicKey(SshPublicKey.Id id) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/SSH-Keys/operation/sshKeys_get
		return getResource(REST_SERVER.resolve("v2/account/keys/" + id.getValue()), body ->
		{
			JsonNode key = body.get("ssh_key");
			return computeParser.sshPublicKeyFromServer(key);
		});
	}

	@Override
	public SshPublicKey getSshPublicKeyByFingerprint(String fingerprint)
		throws IOException, InterruptedException
	{
		requireThat(fingerprint, "fingerprint").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/SSH-Keys/operation/sshKeys_get
		return getResource(REST_SERVER.resolve("v2/account/keys/" + fingerprint),
			body ->
			{
				JsonNode key = body.get("ssh_key");
				return computeParser.sshPublicKeyFromServer(key);
			});
	}

	@Override
	public SshPublicKey createSshPublicKey(String name, PublicKey value)
		throws GeneralSecurityException, IOException, InterruptedException
	{
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(value, "value").isNotNull();

		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/SSH-Keys/operation/sshKeys_create
		SshKeys sshKeys = new SshKeys();
		String openSshRepresentation;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream())
		{
			sshKeys.writePublicKeyAsOpenSsh(value, name, out);
			openSshRepresentation = out.toString();
		}
		catch (IOException | GeneralSecurityException e)
		{
			// Exception never thrown by StringWriter
			throw new AssertionError(e);
		}

		ObjectNode requestBody = getJsonMapper().createObjectNode().
			put("name", name).
			put("public_key", openSshRepresentation);

		Request request = createRequest(REST_SERVER.resolve("v2/account/keys"), requestBody).
			method(POST);
		Response serverResponse = send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " + toString(serverResponse) + "\n" +
				"Request: " + toString(request));
		}
		ContentResponse contentResponse = (ContentResponse) serverResponse;
		JsonNode body = getResponseBody(contentResponse);
		JsonNode responseId = body.get("id");
		if (responseId != null && responseId.textValue().equals("unprocessable_entity"))
		{
			String message = body.get("message").textValue();
			if (message.equals("SSH Key is already in use on your account"))
				throw new IllegalArgumentException("An SSH key with the same fingerprint is already registered");
			throw new AssertionError(message);
		}
		JsonNode sshKeyNode = body.get("ssh_key");
		SshPublicKey.Id id = SshPublicKey.id(computeParser.getInt(sshKeyNode, "id"));

		String actualName = sshKeyNode.get("name").textValue();
		assert that(actualName, "actualName").isEqualTo(name, "name").elseThrow();

		MessageDigest md5 = MessageDigest.getInstance("MD5");
		String fingerprint = getFingerprint(value, md5);
		return new DefaultSshPublicKey(this, id, name, fingerprint);
	}

	/**
	 * Returns the fingerprint of a key.
	 *
	 * @param value  a key
	 * @param digest the digest to use for generating the fingerprint
	 * @return the fingerprint of the key
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws GeneralSecurityException if the key is unsupported or invalid
	 */
	private static String getFingerprint(PublicKey value, MessageDigest digest) throws GeneralSecurityException
	{
		SshKeys sshKeys = new SshKeys();
		String fingerprint;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream())
		{
			sshKeys.writeFingerprint(value, digest, out);
			fingerprint = out.toString();
		}
		catch (IOException e)
		{
			// Exception never thrown by ByteArrayOutputStream
			throw new AssertionError(e);
		}
		// DigitalOcean does not include the bit-length and hash type in their fingerprints.
		// Given "256 MD5:[rest of fingerprint]", we only want to return "[rest of fingerprint]".
		int colon = fingerprint.indexOf(':');
		assert that(colon, "colon").isNotNegative().elseThrow();
		return fingerprint.substring(colon + 1);
	}

	@Override
	public Integer getCurrentDropletId() throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/metadata-api/#operation/getDropletId
		String value = getMetadataValue(URI.create(DROPLET_METADATA + "/metadata/v1/id"));
		if (value == null)
			return null;
		return Integer.parseInt(value);
	}

	@Override
	public String getCurrentDropletHostname() throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/metadata-api/#operation/getHostname
		return getMetadataValue(URI.create(DROPLET_METADATA + "/metadata/v1/hostname"));
	}

	@Override
	public String getCurrentDropletRegion() throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/metadata-api/#operation/getRegion
		return getMetadataValue(URI.create(DROPLET_METADATA + "/metadata/v1/region"));
	}

	/**
	 * Returns a metadata value.
	 *
	 * @param uri the URI of the REST endpoint
	 * @return null when running outside a droplet
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code uri} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	private String getMetadataValue(URI uri) throws IOException, InterruptedException
	{
		// Reduce the timeout since the server is expected to respond quickly. Additionally, we want to timeout
		// swiftly if the service is unavailable when running outside a droplet.
		Request request = createRequest(uri).
			timeout(1, TimeUnit.SECONDS).
			method(GET);
		Response serverResponse = send(request);
		if (serverResponse.getStatus() != OK_200)
			return null;
		ContentResponse contentResponse = (ContentResponse) serverResponse;
		return contentResponse.getContentAsString();
	}
}