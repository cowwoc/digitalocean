package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Base64;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Credentials for a docker registry.
 *
 * @param username the username
 * @param password the password
 */
public record DockerCredentials(String username, String password)
{
	/**
	 * Creates a new instance.
	 *
	 * @param username the username
	 * @param password the password
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	public DockerCredentials
	{
		requireThat(username, "username").isStripped().isNotEmpty();
		requireThat(password, "password").isStripped().isNotEmpty();
	}

	/**
	 * Returns the base64 encoded representation of the credentials.
	 *
	 * @param jsonMapper a {@code JsonMapper}
	 * @return the base64 encoded representation
	 */
	public String asBase64Encoded(JsonMapper jsonMapper)
	{
		ObjectNode auth = jsonMapper.createObjectNode();
		auth.put("username", username);
		auth.put("password", password);
		return Base64.getEncoder().encodeToString(auth.toString().getBytes(UTF_8));
	}
}