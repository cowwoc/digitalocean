/**
 * Kubernetes-related resources.
 */
module io.github.cowwoc.digitalocean.kubernetes
{
	requires transitive io.github.cowwoc.digitalocean.compute;
	requires io.github.cowwoc.digitalocean.network;
	requires io.github.cowwoc.requirements12.java;
	requires com.fasterxml.jackson.databind;
	requires org.slf4j;
	requires org.eclipse.jetty.client;

	exports io.github.cowwoc.digitalocean.kubernetes.client;
	exports io.github.cowwoc.digitalocean.kubernetes.resource;
}