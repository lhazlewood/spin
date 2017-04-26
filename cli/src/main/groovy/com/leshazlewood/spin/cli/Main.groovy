package com.leshazlewood.spin.cli

import com.leshazlewood.spin.lang.Classes
import com.leshazlewood.spin.lang.UnknownClassException

import java.nio.file.Files

class Main {

    private ClassLoader classLoader = null
    private File userDir = null
    private File spinInstallDir = null
    private File userSpinDir = null
    private Map spinConfig = null
    private File spinConfigFile = null
    private String command = null
    private String environmentName = null
    private String serviceName = null
    private List<String> profiles = null

    private Map<String, Class> pluginClasses = [:]

    static File findPluginFile(Map service, File parentDir) {
        File pluginDir = new File(parentDir, 'plugins')
        if (pluginDir.exists() && pluginDir.isDirectory()) {
            String unqualifiedPluginFileName = service.type.capitalize() + 'Plugin.groovy'
            File pluginFile = new File(pluginDir, unqualifiedPluginFileName)
            if (pluginFile.exists() && pluginFile.isFile()) {
                return pluginFile
            }
        }
        return null
    }

    File getPluginFile(Map service) {

        //Try user dir first:
        File pluginFile = findPluginFile(service, userSpinDir)

        if (!pluginFile) { //try installation dir next:
            pluginFile = findPluginFile(service, spinInstallDir)
        }

        if (!pluginFile) {
            String msg = "Cannot find a \"${service.type}\" plugin for service \"${service.type}\" in either " +
                    "$userSpinDir or $spinInstallDir.  Please ensure the \"${service.type}\" value is correct, and if so," +
                    "the plugin exists in either of these locations."
            throw new IllegalArgumentException(msg)
        }

        return pluginFile
    }

    Class getPluginClass(Map service) {

        final String type = service.type

        Class clazz = pluginClasses.get(type)
        if (clazz) return clazz

        String candidate = type  //assumes type is a fully-qualified class name of the plugin to use

        if (!type.contains('.')) { //not a fully qualified class name, use default heuristic:
            candidate = 'com.leshazlewood.spin.plugin.' + type.capitalize() + 'Plugin'
        }

        try {
            clazz = Classes.forName(candidate)
        } catch (UnknownClassException uce) {
            String msg = "Unable to load plugin class $candidate for plugin type '$type' for service '${service.name}': ${uce.message}"
            throw new IllegalArgumentException(msg)
        }

        /*
        //otherwise we have to look up the plugin file:
        File pluginFile = getPluginFile(service)

        try {
            GroovyClassLoader gcl = new GroovyClassLoader(getClass().getClassLoader())
            gcl.addClasspath(spinInstallDir.canonicalPath)
            gcl.addClasspath(userSpinDir.canonicalPath)
            clazz = gcl.parseClass(pluginFile)
            assert clazz != null
        } catch (Exception e) {
            String msg = "Unable to load plugin class for plugin file $pluginFile: $e.message"
            throw new IllegalArgumentException(msg, e)
        }

        /*
        if (!Plugin.class.isAssignableFrom(pluginClass)) {
            String msg = "Plugin class $pluginClass from plugin file $pluginFile does not implement the ${Plugin.class.name} interface."
            throw new IllegalArgumentException(msg)
        }
        */

        //validate plugin supports at least one of the methods that can be called
        /*
        boolean found = false

        PLUGIN_METHOD_NAMES.each { name ->
            if (pluginClass.metaClass.respondsTo(name, [Map.class] as Object[])) {
                found = true
            }
        }
        if (!found) {
            String names = PLUGIN_METHOD_NAMES.join(',')
            String msg = "Plugin class $pluginClass loaded from plugin file $pluginFile does not support any expected " +
                    "plugin commands ${names}"
            throw new IllegalArgumentException(msg)
        }
        */

        //cache for later access:
        pluginClasses.put(service.type as String, clazz)

        return clazz
    }

    def getPlugin(Map service) {
        /*String unqualifiedPluginFileName = service.type.capitalize() + 'Plugin.groovy'
        return PLUGIN_SCRIPT_ENGINE.run(unqualifiedPluginFileName, new Binding())*/
        Class pluginClass = getPluginClass(service)
        return pluginClass.newInstance()
    }

    boolean isEnabled(Map service) {

        if (service.containsKey('enabled') && !service.enabled) {
            return false
        }

        if (service.containsKey('profiles')) {
            if (!(service.profiles instanceof List)) {
                if (!this.profiles.contains(service.profiles)) {
                    return false
                }
            } else {
                boolean found = false
                for (String profileName : service.profiles) {
                    if (this.profiles.contains(profileName)) {
                        found = true
                        break
                    }
                }
                if (!found) {
                    return false
                }
            }
        }

        return true
    }


