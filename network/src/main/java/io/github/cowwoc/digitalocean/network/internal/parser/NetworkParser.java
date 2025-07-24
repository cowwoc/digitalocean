package io.github.cowwoc.digitalocean.network.internal.parser;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.id.VpcId;
import io.github.cowwoc.digitalocean.core.internal.parser.AbstractParser;
import io.github.cowwoc.digitalocean.core.internal.parser.CoreParser;
import io.github.cowwoc.digitalocean.network.internal.client.DefaultNetworkClient;
import io.github.cowwoc.digitalocean.network.internal.resource.DefaultVpc;
import io.github.cowwoc.digitalocean.network.resource.Vpc;

/**
 * Parses server responses.
 */
public final class NetworkParser extends AbstractParser
{
	public NetworkParser(DefaultNetworkClient client)
	{
		super(client);
	}

	@Override
	protected DefaultNetworkClient getClient()
	{
		return (DefaultNetworkClient) super.getClient();
	}

	/**
	 * Converts VPC from its server representation.
	 *
	 * @param json the server representation
	 * @return the VPC
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public Vpc vpcFromServer(JsonNode json)
	{
		CoreParser coreParser = getClient().getCoreParser();
		VpcId id = coreParser.vpcIdFromServer(json.get("id"));
		RegionId region = coreParser.regionIdFromServer(json.get("region"));
		return new DefaultVpc(id, region);
	}
}