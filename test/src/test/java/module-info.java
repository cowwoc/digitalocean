/**
 * DigitalOcean tests.
 */
module io.github.cowwoc.digitalocean.test
{
	requires io.github.cowwoc.digitalocean.core;
	requires io.github.cowwoc.digitalocean.compute;
	requires io.github.cowwoc.digitalocean.database;
	requires io.github.cowwoc.digitalocean.network;
	requires io.github.cowwoc.requirements12.java;
	requires org.slf4j;
	requires org.testng;
	requires com.fasterxml.jackson.annotation;
	requires java.desktop;

	exports io.github.cowwoc.digitalocean.test to org.testng;
	exports io.github.cowwoc.digitalocean.test.util to org.testng;
}