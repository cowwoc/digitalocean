/**
 * Container registry related resources.
 */
module io.github.cowwoc.digitalocean.registry
{
	requires transitive io.github.cowwoc.digitalocean.core;
	requires io.github.cowwoc.pouch.core;
	requires io.github.cowwoc.requirements12.java;
	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires org.eclipse.jetty.client;

	exports io.github.cowwoc.digitalocean.registry.client;
	exports io.github.cowwoc.digitalocean.registry.resource;
}