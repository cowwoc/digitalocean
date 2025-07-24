package io.github.cowwoc.digitalocean.core.exception;

import java.io.Serial;

/**
 * Thrown if a specific combination of parameters is rejected by the server due to constraints that cannot be
 * validated client-side or detected in advance.
 * <p>
 * This exception indicates that while each parameter may be valid individually, the combination is not
 * supported by the server and must be discovered at runtime.
 */
public class UnsupportedCombinationException extends Exception
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates a new instance.
	 *
	 * @param message an explanation of what went wrong
	 */
	public UnsupportedCombinationException(String message)
	{
		super(message);
	}
}