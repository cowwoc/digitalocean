package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * The hostname and port of a public endpoint.
 *
 * @param hostname the hostname
 * @param port     the port
 */
public record Endpoint(String hostname, int port)
{
	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param client the client configuration
	 * @param json   the JSON representation
	 * @return the Endpoint
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	static Endpoint getByJson(DigitalOceanClient client, JsonNode json)
	{
		String hostname = json.get("host").textValue();
		int port = client.getInt(json, "port");
		return new Endpoint(hostname, port);
	}

	/**
	 * Creates a new endpoint.
	 *
	 * @param hostname the hostname
	 * @param port     the port
	 * @throws NullPointerException     if {@code hostname} is null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code hostname} contains whitespace or is empty.</li>
	 *                                    <li>{@code port} is negative or zero.</li>
	 *                                  </ul>
	 */
	public Endpoint
	{
		requireThat(hostname, "hostname").doesNotContainWhitespace().isNotEmpty();
		requireThat(port, "port").isPositive();
	}
}