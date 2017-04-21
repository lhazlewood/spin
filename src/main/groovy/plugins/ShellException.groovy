package plugins

class ShellException extends RuntimeException {

    String command
    int exitValue
    String errorOutput

    public ShellException(String msg, String command, int exitValue, String errorOutput) {
        super(msg)
        this.command = command
        this.exitValue = exitValue
        this.errorOutput = errorOutput
    }

    public ShellException(String command, int exitValue, String errorOutput) {
        this("Command failed to exit cleanly.  Exit value: $exitValue.  Command: $command",
                command, exitValue, errorOutput)
    }
}
