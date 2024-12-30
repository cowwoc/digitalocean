package com.github.cowwoc.digitalocean.scope;

import com.github.cowwoc.digitalocean.util.Configuration;
import com.github.cowwoc.pouch.core.Scope;

/**
 * Values specific to the lifetime of a JVM.
 * <p>
 * Implementations must be thread-safe.
 */
public interface JvmScope extends Scope
{
	/**
	 * Returns the application's external configuration.
	 *
	 * @return the application's external configuration
	 * @throws IllegalStateException if the scope is closed
	 */
	Configuration getConfiguration();

	/**
	 * Internal variables that are used to implement scopes.
	 */
	@Override
	void close();
}