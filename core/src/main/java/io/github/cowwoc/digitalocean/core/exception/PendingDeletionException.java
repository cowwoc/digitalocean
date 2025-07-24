package io.github.cowwoc.digitalocean.core.exception;

import java.io.IOException;
import java.io.Serial;

/**
 * Thrown if a resource is pending deletion.
 * <p>
 * While deletion is in progress, the resource’s unique identifiers (e.g., its name) cannot be reused, and the
 * existing instance cannot be retrieved or interacted with. DigitalOcean says that deletions may take up to
 * 15 minutes to complete.
 */
public class PendingDeletionException extends IOException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates a new instance.
	 *
	 * @param message an explanation of what went wrong
	 */
	public PendingDeletionException(String message)
	{
		super(message);
	}
}