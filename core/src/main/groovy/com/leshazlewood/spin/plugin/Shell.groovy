package com.leshazlewood.spin.plugin

class Shell {

    final Map env = new HashMap<String,String>(System.getenv())

    File dir

    boolean redirectErrorStream = true

    long timeout = 0

    Shell env(Map env) {
        this.env.putAll(env); return this;
    }

    Shell dir(File dir) {
        this.dir = dir; return this;
    }

    Shell dir(String dir) {
        this.dir = new File(dir); return this;
    }

    Shell redirectErrorStream(boolean redirect=true) {
        this.redirectErrorStream = redirect; return this;
    }

    Shell timeoout(int timeout = 0) {
        this.timeout = timeout; return this;
    }

    Process execute(String command) {

        String shell = env.get('SHELL')
        if (!shell) {
            shell = '/bin/bash'
        }

        ProcessBuilder builder = new ProcessBuilder([shell, '-c', command])
                .directory(dir)
                .redirectErrorStream(redirectErrorStream);

        def builderEnv = builder.environment()
        builderEnv.putAll(env)

        return builder.start()
    }

    Process execute(List<String> args) {

        ProcessBuilder builder = new ProcessBuilder(args)
                .directory(dir)
                .redirectErrorStream(redirectErrorStream);

        def builderEnv = builder.environment()
        builderEnv.putAll(env)

        return builder.start()
    }

    Map executeAndWait(String command, boolean stdout = false) {
        Process p = execute(command)
        p.waitFor();
        int exitValue = p.exitValue()

        String output = (stdout || exitValue != 0) ? p.text : null

        if (exitValue == 0 && output) {
            println output
        }

        if (exitValue != 0) {
            throw new ShellException(command, exitValue, output)
        }

        return [exitValue: exitValue, output: output, process: p]
    }

    ShellResult executeAndWait(List<String> command, boolean stdout = false) {
        final Process p = execute(command)
        p.waitFor();
        ShellResult result = new ShellResult(p)

        int exitValue = result.exitValue

        if (exitValue == 0 && stdout) {
            println result.text
        }

        if (exitValue != 0) {
            throw new ShellException(command.join(' '), exitValue, result.text)
        }

        return result
    }

    int call(String command, boolean consumeOutput = true) {
        Process process = execute(command)
        if (consumeOutput) {
            process.consumeProcessOutput()
        }
        if (timeout) {
            process.waitForOrKill(timeout)
        } else {
            process.waitFor()
        }
        return process.exitValue()
    }

    def eachLine(String command, Closure action) {
        execute(command).in.eachLine(action)
    }

    static class ShellResult {

        final Process process
        private boolean textCached = false
        private String text;

        public ShellResult(Process p) {
            assert p != null
            this.process = p
        }

        Process getProcess() {
            return process;
        }

        String getText() {
            if (!textCached) {
                text = process.text?.trim()
                textCached = true
            }
            return text
        }

        int getExitValue() {
            return process.exitValue()
        }

        def eachLine(Closure action) {
            process.in.eachLine(action)
        }
    }

}
