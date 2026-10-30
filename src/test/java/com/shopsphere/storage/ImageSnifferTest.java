package com.shopsphere.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ImageSnifferTest {
    private static byte[] pad(int... head) { byte[] b = new byte[32]; for (int i = 0; i < head.length; i++) b[i] = (byte) head[i]; return b; }

    @Test void detectsRealFormats() {
        assertEquals("jpg", ImageSniffer.detect(pad(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals("png", ImageSniffer.detect(pad(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)));
        assertEquals("webp", ImageSniffer.detect(pad('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')));
    }
    @Test void rejectsEverythingElse() {
        assertNull(ImageSniffer.detect("<svg onload=alert(1)>".getBytes()));   // SVG can carry scripts: not allowed
        assertNull(ImageSniffer.detect("GIF89a....................".getBytes()));
        assertNull(ImageSniffer.detect(new byte[4]));
        assertNull(ImageSniffer.detect(null));
    }
}
