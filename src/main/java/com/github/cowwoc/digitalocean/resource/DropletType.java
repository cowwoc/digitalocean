package com.github.cowwoc.digitalocean.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.digitalocean.client.DigitalOceanClient;
import com.github.cowwoc.digitalocean.id.StringId;
import com.github.cowwoc.digitalocean.internal.util.Numbers;
import com.github.cowwoc.digitalocean.internal.util.ToStringBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.REST_SERVER;
import static com.github.cowwoc.digitalocean.internal.client.MainDigitalOceanClient.TIB_TO_GIB;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Droplet types.
 *
 * @see <a href="https://www.digitalocean.com/pricing/droplets#basic-droplets">pricing</a>
 */
public final class DropletType
{
	/**
	 * Creates a new ID.
	 *
	 * @param value the server-side identifier (slug)
	 * @return the type-safe identifier for the resource
	 * @throws IllegalArgumentException if {@code value} contains leading or trailing whitespace or is empty
	 */
	public static Id id(String value)
	{
		if (value == null)
			return null;
		return new Id(value);
	}

	/**
	 * Returns all the Droplet types that are available for creating Droplets.
	 *
	 * @param client the client configuration
	 * @return an empty set if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Set<DropletType> getAll(DigitalOceanClient client)
		throws IOException, InterruptedException, TimeoutException
	{
		return getAll(client, true);
	}

	/**
	 * Returns all the Droplet types.
	 *
	 * @param client            the client configuration
	 * @param canCreateDroplets {@code true} if the returned types must be able to create Droplets
	 * @return an empty set if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Set<DropletType> getAll(DigitalOceanClient client, boolean canCreateDroplets)
		throws IOException, InterruptedException, TimeoutException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/sizes_list
		return client.getElements(REST_SERVER.resolve("v2/sizes"), Map.of(), body ->
		{
			Set<DropletType> types = new HashSet<>();
			for (JsonNode typeNode : body.get("sizes"))
			{
				DropletType candidate = getByJson(client, typeNode);
				if (candidate.available || !canCreateDroplets)
					types.add(candidate);
			}
			return types;
		});
	}

	/**
	 * Returns the first Droplet type that matches a predicate.
	 *
	 * @param client    the client configuration
	 * @param predicate the predicate
	 * @return null if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static DropletType getByPredicate(DigitalOceanClient client, Predicate<DropletType> predicate)
		throws IOException, InterruptedException, TimeoutException
	{
		// https://docs.digitalocean.com/reference/api/api-reference/#operation/sizes_list
		return client.getElement(REST_SERVER.resolve("v2/sizes"), Map.of(), body ->
		{
			for (JsonNode typeNode : body.get("sizes"))
			{
				DropletType candidate = getByJson(client, typeNode);
				if (predicate.test(candidate))
					return candidate;
			}
			return null;
		});
	}

	/**
	 * Parses the JSON representation of this class.
	 *
	 * @param client the client configuration
	 * @param json   the JSON representation
	 * @return the droplet type
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the server response could not be parsed
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	private static DropletType getByJson(DigitalOceanClient client, JsonNode json)
		throws IOException, TimeoutException, InterruptedException
	{
		String slug = json.get("slug").textValue();
		int ramInMB = client.getInt(json, "memory");
		int vCpus = client.getInt(json, "vcpus");
		int diskInMb = client.getInt(json, "disk") * 1024;
		BigDecimal transferInGiB = json.get("transfer").decimalValue().multiply(TIB_TO_GIB);
		BigDecimal pricePerHour = json.get("price_hourly").decimalValue();
		BigDecimal pricePerMonth = json.get("price_monthly").decimalValue();
		Set<Zone.Id> zones = client.getElements(json, "regions", node -> Zone.id(node.textValue()));
		boolean available = client.getBoolean(json, "available");
		String description = json.get("description").textValue();
		Set<DiskDetails> diskDetails = client.getElements(json, "disk_info", element ->
			DiskDetails.getByJson(client, element));
		JsonNode gpuInfoNode = json.get("gpu_info");
		GpuDetails gpuDetails;
		if (gpuInfoNode == null)
			gpuDetails = null;
		else
			gpuDetails = GpuDetails.getByJson(client, gpuInfoNode);
		return new DropletType(id(slug), ramInMB, vCpus, diskInMb, transferInGiB, pricePerMonth, pricePerHour,
			zones, available, description, diskDetails, gpuDetails);
	}

	private final Id id;
	private final int ramInMiB;
	private final int cpus;
	private final int diskInMiB;
	private final BigDecimal transferInGiB;
	private final BigDecimal pricePerHour;
	private final BigDecimal pricePerMonth;
	private final Set<Zone.Id> zones;
	private final boolean available;
	private final String description;
	private final Set<DiskDetails> diskDetails;
	private final GpuDetails gpuDetails;

	/**
	 * Creates a new instance.
	 *
	 * @param id            the type's ID
	 * @param ramInMiB      the amount of RAM allocated to this type, in MiB
	 * @param cpus          the number of virtual CPUs allocated to this type. Note that vCPUs are relative
	 *                      units specific to each vendor's infrastructure and hardware. A number of vCPUs is
	 *                      relative to other vCPUs by the same vendor, meaning that the allocation and
	 *                      performance are comparable within the same vendor's environment.
	 * @param diskInMiB     the amount of disk space allocated to this type, in GiB
	 * @param transferInGiB the amount of outgoing network traffic allocated to this type, in GiB
	 * @param pricePerHour  the hourly cost of this Droplet type in US dollars. This cost is incurred as long as
	 *                      the Droplet is active and has not been destroyed.
	 * @param pricePerMonth the monthly cost of this Droplet type in US dollars. This cost is incurred as long
	 *                      as the Droplet is active and has not been destroyed.
	 * @param zones         the zones where this type of Droplet may be created
	 * @param available     {@code true} if Droplets may be created with this type, regardless of the zone
	 * @param description   a description of this type. For example, Basic, General Purpose, CPU-Optimized,
	 *                      Memory-Optimized, or Storage-Optimized.
	 * @param diskDetails   more details about the disks available to this type
	 * @param gpuDetails    (optional) more details about the GPU available to this type, or {@code null} if
	 *                      absent
	 * @throws NullPointerException     if any of the mandatory arguments are null
	 * @throws IllegalArgumentException if {@code description} contains leading or trailing whitespace or is
	 *                                  empty
	 */
	public DropletType(Id id, int ramInMiB, int cpus, int diskInMiB, BigDecimal transferInGiB,
		BigDecimal pricePerHour, BigDecimal pricePerMonth, Set<Zone.Id> zones, boolean available,
		String description, Set<DiskDetails> diskDetails, GpuDetails gpuDetails)
	{
		requireThat(id, "id").isNotNull();
		requireThat(ramInMiB, "ramInMiB").isPositive();
		requireThat(cpus, "vCpus").isPositive();
		requireThat(diskInMiB, "diskInMiB").isPositive();
		requireThat(transferInGiB, "transferInGiB").isPositive();
		requireThat(pricePerHour, "pricePerHour").isPositive();
		requireThat(pricePerMonth, "pricePerMonth").isPositive();
		requireThat(zones, "zones").isNotNull();
		requireThat(description, "description").isStripped().isNotEmpty();
		requireThat(diskDetails, "diskDetails").isNotNull();
		this.id = id;
		this.ramInMiB = ramInMiB;
		this.cpus = cpus;
		this.diskInMiB = diskInMiB;
		this.transferInGiB = Numbers.copyOf(transferInGiB);
		this.pricePerHour = Numbers.copyOf(pricePerHour);
		this.pricePerMonth = Numbers.copyOf(pricePerMonth);
		this.zones = Set.copyOf(zones);
		this.available = available;
		this.description = description;
		this.diskDetails = diskDetails;
		this.gpuDetails = gpuDetails;
	}

