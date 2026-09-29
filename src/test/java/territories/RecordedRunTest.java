package territories;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A failed or cancelled dialog run takes its macro line back out of the
 * Recorder. Before 0.3.1 the line stayed, so the recorded macro repeated a run
 * that had failed.
 */
public class RecordedRunTest {

    private static final String LINE = "run(\"Object Territories\", \"label_1=A output=[C:/out]\");\n";

    @Test
    public void removesOnlyTheLastCopyOfTheLine() {
        String before = "open(\"C:/a.tif\");\n" + LINE + "selectImage(\"A\");\n" + LINE;
        assertEquals("open(\"C:/a.tif\");\n" + LINE + "selectImage(\"A\");\n",
                RecordedRun.withoutLastLine(before, LINE));
    }

    @Test
    public void handlesWindowsLineEndingsAndAMissingFinalNewline() {
        String body = LINE.substring(0, LINE.length() - 1);
        assertEquals("a;\r\nb;\r\n",
                RecordedRun.withoutLastLine("a;\r\n" + body + "\r\nb;\r\n", LINE));
        assertEquals("a;\n", RecordedRun.withoutLastLine("a;\n" + body, LINE));
        assertEquals("", RecordedRun.withoutLastLine(LINE, LINE));
    }

    @Test
    public void leavesTextAloneWhenTheLineIsAbsentOrOnlyPartOfALine() {
        String other = "run(\"Object Territories\", \"label_1=B\");\n";
        assertEquals(other, RecordedRun.withoutLastLine(other, LINE));
        String embedded = "// " + LINE;
        assertEquals(embedded, RecordedRun.withoutLastLine(embedded, LINE));
        String extended = LINE.substring(0, LINE.length() - 1) + " // again\n";
        assertEquals(extended, RecordedRun.withoutLastLine(extended, LINE));
        assertEquals("", RecordedRun.withoutLastLine("", LINE));
    }
}
