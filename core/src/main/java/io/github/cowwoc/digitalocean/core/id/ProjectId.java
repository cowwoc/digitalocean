package io.github.cowwoc.digitalocean.core.id;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

/**
 * A type-safe identifier for a Project.
 */
public final class ProjectId extends StringId
{
	/**
	 * Creates a ProjectId.
	 *
	 * @param value the server-side identifier
	 * @return the type-safe identifier for the resource
	 * @throws NullPointerException     if {@code value} is null
	 * @throws IllegalArgumentException if {@code value} contains leading or trailing whitespace or is empty
	 */
	public static ProjectId of(String value)
	{
		if (value == null)
			return null;
		return new ProjectId(value);
	}

	/**
	 * Creates a ProjectId.
	 *
	 * @param value the server-side identifier for a project. An empty string represents the default project.
	 * @throws NullPointerException     if {@code value} is null
	 * @throws IllegalArgumentException if {@code value} contains leading or trailing whitespace
	 */
	private ProjectId(String value)
	{
		super(value);
		requireThat(value, "value").isStripped();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof ProjectId other && super.equals(other);
	}
}