	/**
	 * Returns the type's ID.
	 *
	 * @return the ID
	 */
	public Id getId()
	{
		return id;
	}

	/**
	 * Returns the amount of RAM allocated to this type, in MiB.
	 *
	 * @return the amount of RAM
	 */
	public int getRamInMiB()
	{
		return ramInMiB;
	}

	/**
	 * Returns the number of virtual CPUs allocated to this type. Note that vCPUs are relative units specific to
	 * each vendor's infrastructure and hardware. A number of vCPUs is relative to other vCPUs by the same
	 * vendor, meaning that the allocation and performance are comparable within the same vendor's environment.
	 *
	 * @return the number of virtual CPUs
	 */
	public int getCpus()
	{
		return cpus;
	}

	/**
	 * Returns the amount of disk space allocated to this type, in GiB.
	 *
	 * @return the amount of disk space
	 */
	public int getDiskInMiB()
	{
		return diskInMiB;
	}

	/**
	 * Returns the amount of outgoing network traffic allocated to this type, in GiB.
	 *
	 * @return the amount of network traffic
	 */
	public BigDecimal getTransferInGiB()
	{
		return transferInGiB;
	}

	/**
	 * Returns the hourly cost of this Droplet type in US dollars. This cost is incurred as long as the Droplet
	 * is active and has not been destroyed.
	 *
	 * @return the cost per hour
	 */
	public BigDecimal getPricePerHour()
	{
		return pricePerHour;
	}

	/**
	 * Returns the monthly cost of this Droplet type in US dollars. This cost is incurred as long as the Droplet
	 * is active and has not been destroyed.
	 *
	 * @return the cost per month
	 */
	public BigDecimal getPricePerMonth()
	{
		return pricePerMonth;
	}

	/**
	 * Returns the zones where this type of Droplet may be created.
	 *
	 * @return the zones
	 */
	public Set<Zone.Id> getZones()
	{
		return zones;
	}

