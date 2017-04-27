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
class TestPlugin implements Plugin {

    Map preInstall(Map req) {
        println "test plugin: preInstall"
        return null
    }

    @Override
    Map install(Map req) {
        println "test plugin: install"
        return null
    }

    Map postInstall(Map req) {
        println "test plugin: postInstall"
        return null
    }

    Map preStart(Map req) {
        println "test plugin: preStart"
        return null
    }

    @Override
    Map start(Map req) {
        println "test plugin: start"
        return null
    }

    Map postStart(Map req) {
        println "test plugin: postStart"
        return null
    }

    @Override
    Map status(Map req) {
        [status: 'stopped']
    }

    Map preStop(Map req) {
        println "test plugin: preStop"
        return null
    }

    @Override
    Map stop(Map req) {
        println "test plugin: stop"
        return null
    }

    Map postStop(Map req) {
        println "test plugin: postStop"
        return null
    }

    Map preUninstall(Map req) {
        println "test plugin: preUninstall"
        return null
    }

    @Override
    Map uninstall(Map req) {
        println "test plugin: uninstall"
        return null
    }

    Map postUninstall(Map req) {
        println "test plugin: postUninstall"
        return null
    }
}
