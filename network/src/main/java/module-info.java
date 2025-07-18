/**
 * Network-related resources.
 */
module io.github.cowwoc.digitalocean.network
{
	requires transitive io.github.cowwoc.digitalocean.core;
	requires io.github.cowwoc.pouch.core;
	requires io.github.cowwoc.requirements12.java;
	requires io.github.cowwoc.requirements12.jackson;
	requires org.eclipse.jetty.client;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.datatype.jsr310;

	exports io.github.cowwoc.digitalocean.network.client;
	exports io.github.cowwoc.digitalocean.network.resource;

	exports io.github.cowwoc.digitalocean.network.internal.resource to
		io.github.cowwoc.digitalocean.compute, io.github.cowwoc.digitalocean.kubernetes,
		io.github.cowwoc.digitalocean.database;
}