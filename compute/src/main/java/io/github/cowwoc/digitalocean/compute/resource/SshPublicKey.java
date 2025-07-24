package io.github.cowwoc.digitalocean.compute.resource;

import io.github.cowwoc.digitalocean.core.id.SshPublicKeyId;

import java.io.IOException;

/**
 * An SSH public key that is registered with the account or team where they were created.
 */
public interface SshPublicKey
{
	/**
	 * Returns the ID of the public key.
	 *
	 * @return the ID
	 */
	SshPublicKeyId getId();

	/**
	 * Returns the name of the public key.
	 *
	 * @return the name
	 */
	String getName();

	/**
	 * Returns the fingerprint of the public key.
	 *
	 * @return the fingerprint
	 */
	String getFingerprint();

	/**
	 * Destroys the SSH key. If the key does not exist, this method does nothing.
	 *
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	void destroy() throws IOException, InterruptedException;
}