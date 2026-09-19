package com.pstconverter.core.adapter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceAdapterFactoryTest {

    @Test
    void testGetAdapterForValidFormat() {
        // "pst" is the default built-in format
        SourceAdapter adapter = SourceAdapterFactory.getAdapterByType("pst");
        assertNotNull(adapter, "Adapter for 'pst' should not be null");
        assertEquals("com.pstconverter.pst.PstSourceAdapter", adapter.getClass().getName(), 
                "Should return an instance of PstSourceAdapter");
    }

    @Test
    void testGetAdapterForInvalidFormat() {
        // Should gracefully handle non-existent formats
        SourceAdapter adapter = SourceAdapterFactory.getAdapterByType("invalidFormat");
        assertNull(adapter, "Adapter for an invalid format should return null");
    }

    @Test
    void testIsSupported() {
        assertTrue(SourceAdapterFactory.isSupported(new java.io.File("test.pst")), "Factory should support 'pst' file extension");
        assertFalse(SourceAdapterFactory.isSupported(new java.io.File("test.unknown")), "Factory should reject unknown extension");
    }
}
