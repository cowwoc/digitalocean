package io.github.cowwoc.digitalocean.network.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cowwoc.digitalocean.core.id.VpcId;
import io.github.cowwoc.digitalocean.core.internal.client.AbstractInternalClient;
import io.github.cowwoc.digitalocean.core.internal.parser.CoreParser;
import io.github.cowwoc.digitalocean.network.client.NetworkClient;
import io.github.cowwoc.digitalocean.network.internal.parser.NetworkParser;
import io.github.cowwoc.digitalocean.network.resource.Vpc;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

public class DefaultNetworkClient extends AbstractInternalClient
	implements NetworkClient
{
	@SuppressWarnings("this-escape")
	private final CoreParser coreParser = new CoreParser(this);
	@SuppressWarnings("this-escape")
	private final NetworkParser networkParser = new NetworkParser(this);

	/**
	 * Creates a new DefaultNetworkClient.
	 */
	public DefaultNetworkClient()
	{
	}

	/**
	 * @return the core parser
	 */
	public CoreParser getCoreParser()
	{
		return coreParser;
	}

	/**
	 * @return the network parser
	 */
	public NetworkParser getNetworkParser()
	{
		return networkParser;
	}

	@Override
	public Set<Vpc> getVpcs() throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/VPCs/operation/vpcs_list
		return getElement(REST_SERVER.resolve("v2/vpcs"), Map.of(), body ->
		{
			Set<Vpc> defaultVpcs = new HashSet<>();
			for (JsonNode sshKey : body.get("vpcs"))
				defaultVpcs.add(networkParser.vpcFromServer(sshKey));
			return defaultVpcs;
		});
	}

	@Override
	public Vpc getVpc(Predicate<Vpc> predicate) throws IOException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/VPCs/operation/vpcs_list
		return getElement(REST_SERVER.resolve("v2/vpcs"), Map.of(), body ->
		{
			for (JsonNode vpcNode : body.get("vpcs"))
			{
				Vpc candidate = networkParser.vpcFromServer(vpcNode);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	@Override
	public Vpc getVpc(VpcId id) throws IOException, InterruptedException
	{
		requireThat(id, "id").isNotNull();

		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/VPCs/operation/vpcs_get
		return getResource(REST_SERVER.resolve("v2/vpcs/" + id), body ->
		{
			JsonNode vpc = body.get("vpc");
			return networkParser.vpcFromServer(vpc);
		});
	}
}