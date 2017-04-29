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

        /*
        println "Process execution environment:"
        env.each {k,v ->
            println(" - $k: $v")
        }
        */

        ProcessBuilder builder = new ProcessBuilder(args)
                .directory(dir)
                .redirectErrorStream(redirectErrorStream);

        def builderEnv = builder.environment()
        builderEnv.putAll(env)

        try {
            return builder.start()
        } catch (Throwable t) {
            String msg = "Unable to execute ProcessBuilder.start(): ${t.message}"
            throw new IllegalStateException(msg, t);
        }
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
