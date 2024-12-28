package com.github.cowwoc.digitalocean.resource;

/**
 * Droplet <a href="https://slugs.do-api.dev/">types</a>.
 */
public enum DropletType
{
	/**
	 * Basic machine, 1 VCPU, 512MB memory, 10GB disk.
	 */
	BASIC_1_VCPU_512MB_RAM_10GB_DISK("s-1vcpu-512mb-10gb"),
	/**
	 * Basic machine, 1 VCPU, 1GB memory, 25GB disk.
	 */
	BASIC_1_VCPU_1GB_RAM_10GB_DISK("s-1vcpu-1gb"),
	/**
	 * Basic machine, 1 AMD VCPU, 1GB memory, 25GB disk.
	 */
	BASIC_1_AMD_VCPU_1024MB_RAM_25GB_DISK("s-1vcpu-1gb-amd"),
	/**
	 * Basic machine, 1 Intel VCPU, 1GB memory, 25GB disk.
	 */
	BASIC_1_INTEL_VCPU_1GB_RAM_25GB_DISK("s-1vcpu-1gb-intel"),
	/**
	 * Basic machine, 1 Intel VCPU, 1GB memory, 35GB disk.
	 */
	BASIC_1_INTEL_VCPU_1GB_RAM_35GB_DISK("s-1vcpu-1gb-35gb-intel"),
	/**
	 * Basic machine, 1 VCPU, 2GB memory, 50GB disk.
	 */
	BASIC_1_VCPU_2GB_RAM_50GB_DISK("s-1vcpu-2gb"),
	/**
	 * Basic, 1 AMD VCPU, 2GB memory, 50GB disk.
	 */
	BASIC_1_AMD_VCPU_2GB_RAM_50GB_DISK("s-1vcpu-2gb-amd"),
	/**
	 * Basic, 1 Intel VCPU, 2GB memory, 50GB disk.
	 */
	BASIC_1_INTEL_VCPU_2GB_RAM_50GB_DISK("s-1vcpu-2gb-intel"),
	/**
	 * Basic, 1 Intel VCPU, 2GB memory, 70GB disk.
	 */
	BASIC_1_INTEL_VCPU_2GB_RAM_70GB_DISK("s-1vcpu-2gb-70gb-intel"),
	/**
	 * Basic, 2 VCPUs, 2GB memory, 60GB disk.
	 */
	BASIC_2_VCPU_2GB_RAM_60GB_DISK("s-2vcpu-2gb"),
	/**
	 * Basic, 2 AMD VCPUs, 2GB memory, 60GB disk.
	 */
	BASIC_2_AMD_VCPU_2GB_RAM_60GB_DISK("s-2vcpu-2gb-amd"),
	/**
	 * Basic, 2 Intel VCPUs, 2GB memory, 60GB disk.
	 */
	BASIC_2_INTEL_VCPU_2GB_RAM_60GB_DISK("s-2vcpu-2gb-intel"),
	/**
	 * Basic, 2 Intel VCPUs, 2GB memory, 90GB disk.
	 */
	BASIC_2_INTEL_VCPU_2GB_RAM_90GB_DISK("s-2vcpu-2gb-90gb-intel"),
	/**
	 * Basic, 2 VCPUs, 4GB memory, 80GB disk.
	 */
	BASIC_2_VCPU_4GB_RAM_60GB_DISK("s-2vcpu-4gb"),
	/**
	 * Basic, 2 AMD VCPUs, 4GB memory, 80GB disk.
	 */
	BASIC_2_AMD_VCPU_4GB_RAM_80GB_DISK("s-2vcpu-4gb-amd"),
	/**
	 * Basic, 2 Intel VCPUs, 4GB memory, 80GB disk.
	 */
	BASIC_2_INTEL_VCPU_4GB_RAM_80GB_DISK("s-2vcpu-4gb-intel"),
	/**
	 * Basic, 2 Intel VCPUs, 4GB memory, 120GB disk.
	 */
	BASIC_2_INTEL_VCPU_4GB_RAM_120GB_DISK("s-2vcpu-4gb-120gb-intel"),
	/**
	 * Basic, 2 AMD VCPUs, 8GB memory, 100GB disk.
	 */
	BASIC_2_AMD_VCPU_8GB_RAM_100GB_DISK("s-2vcpu-8gb-amd"),
	/**
	 * CPU-optimized, 2 VCPUs, 4GB memory, 25GB disk.
	 */
	CPU_OPTIMIZED_2_VCPU_4GB_RAM_25GB_DISK("c-2"),
	/**
	 * Basic, 2 Intel VCPUs, 8GB memory, 160GB disk.
	 */
	BASIC_2_INTEL_VCPU_8GB_RAM_160GB_DISK("s-2vcpu-8gb-160gb-intel"),
	/**
	 * Basic, 4 VCPUs, 8GB memory, 160GB disk.
	 */
	BASIC_4_VCPU_8GB_RAM_160GB_DISK("s-4vcpu-8gb"),
	/**
	 * Basic, 4 AMD VCPUs, 8GB memory, 160GB disk.
	 */
	BASIC_4_AMD_VCPU_8GB_RAM_160GB_DISK("s-4vcpu-8gb-amd"),
	/**
	 * Basic, 4 Intel VCPUs, 8GB memory, 160GB disk.
	 */
	BASIC_4_INTEL_VCPU_8GB_RAM_160GB_DISK("s-4vcpu-8gb-intel"),
	/**
	 * General-purpose, 2 VCPUs, 8GB memory, 25GB disk.
	 */
	GENERAL_PURPOSE_2_VCPU_8GB_RAM_25GB_DISK("g-2vcpu-8gb"),
	/**
	 * Basic, 4 Intel VCPUs, 8GB memory, 240GB disk.
	 */
	BASIC_2_INTEL_VCPU_8GB_RAM_240GB_DISK("s-4vcpu-8gb-240gb-intel"),
	/**
	 * General purpose 2x SSD, 4 VCPUs, 8GB memory, 50GB disk.
	 */
	GENERAL_PURPOSE_2X_SSD_4_VCPU_8GB_RAM_50GB_DISK("gd-2vcpu-8gb"),
	/**
	 * Basic, 4 AMD VCPUs, 16GB memory, 200GB disk.
	 */
	BASIC_4_AMD_VCPU_16GB_RAM_200GB_DISK("s-4vcpu-16gb-amd"),
	/**
	 * Memory-optimized, 2 VCPUs, 16GB memory, 50GB disk.
	 */
	MEMORY_OPTIMIZED_2_VCPU_16GB_RAM_50GB_DISK("m-2vcpu-16gb"),
	/**
	 * CPU-optimized, 4 VCPUs, 8GB memory, 50GB disk.
	 */
	CPU_OPTIMIZED_4_VCPU_8GB_RAM_50GB_DISK("c-4l");

	private final String slug;

	/**
	 * @param slug the slug associated with this type
	 */
	DropletType(String slug)
	{
		this.slug = slug;
	}

	/**
	 * Looks up the droplet type by its slug.
	 *
	 * @param slug the slug to look up
	 * @return the matching value
	 * @throws IllegalArgumentException if no match is found
	 */
	public static DropletType getBySlug(String slug)
	{
		for (DropletType type : values())
			if (type.slug.equals(slug))
				return type;
		throw new IllegalArgumentException("Slug not found: " + slug);
	}

	/**
	 * Returns the slug of the image.
	 *
	 * @return the slug of the image
	 */
	public String getSlug()
	{
		return slug;
	}

	@Override
	public String toString()
	{
		return slug;
	}
}