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
