package plugins

interface Plugin {

    Map install(Map req)

    Map start(Map req)

    Map status(Map req)

    Map stop(Map req)

    Map uninstall(Map req)
}