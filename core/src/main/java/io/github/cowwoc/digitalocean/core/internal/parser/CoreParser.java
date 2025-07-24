package io.github.cowwoc.digitalocean.core.internal.parser;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cowwoc.digitalocean.core.client.Client;
import io.github.cowwoc.digitalocean.core.id.RegionId;
import io.github.cowwoc.digitalocean.core.id.VpcId;

public final class CoreParser extends AbstractParser
{
	/**
	 * Creates a new CoreParser.
	 *
	 * @param client the client configuration
	 */
	public CoreParser(Client client)
	{
		super(client);
	}

	/**
	 * Convert a RegionId from its server representation.
	 *
	 * @param json the JSON representation
	 * @return the ID of the region
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public RegionId regionIdFromServer(JsonNode json)
	{
		return regionIdFromServer(json.textValue());
	}

	/**
	 * Convert a RegionId from its string representation.
	 *
	 * @param value the String representation
	 * @return the ID of the region
	 * @throws NullPointerException     if {@code value} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public RegionId regionIdFromServer(String value)
	{
		return switch (value)
		{
			case "atl1" -> RegionId.ATLANTA1;
			case "nyc1" -> RegionId.NEW_YORK1;
			case "nyc2" -> RegionId.NEW_YORK2;
			case "nyc3" -> RegionId.NEW_YORK3;
			case "sfo1" -> RegionId.SAN_FRANCISCO1;
			case "sfo2" -> RegionId.SAN_FRANCISCO2;
			case "sfo3" -> RegionId.SAN_FRANCISCO3;
			case "ams2" -> RegionId.AMSTERDAM2;
			case "ams3" -> RegionId.AMSTERDAM3;
			case "sgp1" -> RegionId.SINGAPORE1;
			case "lon1" -> RegionId.LONDON1;
			case "fra1" -> RegionId.FRANCE1;
			case "tor1" -> RegionId.TORONTO1;
			case "blr1" -> RegionId.BANGALORE1;
			case "syd1" -> RegionId.SYDNEY1;
			default -> throw new IllegalArgumentException("Unsupported value: " + value);
		};
	}

	/**
	 * Convert a Region.Id to its server representation.
	 *
	 * @param value the ID of the region
	 * @return the server representation
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public String regionIdToServer(RegionId value)
	{
		return switch (value)
		{
			case ATLANTA1 -> "atl1";
			case NEW_YORK1 -> "nyc1";
			case NEW_YORK2 -> "nyc2";
			case NEW_YORK3 -> "nyc3";
			case SAN_FRANCISCO1 -> "sfo1";
			case SAN_FRANCISCO2 -> "sfo2";
			case SAN_FRANCISCO3 -> "sfo3";
			case AMSTERDAM2 -> "ams2";
			case AMSTERDAM3 -> "ams3";
			case SINGAPORE1 -> "sgp1";
			case LONDON1 -> "lon1";
			case FRANCE1 -> "fra1";
			case TORONTO1 -> "tor1";
			case BANGALORE1 -> "blr1";
			case SYDNEY1 -> "syd1";
		};
	}

	/**
	 * Converts a VPCId from its server representation.
	 *
	 * @param json the server representation
	 * @return the ID of the VPC
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	public VpcId vpcIdFromServer(JsonNode json)
	{
		return VpcId.of(json.textValue());
	}
}