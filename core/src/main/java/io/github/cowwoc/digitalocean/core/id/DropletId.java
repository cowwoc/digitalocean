package io.github.cowwoc.digitalocean.core.id;

/**
 * A type-safe identifier for a Droplet.
 */
public final class DropletId extends IntegerId
{
	/**
	 * Creates a new DropletId.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 */
	public static DropletId of(int value)
	{
		return new DropletId(value);
	}

	/**
	 * Creates a DropletId.
	 *
	 * @param value the server-side identifier for a droplet
	 */
	private DropletId(int value)
	{
		super(value);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof DropletId other && super.equals(other);
	}
}