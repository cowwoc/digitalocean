package io.github.cowwoc.digitalocean.core.id;

import java.util.Objects;

/**
 * A resource identifier of type {@code String}.
 * <p>
 * This adds type-safety to API methods by ensuring that IDs specific to one class cannot be used in place of
 * IDs belonging to another class.
 */
public abstract class StringId
{
	private final String value;

	/**
	 * Creates a new ID.
	 *
	 * @param value the value of the ID
	 */
	protected StringId(String value)
	{
		this.value = value;
	}

	@Override
	public int hashCode()
	{
		return Objects.hashCode(value);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof StringId other && Objects.equals(other.value, value);
	}

	/**
	 * Returns the String representation of the ID.
	 *
	 * @return the {@code String}
	 */
	public String getValue()
	{
		return value;
	}

	@Override
	public String toString()
	{
		return value;
	}
}