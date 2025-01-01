package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import com.github.cowwoc.digitalocean.internal.util.DigitalOceans;
import com.github.cowwoc.digitalocean.internal.util.SshKeys;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import com.github.cowwoc.digitalocean.util.CreateResult;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.digitalocean.internal.util.DigitalOceans.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;
import static org.eclipse.jetty.http.HttpMethod.DELETE;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;

/**
 * An SSH public key that is registered with the account or team where they were created.
 */
public final class SshPublicKey
{
	/**
	 * Looks up an SSH key by its ID.
	 *
	 * @param client the client configuration
	 * @param id     the ID of the public key
	 * @return null if no match was found
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static SshPublicKey getById(DigitalOceanClient client, int id)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/sshKeys_list
		String uri = REST_SERVER + "/v2/account/keys";
		return DigitalOceans.getElement(client, uri, Map.of(), body ->
		{
			JsonNode sshKeys = body.get("ssh_keys");
			if (sshKeys == null)
				throw new JsonMappingException(null, "ssh_keys must be set");
			for (JsonNode sshKey : sshKeys)
			{
				int actualId = DigitalOceans.toInt(sshKey, "id");
				if (actualId == id)
					return getByJson(client, sshKey);
			}
			return null;
		});
	}

	/**
	 * Returns all SSH keys.
	 *
	 * @param client the client configuration
	 * @return the list of all SSH keys
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static List<SshPublicKey> getAll(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/sshKeys_list
		String uri = REST_SERVER + "/v2/account/keys";
		return DigitalOceans.getElements(client, uri, Map.of(), body ->
		{
			JsonNode sshKeys = body.get("ssh_keys");
			if (sshKeys == null)
				throw new JsonMappingException(null, "ssh_keys must be set");
			List<SshPublicKey> keys = new ArrayList<>();
			for (JsonNode sshKey : sshKeys)
				keys.add(getByJson(client, sshKey));
			return keys;
		});
	}

	/**
	 * Looks up SSH keys by their name.
	 *
	 * @param client the client configuration
	 * @param name   the name of the public key
	 * @return an empty list if no match was found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static List<SshPublicKey> getByName(DigitalOceanClient client, String name)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(name, "name").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/sshKeys_list
		String uri = REST_SERVER + "/v2/account/keys";
		return DigitalOceans.getElements(client, uri, Map.of(), body ->
		{
			JsonNode sshKeys = body.get("ssh_keys");
			if (sshKeys == null)
				throw new JsonMappingException(null, "ssh_keys must be set");
			List<SshPublicKey> matches = new ArrayList<>();
			for (JsonNode sshKey : sshKeys)
			{
				String actualName = sshKey.get("name").textValue();
				if (actualName.equals(name))
					matches.add(getByJson(client, sshKey));
			}
			return matches;
		});
	}

	/**
	 * Looks up an SSH key by its fingerprint.
	 *
	 * @param client      the client configuration
	 * @param fingerprint the fingerprint of the public key
	 * @return null if no match was found
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code fingerprint} contains leading or trailing whitespace or is
	 *                                    empty.</li>
	 *                                  </ul>
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code fingerprint} contains leading or trailing whitespace or is
	 *                                  empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static SshPublicKey getByFingerprint(DigitalOceanClient client, String fingerprint)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(fingerprint, "fingerprint").isStripped().isNotEmpty();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/sshKeys_list
		String uri = REST_SERVER + "/v2/account/keys";
		return DigitalOceans.getElement(client, uri, Map.of(), body ->
		{
			JsonNode sshKeys = body.get("ssh_keys");
			if (sshKeys == null)
				throw new JsonMappingException(null, "ssh_keys must be set");
			for (JsonNode sshKey : sshKeys)
			{
				String actualFingerprint = sshKey.get("fingerprint").textValue();
				if (actualFingerprint.equals(fingerprint))
					return getByJson(client, sshKey);
			}
			return null;
		});
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

	/**
	 * Returns an existing public key with the same fingerprint as the provided key, or creates a new public key
	 * if no matching key exists.
	 * <p>
	 * <b>WARNING</b>: Any keys with the same name but a different state will be deleted before the new
	 * key is created.
	 *
	 * @param client the client configuration
	 * @param name   the name of the public key
	 * @param value  the public key
	 * @return the new or existing public key
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace or
	 *                                    are empty.</li>
	 *                                    <li>another SSH key with the same fingerprint already exists.</li>
	 *                                  </ul>
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws GeneralSecurityException if the key is unsupported or invalid
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static CreateResult<SshPublicKey> getOrCreate(DigitalOceanClient client, String name,
		PublicKey value) throws GeneralSecurityException, IOException, TimeoutException, InterruptedException
	{
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(value, "value").isNotNull();

		MessageDigest md5 = MessageDigest.getInstance("MD5");
		String fingerprint = getFingerprint(value, md5);

		SshPublicKey existingKey = null;
		List<SshPublicKey> keys = getByName(client, name);
		List<SshPublicKey> keysToDelete = new ArrayList<>();
		for (SshPublicKey key : keys)
		{
			if (key.getFingerprint().equals(fingerprint))
				existingKey = key;
			else
				keysToDelete.add(key);
		}
		keys.removeAll(keysToDelete);
		for (SshPublicKey key : keysToDelete)
			key.destroy();

		if (existingKey == null)
			existingKey = getByFingerprint(client, fingerprint);
		if (existingKey == null)
		{
			SshPublicKey sshKey = create(client, name, value);
			return CreateResult.created(sshKey);
		}
		return CreateResult.conflictedWith(existingKey);
	}

	/**
	 * Creates a new SSH key.
	 *
	 * @param client the client configuration
	 * @param name   the name of the public key
	 * @param value  the value of the public key
	 * @return a new public key
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace or
	 *                                    are empty.</li>
	 *                                    <li>another SSH key with the same fingerprint already exists.</li>
	 *                                  </ul>
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws GeneralSecurityException if the key is unsupported or invalid
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static SshPublicKey create(DigitalOceanClient client, String name, PublicKey value)
		throws GeneralSecurityException, IOException, TimeoutException, InterruptedException
	{
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(value, "value").isNotNull();

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/sshKeys_create
		ObjectMapper om = client.getObjectMapper();
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

		ObjectNode requestBody = om.createObjectNode().
			put("name", name).
			put("public_key", openSshRepresentation);

		String uri = REST_SERVER + "/v2/account/keys";
		Request request = DigitalOceans.createRequest(client, uri, requestBody).
			method(POST);
		ClientRequests clientRequests = client.getClientRequests();
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				// success
			}
			default -> throw new AssertionError(
				"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
					"Request: " + clientRequests.toString(request));
		}
		JsonNode body = DigitalOceans.getResponseBody(client, serverResponse);
		JsonNode responseId = body.get("id");
		if (responseId != null && responseId.textValue().equals("unprocessable_entity"))
		{
			String message = body.get("message").textValue();
			if (message.equals("SSH Key is already in use on your account"))
				throw new IllegalArgumentException("An SSH key with the same fingerprint is already registered");
			throw new AssertionError(message);
		}
		JsonNode sshKeyNode = body.get("ssh_key");
		int id = DigitalOceans.toInt(sshKeyNode, "id");

		String actualName = sshKeyNode.get("name").textValue();
		assert that(actualName, "actualName").isEqualTo(name, "name").elseThrow();

		MessageDigest md5 = MessageDigest.getInstance("MD5");
		String fingerprint = getFingerprint(value, md5);
		return new SshPublicKey(client, id, name, fingerprint);
	}

	/**
	 * @param client the client configuration
	 * @param json   the JSON representation of the key
	 * @return the key
	 * @throws NullPointerException if any of the arguments are null
	 */
	private static SshPublicKey getByJson(DigitalOceanClient client, JsonNode json)
	{
		int id = DigitalOceans.toInt(json, "id");
		String name = json.get("name").textValue();
		String fingerprint = json.get("fingerprint").textValue();
		return new SshPublicKey(client, id, name, fingerprint);
	}

