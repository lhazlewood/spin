services {

    vm {
        type = 'dockerMachine'
        machine = 'dev'
        driver {
            name = 'virtualbox'
            options {
                memory = 4096
            }
        }
        routes = ['bridge']
    }
}
