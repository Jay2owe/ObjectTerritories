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
        IJ.showStatus(prefix + ": " + step + " (press Esc to stop)");
        IJ.showProgress(done, total);
    }

    @Override
    public boolean isCancelled() {
        return IJ.escapePressed();
    }
}
