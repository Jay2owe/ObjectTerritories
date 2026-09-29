package territories;

import ij.IJ;
import ij.Macro;
import ij.macro.Interpreter;

/**
 * Ends a headless command run with one clear log line.
 *
 * <p>Rethrowing the original exception does not work in Fiji: ImageJ's command
 * executor catches it, prints the whole stack trace to standard output and
 * lets the calling macro carry on as if the command had succeeded. Instead the
 * message is logged, the calling macro is aborted, and ImageJ's own silent
 * "Macro canceled" signal is returned for the caller to throw.
 */
final class HeadlessFailure {

    private HeadlessFailure() {
    }

    static RuntimeException abort(String commandName, Throwable error) {
        IJ.log("[" + commandName + "] ERROR: " + message(error));
        if (!(error instanceof IllegalArgumentException)) {
            // Not bad input: keep the trace for a bug report, but off stdout.
            error.printStackTrace();
        }
        return abortMacro();
    }

    static RuntimeException cancelled(String commandName) {
        IJ.log("[" + commandName + "] cancelled");
        return abortMacro();
    }

    private static RuntimeException abortMacro() {
        Interpreter.abort();
        return new RuntimeException(Macro.MACRO_CANCELED);
    }

    static String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }
}
