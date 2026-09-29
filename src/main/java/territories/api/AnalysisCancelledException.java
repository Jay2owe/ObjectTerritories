package territories.api;

/**
 * Thrown when a {@link ProgressMonitor} asks an analysis to stop. Any density
 * or territory images produced before the cancel have already been closed.
 */
public final class AnalysisCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AnalysisCancelledException() {
        super("Object Territories was cancelled");
    }
}
