package io.github.cowwoc.digitalocean.core.id;

/**
 * A type-safe identifier for a container repository.
 */
public final class RepositoryId extends IntegerId
{
	/**
	 * Creates a new RepositoryId.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 */
	public static RepositoryId of(int value)
	{
		return new RepositoryId(value);
	}

	/**
	 * Creates a RepositoryId.
	 *
	 * @param value the server-side identifier for a repository
	 */
	private RepositoryId(int value)
	{
		super(value);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof RepositoryId other && super.equals(other);
	}
}