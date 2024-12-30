package com.github.cowwoc.digitalocean.scope;

import com.github.cowwoc.digitalocean.util.Configuration;
import com.github.cowwoc.pouch.core.AbstractScope;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The default implementation of JvmScope.
 * <p>
 * This class ensures that globals, such as slf4j, are initialized in a thread-safe manner.
 */
public final class DefaultJvmScope extends AbstractScope
	implements JvmScope
{
	private final Configuration configuration;
	/**
	 * The maximum amount of time to wait for scope resources to get released.
	 */
	private final Duration scopeCloseTimeout;

	/**
	 * {@code true} if the scope has been closed.
	 */
	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Returns the application's external configuration.
	 *
	 * @param configuration the application's external configuration
	 * @throws NullPointerException if any of the arguments are null
	 */
	public DefaultJvmScope(Configuration configuration)
	{
		this.configuration = configuration;
		scopeCloseTimeout = Duration.ofSeconds(30);
	}

	@Override
	public Configuration getConfiguration()
	{
		ensureOpen();
		return configuration;
	}

	@Override
	public void close()
	{
		children.shutdown(scopeCloseTimeout);
	}

	@Override
	public boolean isClosed()
	{
		return closed.get();
	}
}