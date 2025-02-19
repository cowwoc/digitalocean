/**
 * A Java client for the DigitalOcean cloud platform.
 */
module com.github.cowwoc.digitalocean
{
	requires org.eclipse.jetty.client;
	requires org.eclipse.jetty.http;
	requires com.github.cowwoc.requirements10.jackson;
	requires com.github.cowwoc.pouch.core;
	requires org.apache.sshd.osgi;
	requires org.threeten.extra;
	requires com.fasterxml.jackson.datatype.jsr310;
	requires com.fasterxml.jackson.databind;
	requires java.net.http;
	requires java.xml.crypto;
	requires java.management;
	requires jdk.jfr;

	exports com.github.cowwoc.digitalocean.exception;
	exports com.github.cowwoc.digitalocean.id;
	exports com.github.cowwoc.digitalocean.resource;
	exports com.github.cowwoc.digitalocean.client;
	exports com.github.cowwoc.digitalocean.util;
}