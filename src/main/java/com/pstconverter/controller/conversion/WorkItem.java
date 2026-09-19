package com.pstconverter.controller.conversion;

import com.pstconverter.model.MailMessage;

/**
 * A single unit of work in the producer-consumer conversion pipeline.
 * The producer queues these into the BlockingQueue, and consumers process them.
 * <p>
 * A poison pill ({@code isPoisonPill = true}) signals a consumer to shut down.
 */
public record WorkItem(
    String folderKey,
    MailMessage message,
    boolean isPoisonPill
) {}
