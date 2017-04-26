package com.leshazlewood.spin.plugin

@SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
class ExejarPlugin extends AbstractPlugin {

    static final String DEPENDENCY_PLUGIN_VERSION = '2.10'
    static final String DEPENDENCY_PLUGIN = "org.apache.maven.plugins:maven-dependency-plugin:$DEPENDENCY_PLUGIN_VERSION"
    static final String GET_BASE_COMMAND = "mvn --batch-mode $DEPENDENCY_PLUGIN:get"

    void canonicalize(Map req) {
        if (req.service.containsKey('jar')) {
            def value = req.service.jar
            if (value instanceof File) {
                req.service.jar = ((File)value).canonicalFile
            } else {
                req.service.jar = new File(String.valueOf(value)).canonicalFile
            }
            File file = req.service.jar
            if (file == null || !file.exists() || !file.isFile() || !file.canonicalPath.endsWith('.jar')) {
                String msg = /Specified jar '${file}' is not a concrete file that ends with '.jar'/
                throw new IllegalArgumentException(msg);
            }
        }
    }

    void assertArtifact(Map req) {

        boolean jarSpecified = req.service.containsKey('jar')

        boolean artifactSpecified = req.service.containsKey('artifact') && req.service.artifact instanceof Map

        if (jarSpecified || artifactSpecified) {
            if (jarSpecified && artifactSpecified) {
                String msg = /"${req.service.name} configuration must specify either 'artifact' or 'jar' but not both./
                throw new IllegalArgumentException(msg)
            }
        } else {
            String msg = /"${req.service.name} configuration must specify either 'artifact' or 'jar'./
            throw new IllegalArgumentException(msg)
        }

        if (jarSpecified) {
            canonicalize(req)
        } else if (artifactSpecified) {
            req.service.artifact = validateArtifact(req.service.artifact as Map, req.service.name as String)
        }
    }

    Map validateArtifact(Map artifact, String serviceName, String defaultArtifactType = null, String defaultClassifier = null) {

        String groupId = artifact.groupId != null ? com.leshazlewood.spin.plugin.AbstractPlugin.trimToNull(artifact.groupId) : null
        String artifactId = artifact.artifactId != null ? com.leshazlewood.spin.plugin.AbstractPlugin.trimToNull(artifact.artifactId) : null
        String version = artifact.version != null ? com.leshazlewood.spin.plugin.AbstractPlugin.trimToNull(artifact.version) : null
        String packaging = artifact.type != null ? com.leshazlewood.spin.plugin.AbstractPlugin.trimToNull(artifact.type) : defaultArtifactType
        String classifier = artifact.classifier ? com.leshazlewood.spin.plugin.AbstractPlugin.trimToNull(artifact.classifier) : defaultClassifier

        if (groupId == null) {
            throw new IllegalArgumentException("$serviceName artifact groupId cannot be null or empty.")
        }
        if (artifactId == null) {
            throw new IllegalArgumentException("$serviceName artifact artifactId cannot be null or empty.")
        }
        if (version == null) {
            throw new IllegalArgumentException("$serviceName artifact version cannot be null or empty.")
        }

        return [groupId: groupId, artifactId: artifactId, version: version, packaging: packaging, classifier: classifier]
    }

    String getUnqualifiedDirectoryPath(Map artifact) {
        char sep = File.separatorChar
        return artifact.groupId.replace('.' as char, sep) + sep + artifact.artifactId + sep + artifact.version
    }

    String getUnqualifiedFilePath(Map artifact) {
        String sep = File.separator
        return getUnqualifiedDirectoryPath(artifact) + sep + artifact.artifactId + '-' + artifact.version + '.jar'
    }

    String getLocalRepoDirPath() {
        return System.properties['user.home'] + File.separator + '.m2' + File.separator + 'repository'
    }

    String getJarFilePath(Map req) {

        if (req.service.containsKey('jar')) {
            return (req.service.jar as File).canonicalPath
        }

        //otherwise, artifact:
        return getLocalRepoDirPath() + File.separator +  getUnqualifiedFilePath(req.service.artifact as Map)
    }

    File getWorkingDir(Map req) {

        Map service = req.service

        File workingDir = null

        if (service.containsKey('workingDir') && service.workingDir) {
            if (service.workingDir instanceof File) {
                workingDir = (File)service.workingDir
            } else {
                workingDir = new File(service.workingDir as String)
            }
        }

        if (workingDir) {
            workingDir.mkdirs();
        }

        return workingDir
    }

    String toSysPropNameValue(String name, String value) {
        // To ensure we can find the process later w/ pgrep -f (to see if it is running or so we can shut it down)
        String s = "$name="
        boolean containsWhitespace = value.tokenize().size() > 1
        if (containsWhitespace) { //need to quote the name:
            s += "'${value}'"
        } else {
            s += "$value"
        }
        return s
    }

    boolean isInstalled(Map req) {

        //if the version is a SNAPSHOT, we don't consider it installed, since we always want to ensure the latest
        if ( req.service.artifact?.version?.endsWith('SNAPSHOT') ) {
            return false;
        }

        String path = getJarFilePath(req)
        File file = new File(path)
        return file != null && file.exists() && file.isFile()
    }

    @Override
    Map install(Map req) {

        assertArtifact(req)

        Map service = req.service

        if (isInstalled(req)) {
            println "\"${service.name}\" is already installed."
            return [status: 'installed']
        }

        return doInstall(req)
    }

    Map doInstall(Map req) {
        Map service = req.service
        installViaMaven(service)
        println "Installed \"${service.name}\"."
        return [status: 'installed']
    }

