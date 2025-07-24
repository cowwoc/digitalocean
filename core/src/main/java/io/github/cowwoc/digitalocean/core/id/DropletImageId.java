package io.github.cowwoc.digitalocean.core.id;

/**
 * A type-safe identifier for a DropletImage.
 */
public final class DropletImageId extends IntegerId
{
	/**
	 * Creates a new DropletImageId.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 */
	public static DropletImageId of(int value)
	{
		return new DropletImageId(value);
	}

	/**
	 * Creates a DropletImageId.
	 *
	 * @param value the server-side identifier for a droplet image
	 */
	private DropletImageId(int value)
	{
		super(value);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof DropletImageId other && super.equals(other);
	}
}