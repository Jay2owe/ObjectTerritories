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

    @Override
    public boolean isCancelled() {
        return IJ.escapePressed();
    }
}
