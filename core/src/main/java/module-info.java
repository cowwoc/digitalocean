/**
 * Code common across all modules.
 */
module io.github.cowwoc.digitalocean.core
{
	requires io.github.cowwoc.pouch.core;
	requires io.github.cowwoc.requirements12.java;
	requires io.github.cowwoc.requirements12.jackson;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.datatype.jsr310;
	requires org.eclipse.jetty.client;
	requires org.eclipse.jetty.util;
	requires org.threeten.extra;

	exports io.github.cowwoc.digitalocean.core.client;
	exports io.github.cowwoc.digitalocean.core.exception;
	exports io.github.cowwoc.digitalocean.core.id;
	exports io.github.cowwoc.digitalocean.core.util;

	exports io.github.cowwoc.digitalocean.core.internal.client to
		io.github.cowwoc.digitalocean.compute, io.github.cowwoc.digitalocean.database,
		io.github.cowwoc.digitalocean.registry, io.github.cowwoc.digitalocean.kubernetes,
		io.github.cowwoc.digitalocean.network, io.github.cowwoc.digitalocean.project;
	exports io.github.cowwoc.digitalocean.core.internal.parser to
		io.github.cowwoc.digitalocean.compute, io.github.cowwoc.digitalocean.database,
		io.github.cowwoc.digitalocean.registry, io.github.cowwoc.digitalocean.kubernetes,
		io.github.cowwoc.digitalocean.network, io.github.cowwoc.digitalocean.project;
	exports io.github.cowwoc.digitalocean.core.internal.util to
		io.github.cowwoc.digitalocean.compute, io.github.cowwoc.digitalocean.database,
		io.github.cowwoc.digitalocean.registry, io.github.cowwoc.digitalocean.kubernetes,
		io.github.cowwoc.digitalocean.network, io.github.cowwoc.digitalocean.project, io.github.cowwoc.digitalocean.test;
}