    void installViaMaven(Map service) {

        String command = GET_BASE_COMMAND

        Map artifact = service.artifact

        command += " -Dartifact=${artifact.groupId}:${artifact.artifactId}:${artifact.version}"

        if (artifact.packaging) {
            command += ":${artifact.packaging}"
        } else {
            if (artifact.classifier) {
                command += ":"
            }
        }

        if (artifact.classifier) {
            command += ":${artifact.classifier}"
        }

        if (artifact.transitive) {
            command += " -Dtransitive=true"
        } else {
            command += " -Dtransitive=false"
        }

        println(/Installing "${service.name}" with command: $command/)

        try {
            com.leshazlewood.spin.plugin.AbstractPlugin.shell.executeAndWait(command, 'enabled' == service.stdout)
        } catch (ShellException e) {
            String msg = "Failed to install \"${service.name}\".\n\nCommand: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }
    }

    @Override
    Map start(Map req) {

        assertArtifact(req)

        Map service = req.service
        String name = service.name

        if (!isInstalled(req)) {
            doInstall(req)
        }

        if (isRunning(name)) {
            println "\"${name}\" has already started."
            return [status: 'started']
        }

        return doStart(req)
    }

    Map doStart(Map req) {

        Map service = req.service

        String serviceName = service.name

        // nohup disconnects the process from the terminal and shields the process from SIGHUP if the launching
        // shell exits.  We always want this for a background service.
        // So instead of 'java ....', we run 'nohup java ....'
        String command = 'nohup java '

        service.options?.each { option ->
            command += "$option "
        }

        // To ensure we can find the process later w/ pgrep -f (to see if it is running or so we can shut it down)
        command += '-D' + toSysPropNameValue('com.lhazlewood.spin.service.name', serviceName) + ' '

        service.systemProperties?.each { String name, value ->
            command += "-D$name=$value "
        }

        // now call the actual jar:
        String jarFilePath = getJarFilePath(req)
        command += "-jar $jarFilePath "

        // support any arguments sent into the jar program:
        service.args?.each { String arg ->
            command += "$arg "
        }

        // Ensure command output is redirected as appropriate.
        // Default to /dev/null in case a location is not configured:
        String outfile = '/dev/null'

        if (service.containsKey('stdout') && service.stdout) {
            String stdout = service.stdout as String
            if (stdout && stdout != 'enabled' && stdout != 'disabled' && stdout != 'true' && stdout != 'false') {
                //assume value is a file name.  We have to canonicalize it as well to ensure that
                //if the working directory is different that we always direct to an absolute path
                outfile = new File(stdout).canonicalPath
            }
        }

        // >outfile   = direct stdout to outfile
        // 2>&1       = direct stderr to same location as stdout (which in this case is outfile)
        // &          = launch as a background process so the shell isn't blocked
        // disown     = remove the process from the shell's job list
        command += ">'$outfile' 2>&1 & disown"

        def shell = new Shell()
        File workingDir = getWorkingDir(req)
        if (workingDir) {
            shell.dir = workingDir
        }

        println "Starting \"${service.name}\" with command: $command"

        try {
            shell.executeAndWait(command)
        } catch (ShellException e) {
            String msg = "Failed to start \"${service.name}\" with command: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }

        ensureHealthIfNecessary(service)

        println "Started \"$serviceName\"."
        return [status: 'started']
    }

    int getProcessId(String serviceName) {

        String nameVal = toSysPropNameValue('com.lhazlewood.spin.service.name', serviceName)

        def output = /pgrep -f $nameVal/.execute().text
        if (output) {
            output = output.replace('\n', '').trim()
            if (output) {
                return Integer.parseInt(output)
            }
        }
        return -1
    }

    boolean isRunning(String serviceName) {
        int processId = getProcessId(serviceName)
        return processId > 0
    }

    @Override
    Map status(Map req) {

        String serviceName = req.service.name

        if (isRunning(serviceName)) {
            return [status: 'started']
        }

        return [status: 'stopped']
    }

    @Override
    Map stop(Map req) {

        String serviceName = req.service.name

        int pid = getProcessId(serviceName)

        if (pid > 0) {
            return doStop(req, pid)
        }

        println "\"${serviceName}\" is already stopped."
        return [status: 'stopped']
    }

    Map doStop(Map req, int pid) {

        String serviceName = req.service.name

        String command = "kill $pid"

        println "Stopping \"${serviceName}\" with command: $command"

        try {
            new Shell().executeAndWait(command)
        } catch (ShellException e) {
            String msg = "Failed to stop \"${serviceName}\". Command: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }

        println("Stopped \"$serviceName\".")
        return [status: 'stopped']
    }

    @Override
    Map uninstall(Map req) {

        int pid = getProcessId(req.service.name as String)

        if (pid > 0) {
            doStop(req, pid)
        }

        return [status: 'uninstalled'];

        /*

        assertArtifact(req)

        Map service = req.service
        String serviceName = service.name

        if (!isInstalled(req)) {
            println "\"${service.name}\" is not installed."
            return [status: 'uninstalled']
        }

        int pid = getProcessId(serviceName)

        if (pid > 0) {
            doStop(req, pid)
        }

        return doUninstall(req)
        */
    }

    /*
    Map doUninstall(Map req) {

        File artifactDir = getArtifactDir(req)

        println "Uninstalling \"${req.service.name}\" by deleting directory: $artifactDir"

        artifactDir.deleteDir()

        println "Uninstalled \"${req.service.name}\"."

        return [status: 'uninstalled']
    }
    */
}
