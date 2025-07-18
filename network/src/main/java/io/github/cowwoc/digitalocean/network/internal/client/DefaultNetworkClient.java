package io.github.cowwoc.digitalocean.network.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cowwoc.digitalocean.core.internal.client.AbstractInternalClient;
import io.github.cowwoc.digitalocean.network.client.NetworkClient;
import io.github.cowwoc.digitalocean.network.internal.resource.NetworkParser;
import io.github.cowwoc.digitalocean.network.resource.Vpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

public class DefaultNetworkClient extends AbstractInternalClient
	implements NetworkClient
{
	private final NetworkParser parser = new NetworkParser(this);

	/**
	 * Creates a new DefaultNetworkClient.
	 */
	public DefaultNetworkClient()
	{
	}

	/**
	 * Returns the parser.
	 *
	 * @return the parser
	 */
	public NetworkParser getParser()
	{
		return parser;
	}

	@Override
	public List<Vpc> getVpcs() throws IOException, InterruptedException
	{
		return getVpcs(_ -> true);
	}

	@Override
	public List<Vpc> getVpcs(Predicate<Vpc> predicate) throws IOException, InterruptedException
	{
		requireThat(predicate, "predicate").isNotNull();

		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/VPCs/operation/vpcs_list
		return getElements(REST_SERVER.resolve("v2/vpcs"), Map.of(), body ->
		{
			List<Vpc> defaultVpcs = new ArrayList<>();
			for (JsonNode sshKey : body.get("vpcs"))
			{
				Vpc candidate = parser.vpcFromServer(sshKey);
				if (predicate.test(candidate))
					defaultVpcs.add(candidate);
			}
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
				Vpc candidate = parser.vpcFromServer(vpcNode);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	@Override
	public Vpc getVpc(Vpc.Id id) throws IOException, InterruptedException
	{
		requireThat(id, "id").isNotNull();

		// https://docs.digitalocean.com/reference/api/digitalocean/#tag/VPCs/operation/vpcs_get
		return getResource(REST_SERVER.resolve("v2/vpcs/" + id), body ->
		{
			JsonNode vpc = body.get("vpc");
			return parser.vpcFromServer(vpc);
		});
	}
}