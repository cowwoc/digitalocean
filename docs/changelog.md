Minor updates involving cosmetic changes have been omitted from this list.

See https://github.com/cowwoc/digitalocean/commits/main for a full list.

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