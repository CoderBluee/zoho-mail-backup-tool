package com.pstconverter.core.output;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutputFactoryTest {

    @Test
    void testGetHandlerForValidFormat() {
        // "PDF" should load PdfOutputHandler
        OutputHandler handler = OutputFactory.getHandler("PDF");
        assertNotNull(handler, "Handler for 'PDF' should not be null");
        assertEquals("com.pstconverter.core.output.handlers.PdfOutputHandler", handler.getClass().getName(), 
                "Should return an instance of PdfOutputHandler");
    }

    @Test
    void testGetHandlerForCloudFormat() {
        // "Office 365" should load CloudOutputHandler
        OutputHandler handler = OutputFactory.getHandler("Office 365");
        assertNotNull(handler, "Handler for 'Office 365' should not be null");
        assertEquals("com.pstconverter.core.output.handlers.CloudOutputHandler", handler.getClass().getName(), 
                "Should return an instance of CloudOutputHandler");
    }

    @Test
    void testGetHandlerForInvalidFormat() {
        // Should gracefully handle non-existent formats
        OutputHandler handler = OutputFactory.getHandler("Invalid Format XYZ");
        assertNull(handler, "Handler for an invalid format should return null");
    }

    @Test
    void testGetSupportedCategories() {
        java.util.List<OutputCategory> categories = OutputFactory.getSupportedCategories();
        assertNotNull(categories);
        assertFalse(categories.isEmpty(), "Factory should return a non-empty list of supported categories");
        assertTrue(categories.contains(OutputCategory.PDF), "Supported categories should contain PDF");
    }
}
