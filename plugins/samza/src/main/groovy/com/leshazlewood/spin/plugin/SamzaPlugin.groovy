package com.leshazlewood.spin.plugin

@SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
class SamzaPlugin implements Plugin {

    static final String DEPENDENCY_PLUGIN_VERSION = '2.10'
    static final String DEPENDENCY_PLUGIN = "org.apache.maven.plugins:maven-dependency-plugin:$DEPENDENCY_PLUGIN_VERSION"
    static final String UNPACK_BASE_COMMAND = "mvn --batch-mode $DEPENDENCY_PLUGIN:unpack"
    static final String TARBALL_FILE_TYPE = 'tar.gz'
    static final String TARBALL_FILE_SUFFIX = '.' + TARBALL_FILE_TYPE

    class ArtifactRef {
        String groupId
        String artifactId
        String version
        String packaging
        String classifier

        @Override
        String toString() {
            String s = "groupId: $groupId, artifactId: $artifactId"
            if (version) s += ", version: $version"
            if (packaging) s += ", packaging: $packaging"
            if (classifier) s += ", classifier: $classifier"
            return s
        }
    }

    String trimToNull(String s) {
        String trimmed = s?.trim()
        if (trimmed.equals('')) {
            trimmed = null
        }
        return trimmed
    }

    Map validateArtifact(Map artifact, String serviceName) {

        String groupId = artifact.groupId != null ? trimToNull(artifact.groupId) : null
        String artifactId = artifact.artifactId != null ? trimToNull(artifact.artifactId) : null
        String version = artifact.version != null ? trimToNull(artifact.version) : null
        String packaging = artifact.type != null ? trimToNull(artifact.type) : TARBALL_FILE_TYPE
        String classifier = artifact.classifier ? trimToNull(artifact.classifier) : 'dist'

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

    String toFlags(Map artifact, File workingDir) {

        String s = "-Dproject.basedir='$workingDir' "

        s += "-Dartifact=${artifact.groupId}:${artifact.artifactId}:${artifact.version}"

        if (artifact.packaging) {
            s += ":${artifact.packaging}"
        } else {
            if (artifact.classifier) {
                s += ":"
            }
        }

        if (artifact.classifier) {
            s += ":${artifact.classifier}"
        }

        s += " -DoutputDirectory=${workingDir}"

        return s
    }

    File getPluginWorkingDir(Map req) {

        Map service = req.service

        File pluginWorkingDir

        if (service.containsKey('workingDir') && service.workingDir) {
            if (req.service.workingDir instanceof File) {
                pluginWorkingDir = (File)req.service.workingDir
            } else {
                pluginWorkingDir = new File(req.service.workingDir as String)
            }
        } else {
            File userSpinHomeDir = req.context.userSpinHome as File
            pluginWorkingDir = new File(userSpinHomeDir.canonicalPath + File.separator + 'temp' + File.separator + 'samza')
        }

        return pluginWorkingDir
    }

    boolean isFileSpecified(Map service) {
        return service.containsKey('file') && service.file != null
    }

    File getArtifactFile(Map service, boolean required = false) {

        assert isFileSpecified(service)

        File file

        if (service.file instanceof File) {
            file = ((File)service.file).canonicalFile
        } else {
            file = new File(service.file as String).canonicalFile
        }

        if (required && !file.isFile()) {
            String msg = /"${service.name}" specified file does not exist or is not a file: $file/
            throw new IllegalArgumentException(msg)
        }

        return file
    }

    File getFileArtifactDir(Map req) {

        Map service = req.service
        assert isFileSpecified(service)

        File pluginWorkingDir = getPluginWorkingDir(req)

        File artifactFile = getArtifactFile(service)

        if (!artifactFile) {
            return pluginWorkingDir
        }

        //create artifact dir based on file name:
        String name = artifactFile.canonicalPath

        int i = name.lastIndexOf((int)File.separatorChar)
        if (i > 0) {
            name = name.substring(i + 1)
        }
        if (name.endsWith(TARBALL_FILE_SUFFIX)) {
            name = name.substring(0, name.length() - TARBALL_FILE_SUFFIX.length())
        }

        return new File(pluginWorkingDir.canonicalPath + File.separator + name)
    }

    File getArtifactDir(Map req, boolean validate = true) {

        Map service = req.service

        if (isFileSpecified(service)) {
            return getFileArtifactDir(req)
        }

        //file not specified, so an artifact definition must exist:
        if (!service.containsKey('artifact') || !(service.artifact instanceof Map)) {
            String msg = /"${service.name}" configuration must specify either 'file' or 'artifact'./
            throw new IllegalArgumentException(msg)
        }

        if (validate) {
            service.artifact = validateArtifact(service.artifact as Map, service.name as String)
        }

        Map artifact = service.artifact

        File pluginWorkingDir = getPluginWorkingDir(req)

        String subpath = artifact.groupId.replace('.' as char, File.separatorChar) + File.separatorChar +
                artifact.artifactId + File.separator + artifact.version

        return new File(pluginWorkingDir.canonicalPath + File.separator + subpath)
    }

    File getRunJobShFile(Map req) {
        File artifactWorkingDir = getArtifactDir(req)
        return new File(artifactWorkingDir.canonicalPath, 'bin/run-job.sh')
    }

    boolean isInstalled(Map req) {

        //if the version is a SNAPSHOT, we don't consider it installed, since we always want to download the latest
        if ( (isFileSpecified(req.service) && req.service.file.toString().contains('SNAPSHOT')) ||
                req.service.artifact?.version?.endsWith('SNAPSHOT') ) {
            return false;
        }

        File runJobShFile = getRunJobShFile(req)
        return runJobShFile != null && runJobShFile.exists() && runJobShFile.isFile()
    }

    @Override
    Map install(Map req) {

        Map service = req.service

        if (isInstalled(req)) {
            println "\"${service.name}\" is already installed."
            return [status: 'installed']
        }

        return doInstall(req)
    }

    Map doInstall(Map req) {

        Map service = req.service
        File artifactDir = getArtifactDir(req)

        if (!artifactDir.exists()) {
            artifactDir.mkdirs()
        }

        assert artifactDir.isDirectory()

        if (isFileSpecified(service)) {
            installFromFile(service, artifactDir)
        } else {
            installViaMaven(service, artifactDir)
        }

        println "Installed \"${service.name}\"."
        return [status: 'installed']
    }

    void installFromFile(Map service, File artifactDir) {

        File artifactFile = getArtifactFile(service, true)

        def command = ['tar', '-x', '-z', '-f', artifactFile.canonicalPath]

        println(/Installing "${service.name}" with command: ${command.join(' ')}/)

        Shell shell = new Shell().dir(artifactDir)
        try {
            shell.executeAndWait(command, 'enabled' == service.stdout)
        } catch (ShellException e) {
            String msg = "Failed to install \"${service.name}\".\n\nCommand: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }
    }

    void installViaMaven(Map service, File artifactDir) {

        Map artifact = service.artifact

        String flags = toFlags(artifact, artifactDir)

        String command = "$UNPACK_BASE_COMMAND $flags"

        println(/Installing "${service.name}" with command: $command/)

        Shell shell = new Shell().dir(artifactDir)
        try {
            shell.executeAndWait(command, 'enabled' == service.stdout)
        } catch (ShellException e) {
            String msg = "Failed to install \"${service.name}\".\n\nCommand: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }
    }

    @Override
    Map start(Map req) {

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

        String javaOpts = ''
        service.java?.opts?.each { option ->
            javaOpts += "$option "
        }

        String sysProps = "-Dcom.lhazlewood.spin.service.name=\"${serviceName}\" " //to track which services are running already

        service.java?.systemProperties?.each { String name, value ->
            sysProps += "-D$name=$value "
        }

        javaOpts += sysProps

        javaOpts = trimToNull(javaOpts);

        File artifactDir = getArtifactDir(req)
        File samzaConfigFile = new File(artifactDir, 'config/deploy.properties')

        if (service.config?.file) {
            samzaConfigFile = new File(artifactDir, service.config.file as String)
        }

        if (!samzaConfigFile.exists()) {
            String msg = "$serviceName samza config file '$samzaConfigFile' does not exist.";
            throw new IllegalArgumentException(msg)
        }

        String configFactory = "org.apache.samza.config.factories.PropertiesConfigFactory"
        if (req.service.config?.factory) {
            configFactory = req.service.config.factory
        }

        String runJobSh = new File(artifactDir.canonicalPath, 'bin/run-job.sh').canonicalPath

        String command = "nohup $runJobSh";

        command += " --config-factory=$configFactory --config-path=\"file://$samzaConfigFile\""

        req.service.config?.overrides?.each { String name, value ->
            command += " --config $name=$value"
        }

        //ensure command output is redirected as appropriate:
        String outfile = "/dev/null"

        if (service.containsKey('stdout') && service.stdout) {
            String stdout = service.stdout as String
            if (stdout && stdout != 'enabled' && stdout != 'disabled' && stdout != 'true' && stdout != 'false') {
                //assume value is a file name:
                outfile = stdout
            }
        }

        command += " >$outfile 2>&1"

        //ensure starts in the background:
        command += " & disown"

        def shell = new Shell()
        if (javaOpts) {
            shell.env([JAVA_OPTS:javaOpts])
        }

        println "Starting \"${service.name}\" with command: $command"

        try {
            shell.executeAndWait(command)
        } catch (ShellException e) {
            String msg = "Failed to start \"${service.name}\" with command: $command\n\nOutput: ${e.errorOutput}"
            throw new IllegalStateException(msg, e)
        }

        println "Started \"$serviceName\"."

        return [status: 'started']
    }

    int getProcessId(String serviceName) {
        def output = "pgrep -f com.lhazlewood.spin.service.name=\\\"$serviceName\\\"".execute().text
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
    }

    Map doUninstall(Map req) {

        File artifactDir = getArtifactDir(req)

        println "Uninstalling \"${req.service.name}\" by deleting directory: $artifactDir"

        artifactDir.deleteDir()

        println "Uninstalled \"${req.service.name}\"."

        return [status: 'uninstalled']
    }

}
