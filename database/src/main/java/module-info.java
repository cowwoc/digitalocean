/**
 * Database resources.
 */
module io.github.cowwoc.digitalocean.database
{
	requires transitive io.github.cowwoc.digitalocean.core;
	requires static io.github.cowwoc.digitalocean.network;
	requires io.github.cowwoc.requirements12.java;
	requires org.eclipse.jetty.client;
	requires com.fasterxml.jackson.databind;

	exports io.github.cowwoc.digitalocean.database.client;
	exports io.github.cowwoc.digitalocean.database.resource;
}