	/**
	 * Determines if Droplets may be created with this type, regardless of the zone.
	 *
	 * @return {@code true} if Droplets may be created
	 */
	public boolean isAvailable()
	{
		return available;
	}

	/**
	 * Returns a description of this type. For example, Basic, General Purpose, CPU-Optimized, Memory-Optimized,
	 * or Storage-Optimized.
	 *
	 * @return the description
	 */
	public String getDescription()
	{
		return description;
	}

	/**
	 * Returns more details about the disks available to this type.
	 *
	 * @return the details
	 */
	public Set<DiskDetails> getDiskDetails()
	{
		return diskDetails;
	}

	/**
	 * Returns more details about the GPU available to this type.
	 *
	 * @return {@code null} if absent
	 */
	public GpuDetails getGpuDetails()
	{
		return gpuDetails;
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof DropletType other && other.id.equals(id);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(DropletType.class).
			add("id", id).
			add("ramInMiB", ramInMiB).
			add("cpus", cpus).
			add("diskInMiB", diskInMiB).
			add("transferInGiB", transferInGiB).
			add("pricePerHour", pricePerHour).
			add("pricePerMonth", pricePerMonth).
			add("zones", zones).
			add("available", available).
			add("description", description).
			add("diskDetails", diskDetails).
			add("gpuDetails", gpuDetails).
			toString();
	}

	/**
	 * A type-safe identifier for this type of resource.
	 * <p>
	 * This adds type-safety to API methods by ensuring that IDs specific to one class cannot be used in place
	 * of IDs belonging to another class.
	 */
	public static final class Id extends StringId
	{
		/**
		 * @param value a server-side identifier
		 * @throws NullPointerException     if {@code value} is null
		 * @throws IllegalArgumentException if {@code value} contains leading or trailing whitespace or is empty
		 */
		private Id(String value)
		{
			super(value);
		}
	}

	/**
	 * Information about the disk that is allocated to a droplet.
	 *
	 * @param persistent {@code true} if the disk is persistent
	 * @param sizeInMiB  the amount of space allocated to this disk, in MiB
	 */
	public record DiskDetails(boolean persistent, int sizeInMiB)
	{
		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param client the client configuration
		 * @param json   the JSON representation
		 * @return the disk details
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 * @throws IllegalStateException    if the client is closed
		 */
		private static DiskDetails getByJson(DigitalOceanClient client, JsonNode json)
		{
			String type = json.get("type").textValue();
			boolean persistent = switch (type)
			{
				case "local" -> true;
				case "scratch" -> false;
				default -> throw new IllegalStateException("Unexpected value: " + type);
			};
			JsonNode sizeNode = json.get("size");
			int amount = client.getInt(sizeNode, "amount");
			String unit = sizeNode.get("unit").textValue();
			int sizeInMiB = switch (unit)
			{
				case "gib" -> amount * 1024;
				case "tib" -> amount * 1024 * 1024;
				default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
			};
			return new DiskDetails(persistent, sizeInMiB);
		}
	}

	/**
	 * Information about the GPU that is allocated to a droplet type.
	 *
	 * @param count    the number of GPUs allocated to this type of droplet
	 * @param model    the model of the GPU
	 * @param ramInMiB the size of the GPU's RAM, in MiB
	 */
	public record GpuDetails(int count, String model, int ramInMiB)
	{
		/**
		 * Creates a new instance.
		 *
		 * @param count    the number of GPUs allocated to this type of droplet
		 * @param model    the model of the GPU
		 * @param ramInMiB the size of the GPU's RAM, in MiB
		 * @throws NullPointerException     if {@code model} is null
		 * @throws IllegalArgumentException if:
		 *                                  <ul>
		 *                                  <li>{@code count} is negative or zero.</li>
		 *                                  <li>{@code model} contains leading or trailing whitespace or is empty.</li>
		 *                                  <li>{@code ramInMb} is negative or zero.</li>
		 *                                  </ul>
		 */
		public GpuDetails
		{
			requireThat(count, "count").isPositive();
			requireThat(model, "model").isStripped().isNotEmpty();
			requireThat(ramInMiB, "ramInMb").isPositive();
		}

		/**
		 * Parses the JSON representation of this class.
		 *
		 * @param client the client configuration
		 * @param json   the JSON representation
		 * @return the GPU details
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if the server response could not be parsed
		 * @throws IllegalStateException    if the client is closed
		 */
		private static GpuDetails getByJson(DigitalOceanClient client, JsonNode json)
		{
			int count = client.getInt(json, "count");
			String model = json.get("model").textValue();
			JsonNode ramNode = json.get("vram");
			int amount = client.getInt(ramNode, "amount");
			String unit = json.get("unit").textValue();
			int ramInMiB = switch (unit)
			{
				case "gib" -> amount * 1024;
				case "tib" -> amount * 1024 * 1024;
				default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
			};
			return new GpuDetails(count, model, ramInMiB);
		}
	}
}