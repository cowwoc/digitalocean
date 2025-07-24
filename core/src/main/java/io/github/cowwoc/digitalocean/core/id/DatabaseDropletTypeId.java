package io.github.cowwoc.digitalocean.core.id;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

/**
 * A type-safe identifier for a DatabaseDropletType.
 */
public final class DatabaseDropletTypeId extends StringId
{
	/**
	 * Creates a new DatabaseDropletTypeId.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 * @throws IllegalArgumentException if {@code value} contains whitespace or is empty
	 */
	public static DatabaseDropletTypeId of(String value)
	{
		if (value == null)
			return null;
		return new DatabaseDropletTypeId(value);
	}

	/**
	 * Creates a DatabaseDropletTypeId.
	 *
	 * @param value the server-side identifier for a database droplet type
	 * @throws NullPointerException     if {@code value} is null
	 * @throws IllegalArgumentException if {@code value} contains whitespace or is empty
	 */
	private DatabaseDropletTypeId(String value)
	{
		super(value);
		requireThat(value, "value").doesNotContainWhitespace().isNotEmpty();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof DatabaseDropletTypeId other && super.equals(other);
	}
}