    void status(Map<String, Map> services) {

        int nameLength = 'NAME'.length()
        int statusLength = 'STATUS'.length()

        for (Map service : services.values()) {
            String serviceName = service.name as String
            nameLength = Math.max(nameLength, serviceName.length())
        }

        println String.format("%-${nameLength}s   %-${statusLength}s", ["NAME", "STATUS"] as Object[])

        for (Map service : services.values()) {
            if (!isEnabled(service)) {
                continue
            }
            def plugin = service.plugin
            def request = createRequest(service, 'status')
            def result = plugin.status(request) ?: [status: 'stopped']
            String serviceName = service.name as String
            result.serviceName = serviceName
            statusLength = Math.max(statusLength, result.status.length())

            //print as we get a result:
            println String.format("%-${nameLength}s   %-${statusLength}s", [result.serviceName, result.status] as Object[])
        }
    }

    Map createRequest(Map service, String command) {
        [
                service: service,
                config : service, //alias, need to remove
                spin   : spinConfig,
                context: [
                        file        : spinConfigFile,
                        command     : command,
                        userDir     : userDir,
                        spinHome    : spinInstallDir,
                        userSpinHome: spinUserDir
                ]
        ]
    }

    void executeCommand(Collection<Map> services, String command) {
        for (Map service : services) {
            if (!isEnabled(service)) {
                continue
            }
            def plugin = service.plugin
            if (plugin && plugin.respondsTo(command, [Map.class] as Object[])) {
                def request = createRequest(service, command)
                plugin."$command"(request)
            }
        }
    }

    void executeCommand(Map<String, Map> serviceDefinitions) {

        if (command == 'status') {
            status(serviceDefinitions)
            return
        }

        //otherwise invoke the default pre/command/post flow:
        Collection<Map> services = serviceDefinitions.values()
        executeCommand(services, "pre${command.capitalize()}" as String)
        executeCommand(services, command)
        executeCommand(services, "post${command.capitalize()}" as String)
    }

    void executeCommand() {

        Map targetedServices = spinConfig.services as Map

        if (serviceName) {
            Map definition = targetedServices[serviceName] as Map
            if (!definition) {
                throw new IllegalArgumentException("Service is not defined: \"$serviceName\"")
            }
            targetedServices = [:]
            targetedServices[serviceName] = definition
        }

        executeCommand(targetedServices)
    }

    static void assertNotServiceName(String dep, String serviceName) {
        if (dep == serviceName) {
            throw new IllegalArgumentException("Service \"$serviceName\" cannot list itself as a dependency.")
        }
    }

    static Map toDagNode(Map service) {
        [
                service     : service,
                dependencies: [:]
        ]
    }

    /*
    def loadYamlConfig() {

        Yaml yaml = new Yaml()
        def config = yaml.load(new FileReader(spinConfigFile))

        if (!config || !(config instanceof Map)) {
            throw new IllegalArgumentException("YAML file must be a configuration Map of name-to-value pairs.")
        }

        return config as Map
    }
    */

    Map loadGroovyConfig() {
        ConfigObject config = new ConfigSlurper(environmentName ?: '').parse(spinConfigFile.toURI().toURL())
        //If a plugin lazily checks for a property (e.g. service.someProp?.whatever?.each ), we don't want that traversal
        //to automatically create configuration, as that might have an adverse affect on parsing.  So we convert the
        //ConfigObject to a standard map graph so that lazy config creation won't occur:
        return toMap(config)
    }

    Map toMap(ConfigObject object) {

        Map map = new LinkedHashMap()

        for (Object o : object.entrySet()) {
            Map.Entry next = (Map.Entry) o
            Object key = next.getKey()
            Object value = next.getValue()

            if (value instanceof ConfigObject) {
                value = toMap((ConfigObject) value)
            }

            map.put(key, value)
        }

        return map
    }

