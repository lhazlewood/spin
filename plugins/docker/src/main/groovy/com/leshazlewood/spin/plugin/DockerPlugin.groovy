/*
 * Copyright (c) 2014 Les Hazlewood
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.leshazlewood.spin.plugin

/**
 * @since 0.1.0
 */
@SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
class DockerPlugin extends AbstractPlugin {

    private Shell shell = new Shell()

    boolean isDockerInPath() {
        def output = 'command -v docker'.execute().text
        return output != null && !''.equals(output)
    }

    void assertDockerInPath() {
        if (!isDockerInPath()) {
            throw new IllegalStateException("The ${this.class.getSimpleName()} requires docker to be in the \$PATH.")
        }
    }

    Map preInstall(Map req) {
        assertDockerInPath()
        return null
    }

    @Override
    Map install(Map req) {

        String serviceName = req.service.name

        String containerId = getContainerId(serviceName)

        if (containerId) {

            String containerStatus = getContainerStatus(containerId)

            println "\"${serviceName}\" is already installed."

            return [status: (containerStatus == 'running' ? 'started' : 'stopped')]
        }

        println "\"$serviceName\" will be installed when calling start."
        return [status: 'uninstalled']
    }

    String getContainerId(String serviceName) {
        String output = shell.executeAndWait(['docker', 'ps', '-a', '-q', '-f', "name=$serviceName" as String]).text
        return output ?: null
    }

    String getContainerStatus(String containerId) {
        assert containerId != null
        return shell.executeAndWait(['docker', 'inspect', '-f', '{{.State.Status}}', containerId]).text
    }

    @Override
    Map start(Map req) {

        Map service = req.service
        String serviceName = req.service.name

        String containerId = getContainerId(serviceName)

        if (containerId) {

            String containerStatus = getContainerStatus(containerId)

            if (containerStatus == 'running') { //already started
                println "\"${serviceName}\" has already started."
                return [status: 'started']
            }

            //else not running - start it:
            return dockerStart(service, containerId)
        }

        //else container does not exist (not running or currently stopped) - do a 'docker run':
        return dockerRun(service)
    }

    Map dockerStart(Map service, String containerId) {

        assert containerId != null

        String command = "docker start $containerId"
        println "Starting \"${service.name}\" with command: $command"

        try {
            shell.executeAndWait(command, 'enabled' == service.stdout)
        } catch (ShellException e) {
            String msg = "Failed to start \"${service.name}\". Command: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }

        ensureHealthIfNecessary(service)

        println "Started \"${service.name}\"."
        return [status: 'started']
    }

    Map dockerRun(Map service) {

        //otherwise run for real
        if (!service.image) {
            throw new IllegalArgumentException("${service.name} installation requires an 'image' config value.")
        }

        String command = "docker run --detach --name ${service.name}"

        if (service.cpu_shares) {
            command += " --cpu-shares=${service.cpu_shares}"
        }

        service.dns?.each {
            command += " --dns=$it"
        }

        service.dns_search?.each { searchDomain ->
            command += " --dns-search=$searchDomain"
        }

        if (service.environment) {
            if (service.environment instanceof Map) {
                service.environment.each { key, value ->
                    command += " -e $key"
                    if (value) {
                        command += "='$value'"
                    }
                }
            } else {
                service.environment.each { item ->
                    command += " -e $item"
                }
            }
        }

        if (service.hostname) {
            command += " --hostname=${service.hostname}"
        }

        service.links?.each { link ->
            command += " --link $link"
        }

        if (service.memory) {
            command += " --memory=${service.memory}"
        }

        if (service.memory_reservation) {
            command += " --memory-reservation=${service.memory_reservation}"
        }

        if (service.memory_swap) {
            command += " --memory-swap=${service.memory_swap}"
        }

        if (service.memory_swappiness) {
            command += " --memory-swappiness=${service.memory_swappiness}"
        }

        service.ports?.each { mapping ->
            command += " --publish $mapping"
        }

        if (service.ulimits) {
            if (!(service.ulimits instanceof Map)) {
                command += " --ulimit nofile=${service.ulimits}"
            } else {
                if (service.ulimits.nproc) {
                    command += " --ulimit nproc=${service.ulimits.nproc}"
                }
                if (service.ulimits.nofile) {
                    command += " --ulimit nofile="
                    boolean softSpecified = false
                    if (service.ulimits.nofile.soft) {
                        softSpecified = true
                        command += "${service.ulimits.nofile.soft}"
                    }
                    if (service.ulimits.nofile.hard) {
                        if (softSpecified) {
                            command += ':'
                        }
                        command += "${service.ulimits.nofile.hard}"
                    }
                }
            }
        }

        service.volumes?.each { volume ->
            command += " --volume $volume"
        }

        service.volumes_from?.each {
            command += " --volumes-from $it"
        }

        service?.options?.each { option ->
            command += " $option"
        }

        command += " ${service.image}"

        println "Starting \"${service.name}\" with command: $command"

        try {
            shell.executeAndWait(command, 'enabled' == service.stdout)
        } catch (ShellException e) {
            String msg = "Failed to start \"${service.name}\". Command: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }

        ensureHealthIfNecessary(service)

        println "Started \"${service.name}\"."
        return [status: 'started']
    }

    @Override
    Map status(Map req) {

        String serviceName = req.service.name

        String containerId = getContainerId(serviceName)

        if (containerId) {

            String containerStatus = getContainerStatus(containerId)

            if (containerStatus == 'running') { //started, need to shut down
                return [status: 'started']
            } else {
                return [status: 'stopped']
            }
        }

        return [status: 'uninstalled']
    }

    @Override
    Map stop(Map req) {

        String serviceName = req.service.name

        String containerId = getContainerId(serviceName)

        if (containerId) {

            String containerStatus = getContainerStatus(containerId)

            if (containerStatus == 'running') { //started, need to shut down
                return dockerStop(req.service, containerId)
            }
        }

        println "\"${serviceName}\" is already stopped."
        return [status: 'stopped']
    }

    Map dockerStop(Map service, String containerId) {

        String command = "docker stop $containerId"
        println "Stopping \"${service.name}\" with command: $command"

        try {
            shell.executeAndWait(command, 'enabled' == service.stdout)
        } catch (ShellException e) {
            String msg = "Failed to stop \"${service.name}\". Command: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }

        println "Stopped \"${service.name}\"."
        return [status: 'stopped']
    }

    @Override
    Map uninstall(Map req) {

        Map service = req.service

        String containerId = getContainerId(service.name as String)

        if (containerId) {

            String containerStatus = getContainerStatus(containerId)

            if (containerStatus == 'running') {
                dockerStop(service, containerId)
            }

            return dockerRm(service, containerId)
        }

        println "\"${service.name}\" is not installed."
        return [status: 'uninstalled']
    }

    Map dockerRm(Map service, String containerId) {

        assert containerId != null

        String command = "docker rm -v $containerId"

        println "Uninstalling \"${service.name}\" with command: $command"

        try {
            shell.executeAndWait(command, 'enabled' == service.stdout)
            println "Uninstalled \"${service.name}\"."
        } catch (ShellException e) {
            String msg = "Failed to uninstall \"${service.name}\". Command: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }

        return [status: 'uninstalled']
    }
}
