package com.leshazlewood.spin.plugin

class ShellException extends RuntimeException {

    String command
    int exitValue
    String errorOutput

    ShellException(String msg, String command, int exitValue, String errorOutput) {
        super(msg)
        this.command = command
        this.exitValue = exitValue
        this.errorOutput = errorOutput
    }

    ShellException(String command, int exitValue, String errorOutput) {
        this("Command failed to exit cleanly.  Exit value: $exitValue.  Command: $command", command, exitValue, errorOutput)
    }
}
