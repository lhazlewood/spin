package com.leshazlewood.spin.plugin

abstract class AbstractPlugin implements Plugin {

    protected static final Shell shell = new Shell()

    static String trimToNull(String s) {
        String trimmed = s?.trim()
        if (trimmed.equals('')) {
            trimmed = null
        }
        return trimmed
    }

    protected void ensureHealthIfNecessary(Map service) {

        service.healthchecks?.each { String key, Map value ->

            int tries = value.tries ?: 20
            int sleep = value.sleep ?: 3

            //turn to seconds:
            sleep *= 1000

            String command = value.command

            boolean stdout = 'enabled' == service.stdout

            if (!command) {
                String msg = "Service ${service.name} healthchecks '${key}' entry must have a 'command' property value."
                throw new IllegalArgumentException(msg)
            }

            boolean healthy = false

            for(int i = 0; i < tries; i++) {
                try {
                    println "Running \"${service.name}\" '${key}' healthcheck (${i+1}/${tries}) ..."
                    shell.executeAndWait(command, stdout)
                    //commmand returned a zero status, meaning it succeeded, so stop looping:
                    healthy = true
                    break;
                } catch (ShellException ignored) {

                    if (stdout) {
                        String msg = "\"${service.name}\" healthcheck '${key}' failed to return a zero status.  " +
                                "Command: $command\n\nOutput: ${ignored.errorOutput}"
                        println msg
                    }
                }

                println "Sleeping ${sleep/1000} seconds..."
                Thread.sleep(sleep)
            }

            if (!healthy) {
                String msg = "Unable to ensure healthy status for \"${service.name}\" after ${tries} '${key}' healthcheck attempts using command: $command"
                throw new IllegalStateException(msg)
            }
        }
    }

}
