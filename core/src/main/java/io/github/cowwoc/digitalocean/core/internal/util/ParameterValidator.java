package io.github.cowwoc.digitalocean.core.internal.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.cowwoc.requirements12.java.DefaultJavaValidators.requireThat;

/**
 * Validates common input parameters.
 */
public final class ParameterValidator
{
	private final static Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,63}[a-z0-9]$");

	/**
	 * Validates a name.
	 *
	 * @param value the value of the name. The value must start with a letter, or digit, or underscore, and may
	 *              be followed by additional characters consisting of letters, digits, underscores, periods or
	 *              hyphens.
	 * @param name  the name of the value parameter
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code value} contains characters other than lowercase letters
	 *                                    ({@code a-z}), digits ({@code 0-9}), and dashes ({@code -}).</li>
	 *                                    <li>{@code value} begins or ends with a character other than a letter
	 *                                    or digit.</li>
	 *                                    <li>{@code value} is shorter than 3 characters or longer than 63
	 *                                    characters.</li>
	 *                                    <li>{@code name} contains leading or trailing whitespace or is empty.
	 *                                    </li>
	 *                                  </ul>
	 */
	public static void validateName(String value, String name)
	{
		assert name != null;
		assert !name.isBlank();
		requireThat(value, "value").length().isBetween(3, true, 63, true);

		Matcher matcher = NAME_PATTERN.matcher(value);
		if (!matcher.matches())
		{
			char firstCharacter = name.charAt(0);
			if (!(Character.isLowerCase(firstCharacter) || Character.isDigit(firstCharacter)))
			{
				throw new IllegalArgumentException(name + " must start with a lowercase letter or a number.\n" +
					"Value: " + value);
			}
			char lastCharacter = name.charAt(name.length() - 1);
			if (!(Character.isLowerCase(lastCharacter) || Character.isDigit(lastCharacter)))
			{
				throw new IllegalArgumentException(name + " must end with a lowercase letter or a number.\n" +
					"Value: " + value);
			}
			throw new IllegalArgumentException(name + " may include lowercase letters, numbers or hyphens. " +
				"No other characters are allowed.\n" +
				"Value: " + value);
		}
	}

	private ParameterValidator()
	{
	}
}