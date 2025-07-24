/**
 * Project-related resources.
 */
module io.github.cowwoc.digitalocean.project
{
	requires transitive io.github.cowwoc.digitalocean.core;
	requires static io.github.cowwoc.digitalocean.network;
	requires io.github.cowwoc.pouch.core;
	requires io.github.cowwoc.requirements12.java;
	requires com.fasterxml.jackson.databind;

	exports io.github.cowwoc.digitalocean.project.client;
	exports io.github.cowwoc.digitalocean.project.resource;
}