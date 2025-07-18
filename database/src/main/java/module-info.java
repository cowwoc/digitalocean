/**
 * Database resources.
 */
module io.github.cowwoc.digitalocean.database
{
	requires io.github.cowwoc.digitalocean.core;
	requires io.github.cowwoc.digitalocean.network;
	requires transitive io.github.cowwoc.digitalocean.compute;
	requires io.github.cowwoc.requirements12.java;
	requires org.eclipse.jetty.client;
	requires com.fasterxml.jackson.databind;

	exports io.github.cowwoc.digitalocean.database.client;
	exports io.github.cowwoc.digitalocean.database.resource;
}