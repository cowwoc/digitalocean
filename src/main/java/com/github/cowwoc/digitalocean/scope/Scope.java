package com.github.cowwoc.digitalocean.scope;

/**
 * The lifecycle of one or more variables.
 * <p>
 * Child scopes must invoke {@link #addChild(Scope)} on construction and {@link #removeChild(Scope)} after
 * they are closed.
 */
public interface Scope extends AutoCloseable
{
	/**
	 * Adds a child scope.
	 *
	 * @param child the child scope
	 * @throws IllegalStateException if the scope is closed
	 */
	void addChild(Scope child);

	/**
	 * Removes a child scope.
	 *
	 * @param child the child scope
	 */
	void removeChild(Scope child);

	/**
	 * Determines if the scope is closed
	 *
	 * @return {@code true} if the scope is closed
	 */
	boolean isClosed();

	@Override
	void close();
}