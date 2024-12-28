package com.github.cowwoc.digitalocean.scope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.cowwoc.digitalocean.internal.scope.HttpClientFactory;
import com.github.cowwoc.digitalocean.internal.util.ClientRequests;
import com.github.cowwoc.digitalocean.util.Configuration;
import com.github.cowwoc.pouch.core.Scopes;
import org.eclipse.jetty.client.HttpClient;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * The DigitalOceanScope used by the application.
 * <p>
 * <b>Thread-safety</b>: This implementation is thread-safe.
 */
public class MainDigitalOceanScope extends AbstractScope
	implements DigitalOceanScope
{
	private final JvmScope jvmScope;
	private final String digitalOceanToken;
	private final HttpClientFactory httpClient;
	private final ClientRequests clientRequests = new ClientRequests();
	private final ObjectMapper objectMapper = JsonMapper.builder().
		addModule(new JavaTimeModule()).
		disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).
		build();
	/**
	 * Indicates that the scope has finished shutting down.
	 */
	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Creates a new instance.
	 *
	 * @param jvmScope          the JVM configuration
	 * @param digitalOceanToken the DigitalOcean authentication token
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code digitalOceanToken} contains leading or trailing whitespace or
	 *                                  is empty
	 */
	public MainDigitalOceanScope(JvmScope jvmScope, String digitalOceanToken)
	{
		requireThat(jvmScope, "jvmScope").isNotNull();
		requireThat(digitalOceanToken, "digitalOceanToken").isStripped().isNotEmpty();
		this.jvmScope = jvmScope;
		this.httpClient = new HttpClientFactory();
		this.digitalOceanToken = digitalOceanToken;
	}

	@Override
	public Configuration getConfiguration()
	{
		return jvmScope.getConfiguration();
	}

	@Override
	public HttpClient getHttpClient()
	{
		ensureOpen();
		return httpClient.getValue();
	}

	@Override
	@SuppressWarnings("ClassEscapesDefinedScope")
	public ClientRequests getClientRequests()
	{
		ensureOpen();
		return clientRequests;
	}

	@Override
	public ObjectMapper getObjectMapper()
	{
		ensureOpen();
		return objectMapper;
	}

	@Override
	public String getDigitalOceanToken()
	{
		return digitalOceanToken;
	}

	@Override
	public boolean isClosed()
	{
		return closed.get();
	}

	@Override
	public void close()
	{
		if (!closed.compareAndSet(false, true))
			return;
		Scopes.runAll(() -> jvmScope.removeChild(this));
	}
}