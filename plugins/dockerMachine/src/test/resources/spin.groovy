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
        certs {
            'docker.aue1d.saasure.com' {
                caCert = '$HOME/.strap/Okta-Root-CA.pem'
            }
        }
    }
}
