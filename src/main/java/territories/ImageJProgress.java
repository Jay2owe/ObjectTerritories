package territories;

import ij.IJ;
import territories.api.ProgressMonitor;

/** Shows analysis progress in Fiji's status bar and stops when Escape is pressed. */
final class ImageJProgress implements ProgressMonitor {

    private final String prefix;

    ImageJProgress(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void update(String step, int done, int total) {
        IJ.showStatus(statusText(prefix, step, done, total));
        IJ.showProgress(done, total);
    }

    /** The Esc hint only while there is something left to stop. */
    static String statusText(String prefix, String step, int done, int total) {
        boolean finished = total > 0 && done >= total;
        return prefix + ": " + step + (finished ? "" : " (press Esc to stop)");
    }

    /**
     * Once Escape has been seen the answer stays {@code true} for this run.
     * The engine polls from its worker threads; reading the volatile latch
     * first stops the JIT from hoisting ImageJ's plain {@code escapePressed}
     * field out of a hot loop, so a key press is seen within one poll.
     */
    private volatile boolean cancelled;

    @Override
    public boolean isCancelled() {
        if (cancelled) return true;
        if (IJ.escapePressed()) cancelled = true;
        return cancelled;
    }
}
