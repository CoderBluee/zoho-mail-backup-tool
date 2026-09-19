package com.pstconverter.core.filter;

import com.pstconverter.model.MailMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class FilterEngineTest {

    private Properties testProps;

    @BeforeEach
    void setUp() {
        testProps = new Properties();
        // Setup standard testing defaults
        testProps.setProperty("hygiene.removeDuplicates", "true");
        testProps.setProperty("hygiene.dedupSubject", "true");
        testProps.setProperty("hygiene.dedupSender", "true");
        testProps.setProperty("hygiene.dedupMessageId", "true");
        testProps.setProperty("hygiene.dedupRecipients", "false");
        testProps.setProperty("hygiene.dedupDate", "false");
        
        testProps.setProperty("keyword.searchSubject", "true");
        testProps.setProperty("hygiene.skipEmpty", "true");
    }

    @AfterEach
    void tearDown() {
        // Just to be sure cleanup does not fail
    }

    @Test
    void testSQLiteDeduplication() throws Exception {
        // Initialize the FilterEngine with deduplication enabled (from props)
        try (FilterEngine engine = new FilterEngine(testProps)) {
            
            // Create a MailMessage directly
            MailMessage testMsg = new MailMessage("boss@company.com", "Important Test Invoice", "2023-10-15 10:00:00", "Please find attached the invoice.");
            
            // 1. First evaluation should pass (not a duplicate)
            assertTrue(engine.test(testMsg), "First message should pass deduplication filter.");

            // 2. Second evaluation of the same message should fail (duplicate)
            assertFalse(engine.test(testMsg), "Second message should be blocked by deduplication filter.");
            assertEquals("Hygiene: Duplicate message detected", engine.getRejectionReason(testMsg), "Rejection reason should indicate duplicate.");

            // 3. Modifying a deduplication key field should make it pass again
            MailMessage newMsg = new MailMessage("boss@company.com", "Important Test Invoice v2", "2023-10-15 10:00:00", "Please find attached the invoice.");
            
            assertTrue(engine.test(newMsg), "Message with modified subject should pass deduplication filter.");
        }
    }

    @Test
    void testSQLiteDeduplicationReset() throws Exception {
        try (FilterEngine engine = new FilterEngine(testProps)) {
            MailMessage testMsg = new MailMessage("reset@company.com", "Test Reset", "2023-10-15 10:00:00", "Reset body");

            assertTrue(engine.test(testMsg), "First pass should succeed.");
            assertFalse(engine.test(testMsg), "Second pass should fail.");

            // Call reset which executes DELETE FROM dedup_keys
            engine.reset();

            // After reset, it should pass again
            assertTrue(engine.test(testMsg), "Third pass after reset should succeed.");
        }
    }
    
    @Test
    void testSkipEmptyHygiene() throws Exception {
        try (FilterEngine engine = new FilterEngine(testProps)) {
            MailMessage emptyMsg = new MailMessage(null, "", null, "   "); // No date, no subject, no from, whitespace body
            
            assertFalse(engine.test(emptyMsg), "Empty message should be skipped.");
            assertEquals("Hygiene: Skipped empty message", engine.getRejectionReason(emptyMsg));
        }
    }
}