	private final DigitalOceanClient client;
	private final int id;
	private final String name;
	private final String fingerprint;

	/**
	 * Creates a new public key.
	 *
	 * @param client      the client configuration
	 * @param id          the ID of the key
	 * @param name        the name of the key
	 * @param fingerprint the fingerprint of the key
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	private SshPublicKey(DigitalOceanClient client, int id, String name, String fingerprint)
	{
		requireThat(client, "client").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(fingerprint, "fingerprint").isStripped().isNotEmpty();
		this.client = client;
		this.id = id;
		this.name = name;
		this.fingerprint = fingerprint;
	}

	/**
	 * Destroys the SSH key.
	 *
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public void destroy() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/sshKeys_delete
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		String uri = REST_SERVER + "/v2/account/keys/" + id;
		ClientRequests clientRequests = client.getClientRequests();
		Request request = httpClient.newRequest(uri).
			method(DELETE).
			headers(headers -> headers.put("Content-Type", "application/json").
				put("Authorization", "Bearer " + client.getAccessToken()));
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case NO_CONTENT_204, NOT_FOUND_404 ->
			{
				// success
			}
			default -> throw new AssertionError(
				"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
					"Request: " + clientRequests.toString(request));
		}
	}

	/**
	 * Returns the ID of the public key.
	 *
	 * @return the ID of the public key
	 */
	public int getId()
	{
		return id;
	}

	/**
	 * Returns the name of the public key.
	 *
	 * @return the name of the public key
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the fingerprint of the public key.
	 *
	 * @return the fingerprint of the public key
	 */
	public String getFingerprint()
	{
		return fingerprint;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof SshPublicKey other && other.id == id;
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(SshPublicKey.class).
			add("id", id).
			add("name", name).
			add("fingerprint", fingerprint).
			toString();
	}
}