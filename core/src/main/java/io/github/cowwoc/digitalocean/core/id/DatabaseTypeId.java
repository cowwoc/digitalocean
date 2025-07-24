package io.github.cowwoc.digitalocean.core.id;

/**
 * A type-safe identifier for a database type.
 */
public enum DatabaseTypeId
{
	/**
	 * <a href="https://www.postgresql.org/">PostgreSQL</a>.
	 */
	POSTGRESQL,
	/**
	 * <a href="https://www.mysql.com/">MySQL</a>.
	 */
	MYSQL,
	/**
	 * <a href="https://redis.io/">Redis</a>.
	 */
	REDIS,
	/**
	 * <a href="https://www.mongodb.com/">MongoDB</a>.
	 */
	MONGODB,
	/**
	 * <a href="https://kafka.apache.org/">Kafka</a>.
	 */
	KAFKA,
	/**
	 * <a href="https://opensearch.org/">OpenSearch</a>.
	 */
	OPENSEARCH
}