Minor updates involving cosmetic changes have been omitted from this list.

See https://github.com/cowwoc/digitalocean/commits/main for a full list.

## Version 0.13 - 2025/07/24

* Renamed `*.Id` classes to `*Id`. For example, `Project.Id` is now `ProjectId`.
* Moved all IDs to the core module so now any module can reference other type of resources without needing to
  declare the associated module as a dependency.
* `DatabaseCreator.tags()`, `DatabaseCreator.firewallRules()`, `DropletCreator.tags()`,
  `KubernetesCreator.tags()`, `KubernetesCreator.nodePools()`, `KubernetesCreator.NodePoolBuilder.labels()`
  and `KubernetesCreator.NodePoolBuilder.taints()` no longer remove existing elements before adding new ones.

## Version 0.12 - 2025/07/18

* Renamed groupId from `com.github.cowwoc.digitalocean` to `io.github.cowwoc.digitalocean`.
* Split codebase into separate modules (e.g., compute, database, network).
* Renamed `Zone` to `Region` to match DigitalOcean's terminology.
* Separated interface and implementation of classes that interact with remote services in order to facilitate
  the use of test mocks.
* The code now throws `IOException` instead of `TimeoutException` in the case of I/O-related timeouts;
  signaling that the failure is likely to be intermittent.

## Version 0.11 - 2025/02/14

* Bugfix: `DockerRegistry.getCredentials()` was always requesting a 5-minute expiration instead of the
  duration specified by the user.
* Renamed `PermissionDeniedException` to `AccessDeniedException`.

## Version 0.9 - 2025/01/16

* Added `DigitalOceanClient.anonymous()`.
* Added `Database`.
* Added `hashCode()` and `equals()` to `DockerRegistry`, `DockerRepository` and `DockerImage`.
* Added `DockerRegistry.getRepositories()`, `getRepositoryByPredicate()`.
* Replaced `DockerRepository.getImageByTags()` with `getImageByPredicate()`.
* Added `Droplet.getAll()` and `getCreatedAt()`.
* Replaced `Droplet.getByAddress()`, `getByTags()` with `getByPredicate()`.
* `Droplet.creator()` no longer takes a `vpc` argument. Use `DropletCreator.vpc(Vpc)` instead.
* Renamed `Droplet.matchesState()` to `Droplet.matches()`.
* Added `id()` method to all classes. Resources now return references by ID instead of downloading the full
  state of the related resource.
* `DropletCreator.tags()` now replaces existing tags instead of adding to existing tags.
* Added `DropletCreator.backupSchedule()` and `failOnUnsupportedOperatingSystem()`.
* Replaced `DropletFeature.METRICS_AGENT` with `MONITORING`.
* Added `DropletFeature.FLOATING_IPS`, `FIREWALLS`, `LOAD_BALANCERS`, `SPACES`, `VOLUMES`.
* Added `DropletImage.getName()`, `getDistribution()`, `isPublic()`, `getZones()`, `getType()`,
  `getMinDiskSizeInGiB()`, `getSizeInGiB()`, `getDescription()`, `getTags()`, `getStatus()`,
  `getErrorMessage()` and `getCreatedAt()`.
* `DropletMetadata` methods no longer throw `TimeoutException`. Additionally, reduced the timeout to improve
  performance when running outside a droplet.
* `DropletType` and `Zone` now look up values at runtime instead of hard-coding them as an enum.
* Renamed `KubernetesClusterNotFoundException` to `KubernetesNotFoundException`.
* Renamed `Cluster` to `Kubernetes` and moved it to the `resource` package.
* Renamed `Kubernetes.configuration()` to `Kubernetes.creator()`.
* `Kubernetes.getByZone()` now returns a `Set` instead of a `List`.
* `Kubernetes.NodePool`'s constructor now takes a `Set<Node>` instead of `List<Node>`.
* Renamed `Kubernetes.NodePool.toConfiguration()` to `Kubernetes.NodePool.forCreator()`.
* Renamed `ClusterConfiguration` to `KubernetesCreator`.
* Renamed `Kubernetes.waitForDeletion()` to `waitForDestroy()`.
* `KubernetesCreator.nodePools` now takes a `Set<NodePool>` instead of a `List<NodePool>`.
* `DockerRegistry.getRepositories()`, `DockerRepository.getImages()`, `Droplet.getByName()`,
  `Droplet.getByTags()`, `Droplet.getAddresses()`, `Region.getZones()`, `SshPublicKey.getAll()`,
  `SshPublicKey.getByName()` now return a `Set` instead of a `List`.

## Version 0.8 - 2025/01/01

* Fixed bug that caused `Cluster.waitFor()` to hang.

## Version 0.7 - 2025/01/01

* Increased the logging frequency of `Cluster.waitFor()`.

## Version 0.6 - 2025/01/01

* Renamed `Cluster.get()` to `Cluster.getByZone()`.
* Renamed `SshPublicKey.list()` to `SshPublicKey.getAll()`.

## Version 0.3 - 2025/01/01

* Renamed `DigitalOceanScope` to `DigitalOceanClient`.
* Moved Kubernetes resources into their own package and renamed dropped `Kubernetes` from the name.
* Renamed `KubernetesClusterSpecification` to `ClusterConfiguration` and `NodePoolSpecification` to
  `NodePoolConfiguration`.

## Version 0.2 - 2024/12/30

* Use common definition of `Scope` from the pouch library.

## Version 0.1 - 2024/12/28

* Initial release.