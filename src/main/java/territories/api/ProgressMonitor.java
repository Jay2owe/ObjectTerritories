package territories.api;

/**
 * Receives coarse progress from an analysis and may ask it to stop between
 * steps. A step is one region's territories and interactions, or one density
 * map. Free of ImageJ types, so the Java API stays GUI-free.
 */
public interface ProgressMonitor {

    /** Reports nothing and never cancels. */
    ProgressMonitor NONE = new ProgressMonitor() {
        @Override
        public void update(String step, int done, int total) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    /**
     * Called before each step starts.
     *
     * @param step  short human-readable description of the step about to run
     * @param done  steps already finished
     * @param total steps in the whole analysis
     */
    void update(String step, int done, int total);

    /** Polled before every step; returning {@code true} stops the analysis. */
    boolean isCancelled();
}
