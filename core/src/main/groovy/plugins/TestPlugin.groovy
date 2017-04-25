package plugins

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
