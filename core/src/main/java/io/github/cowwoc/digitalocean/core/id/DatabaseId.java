package io.github.cowwoc.digitalocean.core.id;

import io.github.cowwoc.digitalocean.core.internal.util.ParameterValidator;

/**
 * A type-safe identifier for a Database.
 */
public final class DatabaseId extends StringId
{
	/**
	 * Creates a DatabaseId.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 * @throws IllegalArgumentException if {@code value} contains whitespace or is empty
	 */
	public static DatabaseId of(String value)
	{
		if (value == null)
			return null;
		return new DatabaseId(value);
	}

	/**
	 * Creates a DatabaseId.
	 *
	 * @param value the server-side identifier for a database
	 * @throws NullPointerException     if {@code value} is null
	 * @throws IllegalArgumentException if {@code value}:
	 *                                  <ul>
	 *                                    <li>contains characters other than lowercase letters ({@code a-z}),
	 *                                    digits ({@code 0-9}), and dashes ({@code -}).</li>
	 *                                    <li>begins or ends with a character other than a letter
	 *                                    or digit.</li>
	 *                                    <li>is shorter than 3 characters or longer than 63 characters.</li>
	 *                                  </ul>
	 */
	private DatabaseId(String value)
	{
		super(value);
		ParameterValidator.validateName(value, "value");
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof DatabaseId other && super.equals(other);
	}
}