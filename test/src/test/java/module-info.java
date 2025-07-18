/**
 * DigitalOcean tests.
 */
module io.github.cowwoc.digitalocean.test
{
	requires io.github.cowwoc.digitalocean.core;
	requires io.github.cowwoc.digitalocean.compute;
	requires io.github.cowwoc.requirements12.java;
	requires org.slf4j;
	requires org.testng;

	exports io.github.cowwoc.digitalocean.test to org.testng;
	exports io.github.cowwoc.digitalocean.test.util to org.testng;
}