package territories;

import ij.IJ;
import org.junit.Test;

import java.awt.event.KeyEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The status bar read "done (press Esc to stop)" after every finished run. */
public class ImageJProgressTest {

    @Test
    public void escHintShowsOnlyWhileStepsRemain() {
        assertEquals("Object Territories: region Field: territories (press Esc to stop)",
                ImageJProgress.statusText("Object Territories", "region Field: territories", 0, 5));
        assertEquals("Object Territories: region Field: density A (press Esc to stop)",
                ImageJProgress.statusText("Object Territories", "region Field: density A", 4, 5));
        assertEquals("Object Territories: done",
                ImageJProgress.statusText("Object Territories", "done", 5, 5));
        assertEquals("Object Territories Batch: done",
                ImageJProgress.statusText("Object Territories Batch", "done", 2, 2));
    }

    /** The engine polls from worker threads; once Escape is seen the run stays cancelled. */
    @Test
    public void escapeIsLatchedForTheRun() {
        IJ.resetEscape();
        try {
            ImageJProgress progress = new ImageJProgress("Object Territories");
            assertFalse(progress.isCancelled());
            IJ.setKeyDown(KeyEvent.VK_ESCAPE);
            assertTrue(IJ.escapePressed());
            assertTrue(progress.isCancelled());
            IJ.resetEscape();
            assertTrue(progress.isCancelled());
            assertFalse(new ImageJProgress("Object Territories").isCancelled());
        } finally {
            IJ.resetEscape();
            IJ.setKeyUp(KeyEvent.VK_ESCAPE);
        }
    }
}