    void loadServiceDefinitions() {

        /*if (spinConfigFile.name.endsWith('.yaml')) {
            this.spinConfig = loadYamlConfig()
        } else { */
        this.spinConfig = loadGroovyConfig()
        //}

        Map<String, Map> services = spinConfig.services

        if (!services || !(services instanceof Map)) {
            String msg = "Spin config file must have a 'services' section with serviceName-to-serviceDefinition pairs."
            throw new IllegalArgumentException(msg)
        }

        //ensure config name is available on each service definition:

        Map<String, Map> graphNodes = [:] //service name to graph node map

        services.each { String serviceName, definition ->

            if (!definition || !(definition instanceof Map)) {
                String msg = "$serviceName service definition must be a map of name/value pairs."
                throw new IllegalArgumentException(msg)
            }

            definition.name = serviceName

            //ensure definition also has a a type

            if (!definition.type) {
                String msg = "$serviceName service definition must specify a type."
                throw new IllegalArgumentException(msg)
            }

            //ensure plugin for the specified type is resolvable:
            def plugin = getPlugin(definition)
            if (!plugin) {
                String msg = "No plugin found for $serviceName type \"${definition.type}\""
                throw new IllegalArgumentException(msg)
            }

            //cache plugin instance for this definition:
            definition.plugin = plugin

            Map node = toDagNode(definition)
            graphNodes[serviceName] = node
        }

        //convert list of dependsOn names to a list of nodes for dependency resolution:
        graphNodes.each { String serviceName, Map node ->

            if (node.service.dependsOn) {

                def val = node.service.dependsOn

                def dependencyNames = []

                if (val instanceof String) {
                    dependencyNames << val
                } else {
                    if (!(val instanceof List)) {
                        String msg = "\"$serviceName\" dependsOn must be a string or a list of strings."
                        throw new IllegalArgumentException(msg)
                    }
                    dependencyNames = val as List
                }

                dependencyNames.each { String e ->
                    assertNotServiceName(e, serviceName)
                    if (!services[e]) {
                        throw new IllegalArgumentException("${serviceName} dependency '$e' does not exist.")
                    }
                    node.dependencies[e] = graphNodes[e]
                }
            }
        }

        //order services based on dependsOn:
        List<String> sorted = []
        graphNodes.each { String name, Map node -> visit(node, sorted) }
        //println "NODE ORDER: $sorted"

        //if we're stopping or uninstalling, we need to reverse the service order to ensure dependent services
        //are shut down before those they depend on:
        if (command == 'stop' || command == 'uninstall') {
            sorted = sorted.reverse()
        }

        //re-order services map accordingly:
        Map<String, Map> orderedServices = new LinkedHashMap<>()
        for (String serviceName : sorted) {
            orderedServices[serviceName] = services[serviceName]
        }

        //replace definition map with ordered map so that map iteration occurs in order:
        spinConfig.services = orderedServices
    }

    //Tarjan's algorithm
    void visit(Map node, List<String> sorted) {

        if (node.traversing) {
            String msg = "Dependency graph circular dependency: ${node.service.name} depends on nodes that " +
                    "either directly or indirectly depend on it."
            throw new IllegalStateException(msg)
        }

        if (!node.visited) {

            node.traversing = true

            node.dependencies?.values()?.each { Map dep -> visit(dep, sorted) }

            node.visited = true

            node.remove('traversing')

            if (!sorted.contains(node.service.name)) {
                //noinspection GroovyAssignabilityCheck
                sorted << node.service.name
            }
        }
    }

    static File getSpinInstallDir() {

        String sysPropName = 'app.home'
        String spinHomeDir = System.properties[sysPropName]

        if (spinHomeDir) {
            File f = new File(spinHomeDir)
            if (!f.isDirectory()) {
                printAndExit("System property '$sysPropName' does not reflect a directory.")
            }
            return f
        }

        //else assume running in the IDE during development, default to user.dir:
        String userDirPath = System.properties['user.dir']
        return new File(userDirPath)

        /*
        throw new IllegalStateException("System property '$sysPropName' has not been set.  This is required and " +
                "must equal the spin installation directory path.") */
    }

    static File getSpinUserDir() {
        String path = System.properties['user.home'] + File.separator + ".spin"
        File f = new File(path)
        if (f.exists()) {
            if (f.isDirectory()) {
                return f
            } else {
                throw new IllegalStateException("$path must be a directory.")
            }
        } else {
            if (f.mkdirs()) {
                return f
            } else {
                f = Files.createTempDirectory('spin-temp', null).toFile()
                return f
            }
        }
    }

    static File getUserDir() {
        String path = System.properties['user.dir']
        return new File(path)
    }

