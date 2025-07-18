/**
 * Droplets and other computation-related resources.
 */
module io.github.cowwoc.digitalocean.compute
{
	requires transitive io.github.cowwoc.digitalocean.network;
	requires io.github.cowwoc.pouch.core;
	requires io.github.cowwoc.requirements12.java;
	requires io.github.cowwoc.requirements12.jackson;
	requires org.eclipse.jetty.client;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.datatype.jsr310;
	requires io.github.cowwoc.digitalocean.core;
	requires org.apache.sshd.osgi;

	exports io.github.cowwoc.digitalocean.compute.client;
	exports io.github.cowwoc.digitalocean.compute.resource;

	exports io.github.cowwoc.digitalocean.compute.internal.client to
		io.github.cowwoc.digitalocean.database;
	exports io.github.cowwoc.digitalocean.compute.internal.resource to
		io.github.cowwoc.digitalocean.database, io.github.cowwoc.digitalocean.kubernetes;
	exports io.github.cowwoc.digitalocean.compute.internal.util to
		io.github.cowwoc.digitalocean.kubernetes, io.github.cowwoc.digitalocean.database;
}