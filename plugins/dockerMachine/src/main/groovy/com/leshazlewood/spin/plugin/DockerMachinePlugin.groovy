package com.leshazlewood.spin.plugin

import groovy.json.JsonSlurper

@SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
class DockerMachinePlugin implements Plugin {

    private Shell shell = new Shell()

    boolean isDockerMachineInPath() {
        def output = 'which docker-machine'.execute().text
        return output != null && !''.equals(output)
    }

    void assertDockerMachineInPath() {
        if (!isDockerMachineInPath()) {
            throw new IllegalStateException("The ${this.class.getSimpleName()} requires docker-machine to be in the \$PATH.")
        }
    }

    boolean isInstalled(Map service) {

        if(!isDockerMachineInPath()) {
            return false
        }

        String machineName = service.machine

        boolean found = false

        Process p = shell.execute(['docker-machine', 'ls', '--filter', "name=$machineName" as String])

        p.in.eachLine { String line, int lineNumber ->
            if (lineNumber > 1 && !found) {
                found = (machineName == line.tokenize().first())
            }
        }

        return found
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    Map preInstall(Map req) {
        assertDockerMachineInPath()
    }

    @Override
    Map install(Map req) {

        Map service = req.service

        if (isInstalled(service)) {
            println "\"${service.name}\" is already installed."
            return [status: 'installed']
        }

        return doInstall(service)
    }

    private Map doInstall(Map service) {

        //install for real:
        if (!service.driver?.name) {
            String msg = "${service.name} installation requires a driver.name value"
            throw new IllegalArgumentException(msg)
        }

        String command = "docker-machine create -d ${service.driver.name}"

        service.driver.options?.each { String name, def value ->
            command += " --${service.driver.name}-$name $value"
        }

        command += " ${service.machine}"

        boolean stdout = 'enabled' == service.stdout

        println "Installing \"${service.name}\" with command: $command"

        try {
            shell.executeAndWait(command, stdout)
        } catch (ShellException e) {
            String msg = "Failed to install \"${service.name}\".\n\nCommand: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }

        if (service.containsKey('certs') && service.certs) {

            service.certs.each { String host, Map hostCerts ->

                boolean ensureCertsd = true

                ['clientCert', 'clientKey', 'caCert'].each { String certPropName ->

                    if (hostCerts.containsKey(certPropName)) {

                        String srcPath = hostCerts[certPropName]

                        String unqualifiedName = 'client.cert' //default
                        if (certPropName == 'caCert') {
                            unqualifiedName = 'ca.crt'
                        } else if (certPropName == 'clientKey') {
                            unqualifiedName = 'client.key'
                        }

                        try {

                            println " - Copying $srcPath to ${service.name}:/etc/docker/certs.d/$host/$unqualifiedName"

                            if (ensureCertsd) {
                                command = "docker-machine ssh ${service.machine} sudo mkdir -p /etc/docker/certs.d/$host"
                                shell.executeAndWait(command, stdout)

                                command = "docker-machine ssh ${service.machine} mkdir -p /tmp/spin/certs.d/$host"
                                shell.executeAndWait(command, stdout)
                                ensureCertsd = false
                            }

                            command = "docker-machine scp $srcPath ${service.machine}:/tmp/spin/certs.d/$host/$unqualifiedName"
                            shell.executeAndWait(command, stdout)

                            command = "docker-machine ssh ${service.machine} sudo mv /tmp/spin/certs.d/$host/$unqualifiedName /etc/docker/certs.d/$host/$unqualifiedName"
                            shell.executeAndWait(command, stdout)

                        } catch (ShellException e) {
                            String msg = "Failed to copy $host '$srcPath' to the '${service.name}' " +
                                    "machine's /etc/docker/certs.d directory.\n\n" +
                                    "Command: $command\n\n" +
                                    "Output: ${e.errorOutput}"
                            throw new IllegalStateException(msg)
                        }

                        /*
                        command = "docker-machine scp $srcPath ${service.machine}:/etc/docker/certs.d/$host/$unqualifiedName"

                        try {
                            shell.executeAndWait(command, 'enabled' == service.stdout)
                        } catch (ShellException e) {
                            String msg = "Failed to copy $host '$srcPath' to the '${service.name}' " +
                                    "machine's /etc/docker/certs.d directory.\n\n" +
                                    "Command: $command\n\n" +
                                    "Output: ${e.errorOutput}"
                            throw new IllegalStateException(msg)
                        }
                        */
                    }
                }
            }
        }

        println "Installed \"${service.name}\"."
        return [status: 'installed']
    }

    boolean isStarted(Map service) {
        String out = "docker-machine status ${service.machine}".execute().text
        return out.toLowerCase().contains('running')
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    Map preStart(Map req) {
        assertDockerMachineInPath()
    }

    char[] askForSudoPassword(String serviceName) {
        Console console = System.console()
        println "\"${serviceName}\" routing table changes require sudo."
        return console.readPassword("Password:")
    }

    @Override
    Map start(Map req) {

        Map service = req.service

        if (!isInstalled(service)) {

            char[] sudoPassword = null
            if (service.routes && !isRunningAsSudo()) {
                sudoPassword = askForSudoPassword(service.name)
            }

            Map result = doInstall(service)

            //docker-machine will automatically start a VM when running 'create', so we need to setup routing tables
            applyRoutesIfNecessary(service, sudoPassword)

            // report that it has started:
            result.status = 'started'
            println "Started \"${service.name}\"."
            return result
        }

        if (isStarted(service)) {
            println "\"${service.name}\" has already started."
            return [status: 'started']
        }

        return doStart(service)
    }

    private Map doStart(Map service) {

        char[] sudoPassword = null

        if (service.routes && !isRunningAsSudo()) {
            sudoPassword = askForSudoPassword(service.name)
        }

        String command = "docker-machine start ${service.machine}"
        println "Starting \"${service.name}\" with command: $command"

        try {
            shell.executeAndWait(command, 'enabled' == service.stdout)
        } catch (ShellException e) {
            String msg = "Failed to start \"${service.name}\" with command: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }

        applyRoutesIfNecessary(service, sudoPassword)

        println "Started \"${service.name}\"."
        return [status: 'started']
    }

    private boolean isRunningAsSudo() {
        return "0" == "id -u".execute().text?.trim()
    }

    private String getDockerHostIp(Map service) {

        String command = /docker-machine ip ${service.machine}/

        int MAX_TRIES = 20

        for(int tries = 0; tries < MAX_TRIES; tries++) {

            Process p = shell.execute(command)
            p.waitFor()

            if (p.exitValue() == 0) {
                String output = p.text?.trim()
                if (output) {
                    return output
                }
            }

            Thread.sleep(1000)
        }

        throw new IllegalStateException("Unable to determine docker host IP for \"${service.machine}\"")
    }

    private String getCidr(Map service, String networkName) {

        String command = /docker-machine ssh ${service.machine} docker network inspect $networkName/

        int MAX_TRIES = 20

        for (int tries = 0; tries < MAX_TRIES; tries++) {

            Process p = shell.execute(command)
            p.waitFor()

            if (p.exitValue() == 0) {
                String output = p.text?.trim()
                if (output) {
                    JsonSlurper jsonSlurper = new JsonSlurper()
                    def json = jsonSlurper.parseText(output)
                    String cidr = json.first()?.IPAM?.Config?.first()?.Subnet
                    if (cidr) {
                        return cidr
                    }
                }
            }

            Thread.sleep(1000)
        }

        throw new IllegalStateException("Unable to determine cidr for network $networkName")
    }

    private Map executeSudoAndWait(String command, char[] password, boolean stdout = false) {

        if (password) {
            String shellCommand = 'sudo -S ' + command
            Process p = shell.execute(shellCommand)
            String s = new String(password)
            byte[] bytes = (s + '\n').getBytes('UTF-8')
            p.getOutputStream().write(bytes)
            p.getOutputStream().flush()
            p.waitFor()
            int exitValue = p.exitValue()
            String output = (stdout || exitValue != 0) ? p.text : null
            if (exitValue == 0 && output) {
                println output
            }
            if (exitValue != 0) {
                throw new ShellException(shellCommand, exitValue, output)
            }
            return [exitValue: exitValue, output: output, process: p]
        }
        return shell.executeAndWait(command, stdout)
    }

    private void applyRoutesIfNecessary(Map service, char[] sudoPassword) {

        if (!service.containsKey('routes') || service.routes == null) {
            return
        }

        String dockerHostIp = getDockerHostIp(service)

        List<String> networkNames = []

        if (service.routes instanceof String) {
            networkNames << service.routes
        } else if (service.routes instanceof List) {
            networkNames = service.routes
        }

        networkNames.each { networkName ->
            String cidr = getCidr(service, networkName)
            try {
                println "Ensuring routing table: packets sent to $cidr ($networkName) will be routed to docker host $dockerHostIp"
                executeSudoAndWait("route delete $cidr", sudoPassword, 'enabled' == service.stdout)
                executeSudoAndWait("route -n add $cidr $dockerHostIp", sudoPassword, 'enabled' == service.stdout)
            } catch (ShellException e) {
                String msg = "Failed to add CIDR route for network \"$networkName\" for \"${service.name}\".\n\nOutput: ${e.errorOutput}"
                throw new IllegalStateException(msg, e)
            }
        }
    }

    private void removeRoutesIfNecessary(Map service) {

        if (!service.containsKey('routes') || service.routes == null) {
            return
        }

        String dockerHostIp = getDockerHostIp(service)

        char[] sudoPassword = null

        if (!isRunningAsSudo()) {
            sudoPassword = askForSudoPassword(service.name)
        }

        List<String> networkNames = []

        if (service.routes instanceof String) {
            networkNames << service.routes
        } else if (service.routes instanceof List) {
            networkNames = service.routes
        }

        networkNames.each { networkName ->
            String cidr = getCidr(service, networkName)
            try {
                println "Removing routing table entry for $cidr ($networkName) to $dockerHostIp"
                executeSudoAndWait("route delete $cidr", sudoPassword, 'enabled' == service.stdout)
            } catch (ShellException e) {
                String msg = "Failed to add CIDR route for network \"$networkName\" for \"${service.name}\".\n\nOutput: ${e.errorOutput}"
                throw new IllegalStateException(msg, e)
            }
        }
    }

    @Override
    Map status(Map req) {
        [status: isStarted(req.config) ? 'started' : 'stopped']
    }

    @Override
    Map stop(Map req) {

        assertDockerMachineInPath()

        Map service = req.service

        if (isStarted(service)) {
            return doStop(service)
        }

        println "\"${service.name}\" is already stopped."
        return [status: 'stopped']
    }

    private Map doStop(Map service) {

        removeRoutesIfNecessary(service)

        String command = "docker-machine stop ${service.machine}"
        println "Stopping \"${service.name}\" with command: $command"

        try {
            shell.executeAndWait(command, 'enabled' == service.stdout)
        } catch (ShellException e) {
            String msg = "Failed to stop \"${service.name}\".\n\nCommand: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }

        println "Stopped \"${service.name}\"."
        return [status: 'stopped']
    }

    @Override
    Map uninstall(Map req) {

        assertDockerMachineInPath()

        Map service = req.service
        String serviceName = service.name

        if (isInstalled(service)) {

            if (isStarted(service)) {
                doStop(service);
            }

            return doUninstall(service)
        }

        println "\"$serviceName\" is not installed."
        return [status: 'uninstalled']
    }

    private Map doUninstall(Map service) {

        List<String> command = ['docker-machine', 'rm', '-y', "${service.machine}" as String]
        println "Uninstalling \"${service.name}\" with command: ${command.join(' ')}"

        try {
            shell.executeAndWait(command, 'enabled' == service.stdout)
        } catch (ShellException e) {
            String msg = "Failed to uninstall \"${service.name}\" with command: ${command.join(' ')}\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }

        println "Uninstalled \"${service.name}\"."
        return [status: 'uninstalled']
    }
}
