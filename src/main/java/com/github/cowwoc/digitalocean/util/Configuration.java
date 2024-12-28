package com.github.cowwoc.digitalocean.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * A configuration file.
 * <p>
 * <b>Thread-safety</b>: This class is thread-safe.
 */
public abstract class Configuration
{
	/**
	 * Creates a new configuration.
	 */
	protected Configuration()
	{
	}

	private static final Configuration EMPTY = new Configuration()
	{
		@Override
		String getSource()
		{
			return "empty map";
		}

		@Override
		Set<String> keySet()
		{
			return Set.of();
		}

		@Override
		public String getStringOrDefault(String key, String defaultValue)
		{
			requireThat(key, "key").isStripped().isNotEmpty();
			return defaultValue;
		}

		@Override
		public String toString()
		{
			return "{}";
		}
	};

	/**
	 * @return the source of the configuration properties
	 */
	abstract String getSource();

	/**
	 * @return the set of keys contained within this configuration
	 */
	abstract Set<String> keySet();

	/**
	 * Returns the value of an optional configuration option.
	 *
	 * @param key          the name of the configuration option
	 * @param defaultValue the value to return if the configuration is absent
	 * @return the value of the configuration option
	 * @throws NullPointerException     if {@code key} is null
	 * @throws IllegalArgumentException if {@code key} contains leading, trailing whitespace or is empty
	 */
	public abstract String getStringOrDefault(String key, String defaultValue);

	/**
	 * Returns an empty configuration.
	 *
	 * @return an empty configuration
	 */
	public static Configuration empty()
	{
		return EMPTY;
	}

