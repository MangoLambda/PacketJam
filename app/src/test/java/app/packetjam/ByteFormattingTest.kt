package app.packetjam

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormattingTest {
    @Test fun formatsBytesAndBinaryUnits() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1_572_864))
    }
}
