package territories;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
