package plugins

@SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
class ShellPlugin implements Plugin {

    @Override
    Map install(Map req) {
        return null
    }

    @Override
    Map start(Map req) {

        if (!req.config.commands) return null

        Shell shell = new Shell()

        for (String command : req.config.commands) {
            Process p = shell.execute(command);
            p.waitFor();
            int exitValue = p.exitValue()
            boolean error = exitValue != 0

            boolean readOutput = error || 'enabled' == req.config.stdout
            String output = readOutput ? p.in.text : null

            if (exitValue == 0 && readOutput) {
                println output
            }

            if (exitValue != 0) {
                throw new IllegalStateException("\"${req.config.name}\" shell command did not return with exit value 0: $command.  Output:\n\n$output")
            }
        }
    }

    @Override
    Map status(Map req) {
        return null
    }

    @Override
    Map stop(Map req) {
        return null
    }

    @Override
    Map uninstall(Map req) {
        return null
    }
}
