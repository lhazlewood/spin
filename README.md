<!--
  ~ Copyright 2014 Les Hazlewood
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->
# Spin

Spin is a groovy-based command-line tool for resource and service orchestration. It allows you to install, start, 
stop, uninstall and obtain the status of any spin-defined resource or service.

* [Installation](#installation)
  * [Prerequisites](#prerequisites)
  * [Install](#install)
* [Usage](#usage)
* [Configuration](#configuration)
  * [spin.groovy](#spingroovy)
  * [Universal Service Configuration Options](#universal-service-configuration-options)
    <!--
    * [type](#service-type)
      * [heuristic](#service-type-heuristic)
    * [profiles](#service-profiles)
    * [enabled](#service-enabled)
    * [dependsOn](#service-dependsOn)
      * [evaluation order](#service-dependency-order)
    * [healthchecks](#service-healthchecks)
      * [command](#service-healthchecks-command)
      * [tries](#service-healthchecks-tries)
      * [sleep](#service-healthchecks-sleep)
    * [stdout](#service-stdout)
      * [enabled](#service-stdout-enabled)
      * [file](#service-stdout-file) -->
* [Plugins](#plugins)
  * [Docker Machine](#dockerMachine)
      <!-- * [machine](#dockerMachine-machine)
      * [certs](#dockerMachine-certs) 
      * [driver](#dockerMachine-driver)
        * [name](#dockerMachine-driver-name)
        * [options](#dockerMachine-driver-options)
      * [routes](#dockerMachine-routes) -->
  * [Docker](#docker)
      <!-- * [cpu_shares](#docker-cpuShares)
      * [environment](#docker-environment)
      * [hostname](#docker-hostname)
      * [image](#docker-image)
      * [links](#docker-links)
      * [mem_limit](#docker-memLimit)
      * [options](#docker-options)
      * [ports](#docker-ports)
      * [ulimits](#docker-ulimits)
      * [volumes](#docker-volumes) -->
  * [Executable Jar](#exejar)
      <!-- * [args](#exejar-args)
      * [artifact](#exejar-artifact)
      * [jar](#exejar-jar)
      * [options](#exejar-options)
      * [systemProperties](#exejar-systemProperties)
      * [workingDir](#exejar-workingDir) -->
<!-- * [Developing Custom Plugins](#developing-custom-plugins) -->

## Installation

### Prerequisites

Spin requires Java 8 or later in your `$PATH`. For example, using Homebrew:

```bash
brew cask install java
```

If you do not use Homebrew, download and install [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads) or 
later and ensure that `$JAVA_HOME` is set and that `$JAVA_HOME/bin` directory is in your `$PATH`.  For example, 
in `~/.bash_profile`:

```bash
export JAVA_HOME="$(/usr/libexec/java_home)"
export PATH="$JAVA_HOME/bin:$PATH"
```

### Install

Mac via Homebrew:

```bash
brew install lhazlewood/tap/spin
# ensure this newly installed package is not overridden by the 
# homebrew default 'spin' package (which is completely unrelated):
brew pin spin
```
    
If you do not use homebrew, download and install the `spin` zip package to a location you prefer.  For example:

```bash
SPIN_VERSION="0.1.0"
mkdir -p ~/devtools/spin
pushd ~/devtools/spin
curl -LO "http://repo1.maven.org/maven2/com/leshazlewood/spin/spin/$SPIN_VERSION/spin-$SPIN_VERSION.zip"
unzip "spin-$SPIN_VERSION.zip"
ln -s "spin-$SPIN_VERSION" current
popd
```

And don't forget to add the package's `bin` directory to your path.  For example in `~/.bash_profile`:

```bash
export SPIN_HOME="$HOME/devtools/spin/current"
export PATH="$SPIN_HOME/bin:$PATH"
```

## Usage

Run `spin help` to see options and commands:

```
lhazlewood@Less-MacBook-Pro:~$ spin help
Usage: spin [options] <command> [<service-name>]

Options:
  -h, -help, --h, --help       Show help
  -f <spin-config-file>        Use <spin-config-file> instead of searching default file locations
  -p name[,name2,name3,...]    A comma-delimited list of profiles to enable
  -e <environment-name>        Enable environment configuration for the specified <environment-name>

Commands:
  help                         Show help
  install                      Install all uninstalled services
  install <service-name>       Install the service named <service-name>
  start                        Starts all services
  start <service-name>         Starts only the service named <service-name>
  status                       Show the status of all services
  status <service-name>        Show the status of <service-name>
  stop                         Stops all services
  stop <service-name>          Stops only the service named <service-name>
  uninstall                    Uninstalls all installed services
  uninstall <service-name>     Uninstalls the service named <service-name>
```

## Configuration

When spin executes, it will by default look for a services configuration file located in one of the following 
locations, in order (first one found wins):

* `$PWD/spin.groovy`
* `$HOME/.spin/spin.groovy`

If you want to specify a different services config (i.e. the 'context' that spin will assume), you can use the 
`-f <spin-config-file>` option as described in the help output above. For convenience, we'll assume that you'll 
configure a default file.

### spin.groovy

This file is a [Groovy configuration file](http://docs.groovy-lang.org/latest/html/documentation/#_configslurper) with 
a top-level `services` block. The `services` block contains one or more nested named blocks. Each nested name is the 
name of a service. It's associated block is a service definition. You can install, start, stop, uninstall and obtain 
the runtime status of a service by using its spin service name.

Here is an example config:

```groovy
services {

  vm {
    type = 'dockerMachine'
    profiles = 'all'
    machine = 'dev'
    driver {
      name = 'virtualbox'
      options {
        memory = 4096
      } 
    }
  }
  
  zookeeper {
    type = 'docker'
    image = 'zookeeper:3.4.7'
    ports = ['2181:2181', '2888:2888', '3888:3888']
    healthchecks {
      ready {
        command = "docker exec zookeeper /zk/bin/zkServer.sh status"
      }
    }
  }
  
  kafka {
    type = 'docker'
    dependsOn = 'zookeeper'
    image = 'kafka:0.9.0.0'
    ports = ['9092:9092']
    links = ['zookeeper:zk']
    healthchecks {
      ready {
        command = "docker logs --tail=15 kafka | grep ', started (kafka.server.KafkaServer)'"
      }
    } 
  }
  
  webapp {
    type = 'exejar'
    artifact {
        groupId = 'mycompany'
        artifactId = 'mywebapp'
        version = '1.2.1'
    }
    // instead of a maven artifact, you could specify a file path
    // via the 'jar' property, for example:
    //jar = 'path/to/mywebapp-1.2.1.jar'
    stdout = './application.log'
    options = ['-Xms256m', '-Xmx1g']
    systemProperties {
        'spring.main.show_banner' = false
        foo = 'bar' 
    }
    args = ['hello', 'world']
    healthchecks {
      ready {
        command = "curl localhost:8080 -s -f -o /dev/null"
        sleep = 5
      }
    }
  }
}
```

The above configuration indicates there are four services defined:

* a docker-machine virtual machine named `vm`
* a docker service named `zookeeper`
* a docker service named `kafka`
* an executable jar web application named `webapp`

When interacting with `spin` you can control any service using its name. For example:

```bash
spin start zookeeper
spin status zookeeper
spin stop zookeeper
```

You can add more services by adding more top-level _service-name_-to-_service-definition_ pairs like the ones above.

Each type of service (docker, exejar, etc) has its own configuration options, and those will be covered later.

### Universal Service Configuration Options

While each type of service has its own configuration options, there are a few options that are universal to any 
defined service:

* [type](#service-type)
* [profiles](#service-profiles)
* [enabled](#service-enabled)
* [dependsOn](#service-dependsOn)
* [healthchecks](#service-healthchecks)
* [stdout](#service-stdout)

#### <a name="service-type"></a> type

The `type` property is required and defines the type of plugin that will be used to execute any spin command specified 
for the service.

The field value is a string, and assumes a heuristic: the value is actually a lower-case version of a Groovy class file
with a name that ends with '`Plugin.groovy`' and that file is present in either the following two directories: 

1. `$HOME/.spin/plugins`
2. `<spin install directory>/plugins`

The above directory locations are searched in the order listed above. Once a plugin is found it is returned 
immediately, short-circuiting any remaining locations.

##### <a name="service-type-heuristic"></a> heuristic

The plugin name heuristic is as follows, using the example name `docker`

1. Take the plugin name and uppercase the first character: `docker` --> `Docker`
2. Take the resulting capitalized name and suffix it with Plugin.groovy: `Docker` --> `DockerPlugin.groovy`
3. Look for a file with this name in the above listed plugin directories. Use the first one found.

This flexible nature allows you to define custom plugins as desired for any type of service. Information on creating 
custom plugins will be covered later.

#### <a name="service-profiles"></a> profiles

The `profiles` property is optional. It defines one or more profiles that must be enabled when running `spin` (by using
the `-p name,name2,...` flag) in order for the service to be enabled.

The profiles property value may be either a single string value or a list of string values, e.g. `['foo', 'bar', ...]`

The above example configuration specifies a `profiles` value of `all`:


```groovy
  vm {
    type = 'dockerMachine'
    profiles = 'all'
    // etc...
  }
```

This ensures the `vm` service will only be invoked when the `all` profile is enabled. For example:

```bash
spin -p all start
```

Similarly, running just `spin start` will not interact with the `vm` service.

Profiles are useful because you may not want to start and stop certain services every time you run `spin`.

For example, you may want the `vm` virtual machine to always be running behind the scenes while you bulk start/stop 
other services. This allows you to run, say, `spin stop` and the other services will be stopped, but `vm` will not be 
stopped.

Service definitions without a `profiles` property will always be executed, unless they are disabled via the `enabled` 
property. However, because `profiles` allows you more options of when a plugin is enabled or not, it is usually 
preferred over using the `enabled` property. For example, you could specify a `disabled` profile, and as long as you 
never run `spin -p disabled`, that service will not be invoked.

#### <a name="service-enabled"></a> enabled

The `enabled` property is optional and allows you to define whether or not the service will be enacted upon at all when
executing `spin`. It is mostly convenient for entirely disabling a service without having to comment it out or remove 
it from the spin configuration file.

If you set this property to `false`, the corresponding service will not be invoked when executing spin.

If you set this property to `true` (or do not define the property at all), the service configuration is evaluated.

Because the `profiles` property supports more flexible configuration of when a service is enabled or not, it is 
generally recommended to use the `profiles` property instead of `enabled` in most cases.

#### <a name="service-dependsOn"></a> dependsOn

The `dependsOn` property is optional and allows you to define one or more services that should be executed first 
before executing the current service. 

The `dependsOn` property value may be either a single string value or a list of string values, e.g. 
`['foo', 'bar', ...]`

In the above example, the `kafka` service indicates that it depends on `zookeeper`:

```groovy
  kafka {
    type = 'docker'
    dependsOn = 'zookeeper'
    // etc...
  }
```

##### <a name="service-dependency-order"></a>Service Evaluation Order

All `dependsOn` usages are evaluated to form a service Directed Acyclic Graph (DAG) (via a 
[topological sort](https://en.wikipedia.org/wiki/Topological_sorting) with 
[Tarjan's algorithm](http://www.geeksforgeeks.org/tarjan-algorithm-find-strongly-connected-components/) if you're
curious) to ensure correct command execution order across services.

When running `spin install`, `spin start` or `spin status`, the `install`, `start`, or `status` command is guaranteed
to execute on required services first before executing the command on depending services. In the above example, calling
`spin start` ensures that `start` is executed for `zookeeper` first before calling `start` for `kafka`.

When running `spin stop` or spin `uninstall`, the command execution order is reversed. This ensures that required 
services are stopped or uninstalled _after_ dependent services. In the above example, calling `spin stop` ensures that
`stop` is executed for `kafka` first before calling `stop` for `zookeeper`.

#### <a name="service-healthchecks"></a> healthchecks

The `healthchecks` property is optional. It allows you to specify one or more healthchecks that must pass after 
starting a service before the service is seen as healthy. Each defined healthcheck must successfully complete in a 
certain amount of time - if it does not, spin treats this as a failed startup and will exit accordingly.

If present, the `healthchecks` property must be a `Map` of one or more _healthcheck-name_ (a `String`) to 
_healthcheck-definition_ (a `Map`) pairs. For example:

```groovy
services {
  
  myservice {
    // etc...
    healthchecks {
      ready {
        command = '<shell command here>'
        tries = 20
        sleep = 3
      }
    }
  } 
}
```

This example has one healthcheck named `ready`, but this name can be whatever string you want that makes sense for 
your healthcheck. Any number of named healthcheck definitions may be defined within `healthchecks`.

A healthcheck definition may have 3 properties:

* [command](#service-healthchecks-command)
* [tries](#service-healthchecks-tries)
* [sleep](#service-healthchecks-sleep)

##### <a name="service-healthchecks-command"></a> command

This is a required String, and must be a shell command (or a sequence of commands piped together). If the 
command/commands return(s) with an exit value of `0` (zero), then the healthcheck is considered successful, and no 
further tries for _that particular healthcheck definition_ will be attempted. An early success return for one 
healthcheck does not impact other healthchecks in any way - all defined healthchecks for a service must pass for the 
service to be considered successfully started.

##### <a name="service-healthchecks-tries"></a> tries

The `tries` property specifies the (integer) number of attempts the `command` will be executed before giving up and 
indicating that the service is unhealthy and has failed to start. The first time the command executes successfully 
(with an exit value of zero), all subsequent attempts are skipped.

The `tries` property is optional. If not specified, the default value is `20` tries.

##### <a name="service-healthchecks-sleep"></a> sleep

The `sleep` property specifies the (integer) number of seconds to sleep/wait after an unsuccessful healthcheck 
attempt before executing the next attempt.

The `sleep` property is optional. If not specified, the default value is `3` seconds.

#### <a name="service-stdout"></a> stdout

Most successful invocations of underlying service commands result in verbose output that ins't necessary when 
running `spin`, so spin does not relay the underlying service shell command success stdout by default. However, this 
output could be beneficial in some cases, especially if trying to find out why a command failed for a particular 
service. If you want to enable stdout when running a spin command for a service, you can set this property to one of 
two values: either the String `enabled` or a String file path.

##### <a name="service-stdout-enabled"></a> enabled

Setting `stdout` to a String value of `enabled` will print out anything from the service's stdout or stderr streams 
directly to the same console that is running spin.

##### <a name="service-stdout-file"></a> File Path

If you set the `stdout` property value to a String value that is not equal to the literal values `enabled`, 
`disabled`, `true`, or `false`, then spin interprets this string as a File path. Spin will redirect output of the 
target service (stdout and stderr) to the specified file.

## Plugins

The following list of plugins below are those that are available out-of-the-box when you install spin.

* [dockerMachine](#dockerMachine)
* [docker](#docker)
* [exejar](#exejar)

### <a name="dockerMachine"></a> dockerMachine

The `dockerMachine` plugin allows managing the lifecycle of a docker-machine-based virtual machine (the VM itself, 
not any docker containers that run inside it). It is enabled by setting the service definition's `type` property to 
`dockerMachine` (case sensitive).

The `dockerMachine` plugin is a spin-specific abstraction on top of the `docker-machine` executable; `docker-machine`
must already be installed and in your `$PATH`.

An example configuration:

```groovy
  vm {
    type = 'dockerMachine'
    profiles = 'mac'
    machine = 'dev'
    driver {
      name = 'virtualbox'
      options {
        memory = 4096
      }
    }
    certs {
      'docker.yourcompany.com' {
        clientCert = '~/certs/client.cert'
        clientKey = '~/certs/client.key'
        caCert = 'http://ca.yourcompany.com/Company-Root-CA.pem'
      }
    }
    routes = ['bridge']
  }
```

The `dockerMachine` plugin currently supports the following config properties:

* [certs](#dockerMachine-certs)
* [driver](#dockerMachine-driver)
* [machine](#dockerMachine-machine)
* [routes](#dockerMachine-routes)

#### <a name="dockerMachine-certs"></a> certs

The `certs` property allows you to specify 
[client or CA certificates](https://docs.docker.com/engine/security/certificates/) that are required when 
accessing one or more docker registry servers.  This is helpful for organizations that have private registries and use TLS 
client or server (or both) TLS authentication.  `caCerts` is optional.

The `certs` property contains one or more named blocks where each block name is the host name (and optional port) 
of a docker registry.  The following example shows two named blocks within `certs`:

```groovy
    // ...
    certs {
      'docker.yourcompany.com' {
        clientCert = '~/certs/client.cert'
        clientKey = '~/certs/client.key'
        caCert = 'http://ca.yourcompany.com/Company-Root-CA.pem'
      }
      'docker2.yourcompany.com:8080' {
        caCert = '~/.mycompany/ca-cert.pem'
      }
    }
    // ...
```

This example indicates that two docker registries may be accessed when installing docker containers to the specified 
docker machine - one registry is accessible via just a host name `docker.yourcompany.com` and the other registry via a
hostname and port, `docker2.yourcompany.com:8080`

The cert files referenced within the blocks will be automatically placed in the corresponding docker machine's file 
system under `/etc/docker/certs.d/` in a directory equal to the block name.  That is, based on the example above, 
there will be two directories created in the respective docker machine's file system:

* `/etc/docker/certs.d/docker.yourcompany.com`
* `/etc/docker/certs.d/docker2.yourcompany.com:8080`

Thes are _directories_, not files.  The files defined in each named block will be placed within these respective
directories.  More information on this can be found in docker's documentation on 
[repository client certificates](https://docs.docker.com/engine/security/certificates/).

Each named block may contain 3 properties:
 
* [caCert](#dockerMachine-certs-caCert)
* [clientCert](#dockerMachine-certs-clientCert)
* [clientKey](#dockerMachine-certs-clientKey)

##### <a name="dockerMachine-certs-caCert"></a> caCert

The `caCert` property specifies the public certificate of the Certificate Authority that signed the docker registry's 
TLS certificate.  This allows the docker client to authenticate the registry server when downloading a container.

Specifying this property is mostly only necessary when the registry server uses a TLS cert that is signed by a 
private certificate authority, such as one at your company/employer.  If your registry server uses a TLS cert 
signed by a Well Known Internet certificate authority (like Digicert, Thawte, Verisign, etc), it is likely that 
you do not need to configure this value.

An example:

```groovy
    // ...
    certs {
      'docker.yourcompany.com' {
        caCert = 'http://ca.yourcompany.com/Company-Root-CA.pem'
      }
    }
    // ...
  }
```

The `caCert` value can be a string file path, an `http` or `https` URL, or even a `File` instance. For example:

* `/fully/qualified/path/to/ca-cert.pem`
* `$HOME/.mycompany/ca-root.pem`
* `~/mycompany/ca.pem`
* `http://ca.mycompany.com/root.pem`
* `new File("wherever/ca.pem")`

The source file path or URL can be named anything, but when the file is copied into the docker machine file system, 
it will reside at the following path and name as required by docker conventions:

`/etc/docker/certs.d/{registryHost}/ca.crt`

where `{registryHost}` equals the registry host (i.e. block name).

**Note: `caCert` allows the docker client to authenticate a registry server. If that registry server requires the 
docker client to authenticate itself with TLS authentication, you'll need to specify the
[clientCert](#dockerMachine-certs-clientCert) and [clientKey](#dockerMachine-certs-clientKey) properties as well.**

##### <a name="dockerMachine-certs-clientCert"></a> clientCert

The `clientCert` property specifies the _public_ certificate that the docker client should use when authenticating
itself with a docker registry server.  If the docker registry server requires TLS authentication, this property will
be required when the docker client attempts to download a docker container.

If the docker registry server does not require the docker client to authenticate itself, you do not need to specify
`clientCert`or `clientKey`. But **if you specify `clientCert`, you must also specify the 
[clientKey](#dockerMachine-certs-clientKey) property.**

For example:

```groovy
    // ...
    certs {
      'docker.yourcompany.com' {
        clientCert = '~/mycompany/client.cert'
        clientKey = '~/mycompany/client.private.key'
      }
    }
    // ...
  }
```

The `clientCert` value can be a string file path, an `http` or `https` URL, or even a `File` instance. For example:

* `/fully/qualified/path/to/ca-cert.pem`
* `$HOME/.mycompany/ca-root.pem`
* `https://company.com/myclient/client.cert`
* `~/mycompany/ca.pem`
* `new File("wherever/ca.pem")`

The source file path or URL can be named anything, but when the file is copied into the docker machine file system, 
it will reside at the following path and name as required by docker conventions:

`/etc/docker/certs.d/{registryHost}/client.cert`

where `{registryHost}` equals the registry host (i.e. block name).

##### <a name="dockerMachine-certs-clientKey"></a> clientKey

The `clientKey` property specifies the _private_ key that the docker client should use when authenticating
itself with a docker registry server.  If the docker registry server requires TLS authentication, this property will
be required when the docker client attempts to download a docker container.

If the docker registry server does not require the docker client to authenticate itself, you do not need to specify
`clientKey`or `clientCert`. But **if you specify `clientKey`, you must also specify the 
[clientCert](#dockerMachine-certs-clientCert) property.**

For example:

For example:

```groovy
    // ...
    certs {
      'docker.yourcompany.com' {
        clientCert = '~/mycompany/client.cert'
        clientKey = '~/mycompany/client.private.key'
      }
    }
    // ...
  }
```

The `clientKey` value can be a string file path, an `http` or `https` URL, or even a `File` instance. For example:

* `/fully/qualified/path/to/client.private.key`
* `$HOME/.mycompany/.certs/client-private.key`
* `~/mycompany/client.cert`
* `new File("wherever/client.cert")`

The source file path or URL can be named anything, but when the file is copied into the docker machine file system, 
it will reside at the following path and name as required by docker conventions:

`/etc/docker/certs.d/{registryHost}/client.key`

where `{registryHost}` equals the registry host (i.e. block name).

#### <a name="dockerMachine-driver"></a> driver

The `driver` property is a map that specifies the driver to use when creating the virtual machine.  The map may contain
the following properties:

* [name](#dockerMachine-driver-name)
* [options](#dockerMachine-driver-options)

##### <a name="dockerMachine-driver-name"></a> name

The driver `name` property specifies the docker-machine driver name to use when creating the virtual machine. The 
above example configuration example specifies that the `virtualbox` driver should be used.

##### <a name="dockerMachine-driver-options"></a> options

The driver `options` property is a set of driver-specific name/value pairs to supply to the driver when creating the 
virtual machine. The above configuration example specifies that the `virtualbox` driver should use `4096` megs of
ram when creating the virtual machine.

The options specified here are applied to the `docker-machine create` command using the standard docker-machine 
_--drivername-optionname-optionvalue_ flag convention on the command line. This implies that the above configuration 
example adds the following to the docker- machine create command:

`--virtualbox-memory 4096`

#### <a name="dockerMachine-machine"></a> machine

The `machine` property is required. The value is the name of the docker machine to use when executing `docker-machine`.

#### <a name="dockerMachine-routes"></a> routes

The `routes` property can be a single string or a list-of-strings. It is optional.

**NOTE: If this property is non-null, sudo is required to modify the local operating system's routing table. Spin will prompt for the sudo password when required.**

##### spin start

When the docker host is started via `spin start`, spin will prompt for the sudo password and then automatically 
create routing table entries in the local operating system to the named docker networks within the docker host.

For example, assume that the `routes` value equals `bridge`, the docker 
[default network](https://docs.docker.com/engine/userguide/networking/#default-networks). When the docker host is 
started via `spin start`, the `dockerMachine` plugin will:

1. Discover the docker `bridge` network's subnet (by running `docker network inspect bridge`, inspecting the 
resulting JSON, and using the `[0].IPAM.Config[0].Subnet` value). For example: `172.17.0.0/16`
2. Discover the docker host's IP address (by running `docker ip` _machine_, where _machine_ is the value of the spin 
   `machine` config property listed above). For example: `192.168.99.100`
3. Add a route to the local operating system to ensure packets intended for the named network are routed to the 
   docker host (which acts as a gateway for that network). For example: `route -n add 172.17.0.0/16 192.168.99.100`

This ensures that any packets sent by the local operating system to a specified subnet will automatically be routed to 
the docker host's assigned IP address. The above example means packets destined for `172.17.0.0/16` will be routed to 
`192.168.99.100`, whereby the docker host will then likely route those packets to a target docker container.

Routes are created for each specified docker network name to ensure each network is routable.

##### spin stop

When using spin to stop the docker host via `spin stop`, all routes for each named docker network are automatically 
removed from the local operating system routing table.

### docker

The docker plugin manages the lifecycle of a single docker container. It is enabled by setting the service 
definition's `type` property to `docker`. The docker plugin automatically sets the `-d` and `–name` *`serviceName`* 
flags when invoking `docker run` to ensure the docker container runs as background service and is accessible/queryable
by using the spin service name, respectively.

An example configuration:

```groovy
  cassandra1 {
    type = 'docker'
    dependsOn = 'vm'
    image = 'cassandra:2'
    ports = ['9160:9160', '9042:9042', '7199:7199', '7001:7001', '7000:7000']
    environment {
      CASSANDRA_START_RPC = true
    }
    healthchecks {
      ready {
        command = "docker exec cassandra1 /usr/bin/cqlsh -e 'describe KEYSPACES' | grep system"
      } 
    }
  }
  
  cassandra2 {
    type = 'docker'
    dependsOn = 'cassandra1'
    image = 'cassandra:2'
    links = ['cassandra1:cassandra']
    environment {
      CASSANDRA_START_RPC = true
    }
    healthchecks {
      ready {
        command = "docker exec cassandra2 /usr/bin/cqlsh -e 'describe KEYSPACES' | grep system"
      } 
    }
  }
```

The docker plugin supports the following configuration options:

* [cpu_shares](#docker-cpuShares)
* [environment](#docker-environment)
* [hostname](#docker-hostname)
* [image](#docker-image)
* [links](#docker-links)
* [mem_limit](#docker-memLimit)
* [options](#docker-options)
* [ports](#docker-ports)
* [ulimits](#docker-ulimits)
* [volumes](#docker-volumes)

#### <a name="docker-cpuShares"></a> cpu_shares

The `cpu_shares` property is optional. It allows you to set the
[CPU Shares Constraint](https://docs.docker.com/engine/reference/run/#cpu-share-constraint) on a container.

#### <a name="docker-environment"></a> environment

The `environment` property is optional. If specified, it must be either a 
[list or a map of environment variables](https://docs.docker.com/compose/compose-file/#environment) to expose to the 
docker container.

#### <a name="docker-hostname"></a> hostname

The `hostname` property is optional. It allows you to set the 
[docker container hostname](https://docs.docker.com/engine/reference/run/).

#### <a name="docker-image"></a> image

The `image` property is required. The value is the name of the docker image name or id (and optionally the image 
version number) to use when creating the docker container.

Some example image values:

```groovy
  image = 'ubuntu'
  image = 'orchardup/postgresql'
  image = 'a4bc65fd'
```

#### <a name="docker-links"></a> links

The `links` property is optional. It is an array of strings. It allows you to link a container to other containers in 
another service. Either specify both the service name and the link alias (`SERVICE:ALIAS`), or just the service name 
(which will also be used for the alias). Example:

```groovy
  links = ['db', 'db:database', 'hazelcast']
```

An entry with the alias’s name will be created in `/etc/hosts` inside containers for this service, e.g:

```bash
  172.17.2.186  db
  172.17.2.186  database
  172.17.2.187  hazelcast
```

Environment variables will also be created - see the 
[docker environment variable reference](https://docs.docker.com/compose/environment-variables/) for details.

#### <a name="docker-memLimit"></a> mem_limit

The `mem_limit` property is optional. It allows you to set a container's upper 
[memory limit](https://docs.docker.com/engine/reference/run/#user-memory-constraints).

#### <a name="docker-options"></a> options

The `options` property is optional. It is a convenience/catch-all list-of-strings property that allows you to specify 
any additional options that are not already supported by the spin docker config properties.

It allows you to still use a docker run option if the spin docker plugin does not yet support it via .groovy config. 
Each value in the list is appended directly to the `docker run` command without modification.

#### <a name="docker-ports"></a> ports

The `ports` property is optional. It is a list of container ports (as Strings) to expose. Either specify both ports 
(`HOST:CONTAINER`), or just the container port (and a random host port will be chosen).

Example port declaration:

```groovy
  ports = [
    '3000',
    '3000-3005',
    '8000:8000',
    '9090-9091:8080-8081',
    '49100:22',
    '127.0.0.1:8001:8001',
    '127.0.0.1:5000-5010:5000-5010'
  ]
```

#### <a name="docker-ulimits"></a> ulimits

The `ulimits` property is optional. It allows you to override a container's default ulimits. You can either specify a 
single limit as an integer or soft/hard limits as a mapping. For example:

```groovy
  ulimits = 65535
  // this is the same as the following:
  // ulimits {
  //   nproc = 65535
  // }
```
or

```groovy
  ulimits {
    nproc = 65535
    nofile {
      soft = 20000
      hard = 40000
    } 
  }
```

Also see the [docker run --ulimit flag documentation](https://docs.docker.com/engine/reference/commandline/run/#set-ulimits-in-container---ulimit) for more.

#### <a name="docker-volumes"></a> volumes

The `volumes` property allows you to mount paths as volumes, optionally specifying a path on the host machine 
(`HOST:CONTAINER`), or an access mode (`HOST:CONTAINER:ro`). Example:

```groovy
  volumes = [
    '/var/lib/mysql',
    './cache:/tmp/cache',
    '~/configs:/etc/configs/:ro'
  ]
```
For more information see the 
[docker run -v](https://docs.docker.com/engine/reference/commandline/run/#mount-volume--v---read-only) flag 
documentation and the general [Docker Volumes](https://docs.docker.com/engine/tutorials/dockervolumes/) documentation.

### exejar

The exejar plugin allows you to execute and run an executable jar as a system service. It is enabled by setting the 
service definition's `type` value to `exejar`

Service definition example:

```groovy
  webapp {
    type = 'exejar'
    artifact {
        groupId = 'mycompany'
        artifactId = 'mywebapp'
        version = '1'
    }
    // instead of a maven artifact, you could specify a file path
    // via the 'jar' property, for example:
    //jar = 'path/to/jar/file'
    stdout = './application.log'
    options = ['-Xms256m', '-Xmx1g']
    systemProperties {
        'spring.main.show_banner' = false
        foo = 'bar' 
    }
    args = ['hello', 'world']
    healthchecks {
      ready {
        command = "curl localhost:8080 -s -f -o /dev/null"
        sleep = 5
      }
    }
  }
```

When `spin start` is called for this definition, spin will execute the following Java command and turn it into a 
background service:

```bash
java -Xms256m -Xmx1g -Dspring.main.show_banner=false -Dfoo=bar -jar mywebapp-1.jar hello world
```
 
In addition to the universal configuration properties, the following additional properties are supported:

* [args](#exejar-args)
* [artifact](#exejar-artifact)
* [jar](#exejar-jar)
* [options](#exejar-options)
* [systemProperties](#exejar-systemProperties)
* [workingDir](#exejar-workingDir)

#### <a name="exejar-args"></a> args

`args` is an optional array of program arguments to supply to the executable jar itself. These args will be passed to 
the jar's designated `public static void main(String[] args)` method.

For example, the following config:

```groovy
  webapp {
    artifact {
      groupId = 'foo'
      artifactId = 'bar'
      version = '0.1.0'
    }
    args = ['hello', 'world']
  }
```

will result in the following Java command being executed (`args` in bold):

`java -jar bar-0.1.0.jar`**`hello world`**

Program (`main` method) arguments for the executable jar are declared _after_ the referenced jar name. Options to the 
java command itself (for the JVM) come _before_ the `-jar` flag. These options can be defined as `options` and 
`systemProperties` declarations, covered below.

#### <a name="exejar-artifact"></a> artifact

`artifact` is a map that contains valid Maven `groupId`, `artifactId` and `version` values of the .jar you want to 
execute.

Spin will use this metadata, and, under the hood, use the Maven Dependency Plugin to auto-download the specified .jar 
from an appropriate Maven repository server (assumes you have `$HOME/.m2/settings.xml` setup correctly). 

Either the `artifact` or the `jar` property must be specified.

#### <a name="exejar-jar"></a> jar

`jar` is a `File` or a `String` path that reflects the executable jar location on the file system. The specified file 
must exist, must be a file (not a directory) and must end with the name '.jar'.

Either the `jar` or the `artifact` property must be specified.

#### <a name="exejar-options"></a> options

`options` is an optional array of values to add as arguments to the `java` command before the `-jar` flag. You may 
find the available options by running the `java` command on the command line and looking at the resulting list of 
options (for example `-Xmx` options or `-version` etc).

An example service definition with options:

```groovy
    webapp {
      artifact {
        groupId = 'foo'
        artifactId = 'bar'
        version = '0.1.0'
      }
    options = ['-Xms256m', '-Xmx1g']
}
```

this definition will result in the following Java command being executed (options in bold): 

`java `**`-Xms256m -Xmx1g`**`-jar bar-0.1.0.jar`

#### <a name="exejar-systemProperties"></a> systemProperties

`systemProperties` is optional. Each `name: value` pair defined in this map will automatically be appended as 
`-Dname="value"` system property declarations in the java `options` list above.

This is a convenience property - identical behavior can be achieved by using `options` alone. For example, the 
following two `exejava` nested declarations achieve the exact same effect:

Using `systemProperties`:
```groovy
  // ...
  systemProperties {
    foo = 'bar'
    hello = 'world'
  }
```
Using `options`:
```groovy
  // ...
  options = [
    '-Dfoo=bar',
    '-Dhello=world'
  ]
```

Even though both are effectively the same thing, defining many `-D` pairs in opts can be cumbersome and perhaps more 
difficult to read and understand.

#### <a name="exejar-workingDir"></a> workingDir

The `workingDir` property is optional, and if specified, must be a `File` or a `String` path that reflects a directory 
on the file system.

If specified, the java process will be launched in that directory; it is the location that will be returned if the 
process calls `System.getProperty("user.dir");`

If not specified, the current working directory when executing spin will be used, i.e. `$PWD`

<!-- 

#### samza

The samza plugin allows managing the lifecycle of a Samza job packaged as a tarball. It is enabled by setting the 
service definition's `type` property to `samza`

In addition to the universal configuration properties, the following additional properties exist. 

...create section TOC here

Service definition example:

```groovy
  mySamzaService {
    type = 'samza'
    dependsOn = 'kafka'
    artifact {
      groupId = 'com.foo.whatever'
      artifactId = 'whatever-artifact-name'
      version = '0.1.0-SNAPSHOT'
    }
    java {
      // Each list element will be appended directly to JAVA_OPTS
      opts = ['-Xms1g', '-Xmx1g']
      // each name: value pair here will automatically be appended to JAVA_OPTS as -Dkey=value:
      systemProperties = [foo: 'bar', baz: 'boo']
    }
    config {
      // default value assumes config/deploy.properties, relative to the root of the tarball:
      // but a different properties file can be specified here if desired:
      file = 'config/deploy.properties'
      overrides {
        // Any overrides to the default config/deploy.properties file as key: value pairs:
        foo = 'bar'
        hello = 'world'
      }
    } 
  }
```

##### artifact

Either `artifact` or `file` must be specified. If specifying `artifact`, it reflects the required Maven metadata of 
the Samza tarball in your Maven repository server that you want to use. Spin will use this metadata, and, under the 
hood, use the Maven Dependency Plugin to auto-download the specified Samza tarball from the Maven repository server 
(assumes you have `$HOME/.m2/settings.xml` setup correctly).

`groupId`, `artifactId` and `version` fields are required. `type` and `classifer` fields are optional and default to 
`tar.gz` and `dist` respectively.

##### config

The `config` property is optional. It allows you to override the default base deployment properties file if desired as 
well as set any system properties without needing to edit the properties file.

###### file

The config `file` property is optional. The default value is `config/deploy.properties` and this file is expected to 
be in the Samza tarball downloaded from the Maven repository. If this file is not present in the tarball, or you just 
wish to use another config file instead of the default, you can specify the config `file` field. The value can be 
either an absolute file path or a path relative to the tarball root.

###### overrides

The config `overrides` section is optional. It allows you to override any of the properties defined in the config 
file (either the default or the explicitly specified one) by setting `name: value` pairs. Each `name: value` pair 
defined in this map will be translated to `–config name=value` declarations being appended to the Samza 
`bin/run-job.sh` call.

For example, assume that the `config/deploy.properties` file had a property value inside it:

```properties
systems.kafka.consumer.zookeeper.connect=172.17.42.1:2181
```

and assume the following config `overrides` section:

```groovy
  //...
  config {
    overrides {
      'systems.kafka.consumer.zookeeper.connect' = 'localhost:2181'
    }
  }
```

In this case, when spin starts the Samza service, it will append a 
`–config systems.kafka.consumer.zookeeper.connect=localhost:2181` flag when invoking Samza's `bin/run-job.sh` script. 
This in turn ensures that the Samza process sees a runtime value for 
`systems.kafka.consumer.zookeeper.connect` of `localhost:2181`, _not_ the default .properties file value of 
`172.17.42.1:2181`.

##### file

Either `artifact` or `file` must be specified. If specifying `file`, the value is the (String) file system path of the
Samza tarball file to be deployed. Absolute file paths will be accessed as expected. Relative paths are relative to 
the working directory where spin is executed.

##### java

The java section is optional. Any values you set here will automatically be included in the `JAVA_OPTS` shell 
environment variable used by Samza's `run-job.sh` script.

###### opts

`opts` is optional. Any values found in the opts list-of-strings will be appended directly to the `JAVA_OPTS` variable 
without modification.

###### systemProperties

`systemProperties` is optional. Each `name: value` pair defined in this map will automatically be appended as 
`-Dname="value"` system property declarations to the `JAVA_OPTS` variable.

This is a convenience property - identical behavior can be achieved by using `opts` alone. For example, the following 
two `java` configs achieve the exact same effect:

Using `systemProperties`:

```groovy
  // ... 
  java {
    systemProperties {
      foo = 'bar'
      hello = 'world'
    } 
  }
```

Using `opts`:

```groovy
  // ...
  java {
    opts = [
      '-Dfoo=bar',
      '-Dhello=world'
    ] 
  }
```

Even though both are effectively the same thing, defining many `-D` pairs in opts can be cumbersome and perhaps more 
difficult to read and understand.

##### workingDir

The `workingDir` property is optional. If specified, it equals the base directory where the samza plugin will extract 
the samza tarball. If unspecified, the default value is equivalent to `$HOME/.spin/temp/samza`

-->
<!-- 

## Developing Custom Plugins

TBD
-->