	/**
	 * Returns a configuration that reads values from a file.
	 *
	 * @param path the path of the configuration file
	 * @return the configuration
	 * @throws IOException if an I/O error occurs while loading the file
	 */
	public static Configuration fromPath(Path path) throws IOException
	{
		Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(path))
		{
			properties.load(in);
		}
		Map<String, String> propertiesAsMap = properties.entrySet().stream().
			collect(Collectors.toMap(e -> (String) e.getKey(), e -> (String) e.getValue()));
		return fromMap(propertiesAsMap, "classpath");
	}

	/**
	 * Returns a configuration that reads values from a file on the classpath.
	 *
	 * @param classpath the path of the configuration file on the classpath
	 * @return the configuration
	 * @throws IOException if an I/O error occurs while loading the file
	 */
	public static Configuration fromClassPath(String classpath) throws IOException
	{
		ClassLoader classLoader = Configuration.class.getModule().getClassLoader();
		Properties properties = new Properties();
		try (InputStream resource = classLoader.getResourceAsStream(classpath))
		{
			if (resource == null)
				throw new FileNotFoundException("Classpath entry not found: " + classpath);
			properties.load(resource);
		}
		Map<String, String> propertiesAsMap = properties.entrySet().stream().
			collect(Collectors.toMap(e -> (String) e.getKey(), e -> (String) e.getValue()));
		return fromMap(propertiesAsMap, "classpath");
	}

	/**
	 * Returns a configuration that reads values from a map.
	 *
	 * @param properties the map from each key to its value
	 * @return a new configuration
	 * @throws NullPointerException if {@code properties} is null
	 */
	public static Configuration fromMap(Map<String, String> properties)
	{
		Map<String, String> copyOfProperties = Map.copyOf(properties);
		return fromMap(copyOfProperties, "Map<String, String>");
	}

	/**
	 * Returns a configuration that reads values from a map.
	 *
	 * @param properties the map from each key to its value
	 * @param source     the type of the configuration source
	 * @return a new configuration
	 */
	private static Configuration fromMap(Map<String, String> properties, String source)
	{
		return new Configuration()
		{
			@Override
			String getSource()
			{
				return source;
			}

			@Override
			Set<String> keySet()
			{
				return Set.of();
			}

			@Override
			public String getStringOrDefault(String key, String defaultValue)
			{
				requireThat(key, "key").isStripped().isNotEmpty();
				String value = properties.get(key);
				if (value == null)
					return defaultValue;
				return value;
			}

			@Override
			public String toString()
			{
				return properties.toString();
			}
		};
	}

	/**
	 * Combines one or more configurations. If multiple configurations contain the same key, the last value is
	 * used.
	 *
	 * @param configurations one or more configuration files
	 * @return a new configuration
	 */
	public static Configuration combine(Configuration... configurations)
	{
		requireThat(configurations, "configurations").isNotNull();

		return new Configuration()
		{
			@Override
			String getSource()
			{
				StringJoiner joiner = new StringJoiner(", ");
				for (Configuration configuration : configurations)
					joiner.add(configuration.getSource());
				return "combine(" + joiner + ")";
			}

			@Override
			Set<String> keySet()
			{
				Set<String> keys = new HashSet<>();
				for (Configuration configuration : configurations)
					keys.addAll(configuration.keySet());
				return keys;
			}

			@Override
			public String getStringOrDefault(String key, String defaultValue)
			{
				String value = null;
				for (Configuration configuration : configurations)
				{
					String newValue = configuration.getStringOrDefault(key, null);
					if (newValue != null)
						value = newValue;
				}
				if (value == null)
					return defaultValue;
				return value;
			}

			@Override
			public String toString()
			{
				List<Configuration> concat = new ArrayList<>();
				Collections.addAll(concat, configurations);
				return concat.toString();
			}
		};
	}

	/**
	 * Returns a configuration that reads values from environment variables.
	 *
	 * @return a new configuration
	 */
	public static Configuration fromEnvironmentVariables()
	{
		return fromMap(System.getenv(), "environment variables");
	}

	/**
	 * Returns the value of a configuration option as a URI.
	 *
	 * @param key the name of the configuration option
	 * @return the value of the configuration option
	 * @throws NullPointerException     if {@code key} is null
	 * @throws IllegalArgumentException if {@code key} is blank. If the configuration did not contain the
	 *                                  specified key. If the value is not a valid URI.
	 */
	public URI getUri(String key)
	{
		String uriAsString = getString(key);
		return URI.create(uriAsString);
	}

	/**
	 * Returns the value of a configuration option consisting of a comma-separated list of values.
	 *
	 * @param key the name of the configuration option
	 * @return the value of the configuration option
	 * @throws NullPointerException     if {@code key} is null
	 * @throws IllegalArgumentException if {@code key} is blank. If the configuration did not contain the
	 *                                  specified key.
	 */
	public List<String> getList(String key)
	{
		String commaSeparatedList = getString(key);
		String[] individualValues = commaSeparatedList.split(",\\s*");
		return Arrays.asList(individualValues);
	}

	/**
	 * Returns the value of a configuration option.
	 *
	 * @param key the name of the configuration option
	 * @return the value of the configuration option
	 * @throws NullPointerException     if {@code key} is null
	 * @throws IllegalArgumentException if the configuration did not contain the specified key
	 */
	public String getString(String key)
	{
		String value = getStringOrDefault(key, null);
		if (value == null)
		{
			throw new IllegalArgumentException("configuration must contain " + key + ".\n" +
				"Actual: null\n" +
				"Source: " + getSource() + "\n" +
				"Keys  : " + keySet());
		}
		return value;
	}

	/**
	 * Returns the value of a configuration option as an integer.
	 *
	 * @param key the name of the configuration option
	 * @return the value of the configuration option as an integer
	 * @throws NullPointerException     if {@code key} is null
	 * @throws NumberFormatException    if the value is not an integer
	 * @throws IllegalArgumentException if {@code key} contains leading, trailing whitespace or is blank. If the
	 *                                  configuration did not contain the specified key.
	 */
	public int getInt(String key)
	{
		return Integer.parseInt(getString(key));
	}

	/**
	 * Returns the value of an optional configuration option as an integer.
	 *
	 * @param key          the name of the configuration option
	 * @param defaultValue the value to return if the configuration is absent
	 * @return the value of the configuration option as an integer
	 * @throws NullPointerException     if {@code key} is null
	 * @throws NumberFormatException    if the value is not an integer
	 * @throws IllegalArgumentException if {@code key} contains leading, trailing whitespace or is blank
	 */
	public int getIntOrDefault(String key, int defaultValue)
	{
		String valueAsString = getStringOrDefault(key, null);
		if (valueAsString == null)
			return defaultValue;
		return Integer.parseInt(valueAsString);
	}

	/**
	 * Returns the value of a configuration option as a double.
	 *
	 * @param key the name of the configuration option
	 * @return the value of the configuration option as a double
	 * @throws NullPointerException     if {@code key} is null
	 * @throws NumberFormatException    if the value is not a double
	 * @throws IllegalArgumentException if {@code key} contains leading, trailing whitespace or is blank. If the
	 *                                  configuration did not contain the specified key.
	 */
	public double getDouble(String key)
	{
		return Double.parseDouble(getString(key));
	}

	/**
	 * Returns the value of an optional configuration option as a double.
	 *
	 * @param key          the name of the configuration option
	 * @param defaultValue the value to return if the configuration is absent
	 * @return the value of the configuration option as a double
	 * @throws NullPointerException     if {@code key} is null
	 * @throws NumberFormatException    if the value is not a double
	 * @throws IllegalArgumentException if {@code key} contains leading, trailing whitespace or is blank
	 */
	public double getDoubleOrDefault(String key, double defaultValue)
	{
		String valueAsString = getStringOrDefault(key, null);
		if (valueAsString == null)
			return defaultValue;
		return Double.parseDouble(valueAsString);
	}
}