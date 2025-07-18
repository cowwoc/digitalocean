package io.github.cowwoc.digitalocean.core.exception;

import java.io.Serial;

/**
 * Thrown when the server rejects a request due to insufficient permissions.
 */
public final class AccessDeniedException extends RuntimeException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates a new instance.
	 *
	 * @param message an explanation of the failure
	 */
	public AccessDeniedException(String message)
	{
		super(message);
	}
}