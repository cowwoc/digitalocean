package io.github.cowwoc.digitalocean.core.exception;

import java.io.IOException;
import java.io.Serial;
import java.time.Duration;
import java.time.Instant;

/**
 * Thrown when the client has sent too many requests in a given period and has surpassed the server's rate
 * limit.
 */
public final class TooManyRequestsException extends IOException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Indicates how long the client has to sleep before making another request.
	 *
	 * @param retryAfter the next time that the client is allowed to make a request
	 * @param resetTime  the time when the oldest request will expire
	 * @return the sleep time
	 */
	public static Duration getSleepDuration(Duration retryAfter, Instant resetTime)
	{
		Instant now = Instant.now();
		if (retryAfter.isPositive())
			return retryAfter;
		return positiveOrZero(Duration.between(now, resetTime));
	}

	// private fields must be documented because the class is Serializable:
	// https://bugs.openjdk.org/browse/JDK-8275192
	/**
	 * The number of requests that the client may make per minute.
	 */
	private final int requestsPerMinute;
	/**
	 * The number of requests that the client may make per hour.
	 */
	private final int requestsPerHour;
	/**
	 * The next time that the client is allowed to make a request.
	 */
	private final Instant retryAfter;
	/**
	 * The time when the oldest request will expire.
	 */
	private final Instant resetTime;

	/**
	 * Creates a new instance.
	 *
	 * @param requestsPerMinute the number of requests that the client may make per minute
	 * @param requestsPerHour   the number of requests that the client may make per hour
	 * @param retryAfter        the next time that the client is allowed to make a request
	 * @param resetTime         the time when the oldest request will expire
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code requestsPerMinute} or {@code requestsPerHour} are negative
	 */
	public TooManyRequestsException(int requestsPerMinute, int requestsPerHour, Duration retryAfter,
		Instant resetTime)
	{
		super("The client must wait " + getSleepDuration(retryAfter, resetTime) + " to make another request");
		this.requestsPerMinute = requestsPerMinute;
		this.requestsPerHour = requestsPerHour;
		this.retryAfter = Instant.now().plus(retryAfter);
		this.resetTime = resetTime;
	}

	/**
	 * Returns the number of requests that may be made per minute to this endpoint.
	 *
	 * @return the number of requests
	 */
	public int getRequestsPerMinute()
	{
		return requestsPerMinute;
	}

	/**
	 * Returns the number of requests that may be made per hour to this endpoint.
	 *
	 * @return the number of requests
	 */
	public int getRequestsPerHour()
	{
		return requestsPerHour;
	}

	/**
	 * Indicates how long the client has to sleep before making another request.
	 *
	 * @return the sleep time
	 */
	public Duration getSleepDuration()
	{
		Instant now = Instant.now();
		Duration retryAfterTimeLeft = Duration.between(now, retryAfter);
		if (retryAfterTimeLeft.isPositive())
			return retryAfterTimeLeft;
		return positiveOrZero(Duration.between(now, resetTime));
	}

	/**
	 * Returns the given duration if it is positive; otherwise, returns a duration of zero.
	 *
	 * @param duration the duration
	 * @return the duration if it is positive, or {@code Duration.ZERO} if negative or zero
	 * @throws NullPointerException if {@code duration} is null
	 */
	private static Duration positiveOrZero(Duration duration)
	{
		if (duration.isNegative())
			return Duration.ZERO;
		return duration;
	}
}