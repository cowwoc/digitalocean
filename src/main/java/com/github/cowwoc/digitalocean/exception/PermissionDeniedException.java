package com.github.cowwoc.digitalocean.exception;

import java.io.Serial;

/**
 * Thrown if the server rejects a request due to insufficient permissions.
 */
public final class PermissionDeniedException extends Exception
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates a new instance.
	 *
	 * @param message an explanation of the failure
	 */
	public PermissionDeniedException(String message)
	{
		super(message);
	}
}