package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.id.IntegerId;
import com.github.cowwoc.digitalocean.internal.util.SshKeys;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;

/**
 * An SSH public key that is registered with the account or team where they were created.
 */
public final class SshPublicKey
{
	/**
	 * Returns all the SSH keys.
	 *
	 * @param client the client configuration
	 * @return an empty set if no match is found
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Set<SshPublicKey> getAll(DigitalOceanClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/sshKeys_list
		return client.getElements(REST_SERVER.resolve("v2/account/keys"), Map.of(), body ->
		{
			Set<SshPublicKey> keys = new HashSet<>();
			for (JsonNode sshKey : body.get("ssh_keys"))
				keys.add(getByJson(client, sshKey));
			return keys;
		});
	}

	/**
	 * Returns the first SSH key that matches a predicate.
	 *
	 * @param client    the client configuration
	 * @param predicate the predicate
	 * @return null if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static SshPublicKey getByPredicate(DigitalOceanClient client, Predicate<SshPublicKey> predicate)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/projects_list
		return client.getElement(REST_SERVER.resolve("v2/account/keys"), Map.of(), body ->
		{
			for (JsonNode sshKey : body.get("ssh_keys"))
			{
				SshPublicKey candidate = getByJson(client, sshKey);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

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
	public static SshPublicKey getById(DigitalOceanClient client, Id id)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/sshKeys_get
		return client.getResource(REST_SERVER.resolve("v2/account/keys/" + id.getValue()), body ->
		{
			JsonNode key = body.get("ssh_key");
			return getByJson(client, key);
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

		// https://docs.digitalocean.com/reference/api/api-reference/#operation/sshKeys_get
		return client.getResource(REST_SERVER.resolve("v2/account/keys/" + fingerprint),
			body ->
			{
				JsonNode key = body.get("ssh_key");
				return getByJson(client, key);
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
		JsonMapper jm = client.getJsonMapper();
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

		ObjectNode requestBody = jm.createObjectNode().
			put("name", name).
			put("public_key", openSshRepresentation);

		Request request = client.createRequest(REST_SERVER.resolve("v2/account/keys"), requestBody).
			method(POST);
		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		JsonNode body = client.getResponseBody(serverResponse);
		JsonNode responseId = body.get("id");
		if (responseId != null && responseId.textValue().equals("unprocessable_entity"))
		{
			String message = body.get("message").textValue();
			if (message.equals("SSH Key is already in use on your account"))
				throw new IllegalArgumentException("An SSH key with the same fingerprint is already registered");
			throw new AssertionError(message);
		}
		JsonNode sshKeyNode = body.get("ssh_key");
		Id id = id(client.getInt(sshKeyNode, "id"));

		String actualName = sshKeyNode.get("name").textValue();
		assert that(actualName, "actualName").isEqualTo(name, "name").elseThrow();

		MessageDigest md5 = MessageDigest.getInstance("MD5");
		String fingerprint = getFingerprint(value, md5);
		return new SshPublicKey(client, id, name, fingerprint);
	}

	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param client the client configuration
	 * @param json   the JSON representation
	 * @return the key
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 */
	private static SshPublicKey getByJson(DigitalOceanClient client, JsonNode json)
	{
		Id id = id(client.getInt(json, "id"));
		String name = json.get("name").textValue();
		String fingerprint = json.get("fingerprint").textValue();
		return new SshPublicKey(client, id, name, fingerprint);
	}

	/**
	 * Creates a new ID.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 */
	public static Id id(int value)
	{
		return new Id(value);
	}

	private final DigitalOceanClient client;
	private final Id id;
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
	private SshPublicKey(DigitalOceanClient client, Id id, String name, String fingerprint)
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
	 * Returns the ID of the public key.
	 *
	 * @return the ID
	 */
	public Id getId()
	{
		return id;
	}

	/**
	 * Returns the name of the public key.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the fingerprint of the public key.
	 *
	 * @return the fingerprint
	 */
	public String getFingerprint()
	{
		return fingerprint;
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
		client.destroyResource(REST_SERVER.resolve("v2/account/keys/" + id));
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

	/**
	 * A type-safe identifier for this type of resource.
	 * <p>
	 * This adds type-safety to API methods by ensuring that IDs specific to one class cannot be used in place
	 * of IDs belonging to another class.
	 */
	public static final class Id extends IntegerId
	{
		/**
		 * @param value a server-side identifier
		 */
		private Id(int value)
		{
			super(value);
		}
	}
}