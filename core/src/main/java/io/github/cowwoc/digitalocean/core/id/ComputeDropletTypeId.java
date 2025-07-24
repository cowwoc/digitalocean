package io.github.cowwoc.digitalocean.core.id;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

/**
 * A type-safe identifier for a ComputeDropletType.
 */
public final class ComputeDropletTypeId extends StringId
{
	/**
	 * Creates a new ComputeDropletTypeId.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 * @throws IllegalArgumentException if {@code value} contains whitespace or is empty
	 */
	public static ComputeDropletTypeId of(String value)
	{
		if (value == null)
			return null;
		return new ComputeDropletTypeId(value);
	}

	/**
	 * Creates a ComputeDropletTypeId.
	 *
	 * @param value the server-side identifier for a compute droplet type
	 * @throws NullPointerException     if {@code value} is null
	 * @throws IllegalArgumentException if {@code value} does not contain whitespace and is not empty
	 */
	private ComputeDropletTypeId(String value)
	{
		super(value);
		requireThat(value, "value").doesNotContainWhitespace().isNotEmpty();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof ComputeDropletTypeId other && super.equals(other);
	}
}