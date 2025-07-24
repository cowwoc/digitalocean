package io.github.cowwoc.digitalocean.core.id;

/**
 * A type-safe identifier for an SshPublicKey.
 */
public final class SshPublicKeyId extends IntegerId
{
	/**
	 * Creates a new SshPublicKeyId.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 */
	public static SshPublicKeyId of(int value)
	{
		return new SshPublicKeyId(value);
	}

	/**
	 * Creates a SshPublicKeyId.
	 *
	 * @param value the server-side identifier for an ssh public key
	 */
	private SshPublicKeyId(int value)
	{
		super(value);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof SshPublicKeyId other && super.equals(other);
	}
}