    @SuppressWarnings("GroovyMissingReturnStatement")
    File resolveSpinConfigFile(String filePath = null) {

        if (filePath) {
            File f = new File(filePath)
            if (!f.exists()) {
                printAndExit("Specified spin configuration file does not exist: $spinConfigFile")
            }
            if (!f.isFile()) {
                printAndExit("Specified spin configuration file is not a valid file: $spinConfigFile")
            }
            return f
        }

        //locations to check, in order:

        def locations = [
                this.userDir.toString() + File.separator + 'spin.groovy',
                //this.userDir.toString() + File.separator + 'spin.yaml',
                //this.userDir.toString() + File.separator + '.spin.groovy',
                //this.userDir.toString() + File.separator + '.spin.yaml',
                this.userSpinDir.toString() + File.separator + 'spin.groovy'/*,
            this.userSpinDir.toString() + File.separator + 'spin.yaml'*/
        ]

        for (String s : locations) {
            File file = new File(s)
            //println "Checking for file: $file"
            if (file.exists() && file.isFile()) {
                //println "Using config file: $file"
                return file
            }
        }

        //we're only at this point if they didn't supply a file via -f and none of the default locations worked, so
        //we fail as an error

        //convert locations to placeholder representations so as to not confuse the end-user
        locations = locations.collect { location ->
            location.replace(userSpinDir.toString(), '$HOME/.spin').replace(userDir.toString(), '$PWD')
        }

        String msg = "A spin groovy configuration file was not specified as a -f option, and a default " +
                "config file could not be found.  Please create one of the default files or specify -f.\n\n" +
                "Defaults tried in order (first one found wins):\n" +
                locations.join('\n')
        println(msg)
        println()
        printUsageAndExit()
    }

    static void printAndExit(String msg, int status = 1) {
        println msg
        System.exit(status)
    }

    static void printUsage() {
        println "Usage: spin [options] <command> [<service-name>]"
        println()
        println "Options:"
        println "  -h, -help, --h, --help       Show help"
        println "  -f <spin-config-file>        Use <spin-config-file> instead of searching default file locations"
        println "  -p name[,name2,name3,...]    A comma-delimited list of profiles to enable"
        println "  -e <environment-name>        Enable environment configuration for the specified <environment-name>"
        println()
        println "Commands:"
        println "  help                         Show help"
        println "  install                      Install all uninstalled services"
        println "  install <service-name>       Install the service named <service-name>"
        println "  start                        Starts all services"
        println "  start <service-name>         Starts only the service named <service-name>"
        println "  status                       Show the status of all services"
        println "  status <service-name>        Show the status of <service-name>"
        println "  stop                         Stops all services"
        println "  stop <service-name>          Stops only the service named <service-name>"
        println "  uninstall                    Uninstalls all installed services"
        println "  uninstall <service-name>     Uninstalls the service named <service-name>"
    }

    static void printUsageAndExit(int status = 1) {
        printUsage()
        System.exit(status)
    }

    void doMain(String[] args) {

        def commands = ['install', 'start', 'stop', 'uninstall', 'status', 'help']

        this.classLoader = getClass().getClassLoader()
        this.spinInstallDir = getSpinInstallDir()
        this.userSpinDir = getSpinUserDir()
        this.userDir = getUserDir()

        String servicesFilePath = null, anEnvironmentName = null,
               specifiedProfiles = null, aCommand = null, aServiceName = null

        for (int i = 0; i < args.length; i++) {

            String arg = args[i]

            if (aCommand == null) {
                if (arg == '-h' || arg == '--h' || arg == '-help' || arg == '--help') {
                    aCommand = 'help'
                    break
                } else if (arg == '-f') {
                    if (args.length <= i + 1) {
                        printUsageAndExit()
                    }
                    servicesFilePath = args[i + 1]
                    i++
                } else if (arg == '-p') {
                    if (args.length <= i + 1) {
                        printUsageAndExit()
                    }
                    specifiedProfiles = args[i + 1]
                    i++
                } else if (arg == '-e') {
                    if (args.length <= i + 1) {
                        printUsageAndExit()
                    }
                    anEnvironmentName = args[i + 1]
                    i++
                } else {
                    aCommand = arg
                }
            } else {
                aServiceName = arg
            }
        }

        List<String> profiles = []
        if (specifiedProfiles) {
            specifiedProfiles.tokenize(',' as char).each { token ->
                token = token.trim()
                profiles << token
            }
        }
        this.profiles = profiles

        if (aCommand) {
            if (!commands.contains(aCommand)) {
                printUsageAndExit()
            }
        } else {
            aCommand = 'status'
        }

        if (aCommand == 'help') {
            printUsageAndExit(0)
        }

        spinConfigFile = resolveSpinConfigFile(servicesFilePath)
        environmentName = anEnvironmentName
        command = aCommand
        serviceName = aServiceName

        if (environmentName && !spinConfigFile.name.endsWith('.groovy')) {
            printAndExit("The -e configuration flag is only relevant when using a .groovy configuration file. " +
                    "$spinConfigFile is not a .groovy file.")
        }

        if (!command) {
            printUsageAndExit()
        }

        int status = 0

        try {
            loadServiceDefinitions()
            executeCommand()
        } catch (Exception e) {
            println "ERROR: $e.message"
            //e.printStackTrace()
            status = 1
        } finally {
            System.exit(status)
        }
    }

    static void main(String[] args) {
        new Main().doMain(args)
    }
}
