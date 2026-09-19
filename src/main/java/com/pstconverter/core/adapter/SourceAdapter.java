package com.pstconverter.core.adapter;

import com.pstconverter.model.MailboxFolder;
import com.pstconverter.model.MailMessage;
import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Interface representing a source email container/file format parser.
 * Implementing classes handle validation, folder parsing, and message extraction.
 */
public interface SourceAdapter {

    /**
     * Checks if the given file matches this adapter's supported format.
     *
     * @param file The file to check.
     * @return true if supported, false otherwise.
     */
    boolean canParse(File file);

    /**
     * Checks if the file is a valid representation of the mailbox format.
     *
     * @param file The file to validate.
     * @return true if valid, false otherwise.
     */
    boolean validateFile(File file);

    /**
     * Parses the folder structure of the mailbox file recursively.
     *
     * @param file The mailbox file.
     * @param progressCallback Callback to report progress during parsing.
     * @return MailboxFolder representing root of the parsed structure.
     * @throws Exception if parsing fails.
     */
    MailboxFolder parseFolderStructure(File file, BiConsumer<String, String> progressCallback) throws Exception;

    /**
     * Extracts emails from the specified folder path.
     *
     * @param file The mailbox file.
     * @param folderPath The hierarchical path elements of the target folder.
     * @return List of MailMessages extracted from the folder.
     * @throws Exception if extraction fails.
     */
    List<MailMessage> getEmails(File file, List<String> folderPath) throws Exception;

    /**
     * Extracts emails from the specified folder path starting at offset up to limit.
     *
     * @param file The mailbox file.
     * @param folderPath The hierarchical path elements of the target folder.
     * @param offset Starting item index.
     * @param limit Maximum items to fetch (-1 for no limit).
     * @return List of MailMessages extracted from the folder.
     * @throws Exception if extraction fails.
     */
    default List<MailMessage> getEmails(File file, List<String> folderPath, int offset, int limit) throws Exception {
        java.util.List<MailMessage> list = new java.util.ArrayList<>();
        streamEmails(file, folderPath, list::add, offset, limit);
        return list;
    }

    /**
     * Streams emails from the specified folder path one by one, invoking the consumer.
     * Implementing classes should override this to perform streaming/iterator-based
     * extraction to avoid loading all items into memory.
     *
     * @param file The mailbox file.
     * @param folderPath The hierarchical path elements of the target folder.
     * @param consumer Callback invoked for each extracted MailMessage.
     * @throws Exception if extraction fails.
     */
    default void streamEmails(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer) throws Exception {
        streamEmails(file, folderPath, consumer, 0, -1);
    }

    /**
     * Streams emails from the specified folder path one by one, up to the specified limit.
     *
     * @param file The mailbox file.
     * @param folderPath The hierarchical path elements of the target folder.
     * @param consumer Callback invoked for each extracted MailMessage.
     * @param limit Maximum number of emails to stream, or -1 for no limit.
     * @throws Exception if extraction fails.
     */
    default void streamEmails(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int limit) throws Exception {
        streamEmails(file, folderPath, consumer, 0, limit);
    }

    /**
     * Streams emails from the specified folder path one by one, skipping the first 'offset' messages, up to the limit.
     *
     * @param file The mailbox file.
     * @param folderPath The hierarchical path elements of the target folder.
     * @param consumer Callback invoked for each extracted MailMessage.
     * @param offset Number of messages to skip from the beginning.
     * @param limit Maximum number of emails to stream, or -1 for no limit.
     * @throws Exception if extraction fails.
     */
    default void streamEmails(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int offset, int limit) throws Exception {
        streamEmails(file, folderPath, consumer, offset, limit, null);
    }

    /**
     * Streams emails from the specified folder path one by one, skipping the first 'offset' messages,
     * checking if already migrated via skipCheck before parsing the full message details, up to the limit.
     *
     * @param file The mailbox file.
     * @param folderPath The hierarchical path elements of the target folder.
     * @param consumer Callback invoked for each extracted MailMessage.
     * @param offset Number of messages to skip from the beginning.
     * @param limit Maximum number of emails to stream, or -1 for no limit.
     * @param skipCheck Predicate checking if a message ID has already been migrated.
     * @throws Exception if extraction fails.
     */
    default void streamEmails(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int offset, int limit, java.util.function.Predicate<String> skipCheck) throws Exception {
        int[] count = {0};
        List<MailMessage> emails = getEmails(file, folderPath);
        if (emails != null) {
            for (MailMessage msg : emails) {
                if (count[0] >= offset) {
                    if (limit > 0 && (count[0] - offset) >= limit) {
                        return;
                    }
                    if (skipCheck == null || !skipCheck.test(msg.getUniqueIdentifier())) {
                        consumer.accept(msg);
                    }
                }
                count[0]++;
            }
        }
    }

    /**
     * Streams only basic email metadata (like Sender/Recipient headers) from the specified folder path.
     * This avoids expensive body and attachment parsing.
     *
     * @param file The mailbox file.
     * @param folderPath The hierarchical path elements of the target folder.
     * @param consumer Callback invoked for each extracted MailMessage metadata.
     * @throws Exception if extraction fails.
     */
    default void streamEmailsMetadata(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer) throws Exception {
        streamEmailsMetadata(file, folderPath, consumer, 0, -1);
    }

    /**
     * Streams only basic email metadata from the specified folder path up to the limit.
     *
     * @param file The mailbox file.
     * @param folderPath The hierarchical path elements of the target folder.
     * @param consumer Callback invoked for each extracted MailMessage metadata.
     * @param limit Maximum number of emails to stream, or -1 for no limit.
     * @throws Exception if extraction fails.
     */
    default void streamEmailsMetadata(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int limit) throws Exception {
        streamEmailsMetadata(file, folderPath, consumer, 0, limit);
    }

    /**
     * Streams only basic email metadata from the specified folder path, skipping the first 'offset' messages, up to the limit.
     *
     * @param file The mailbox file.
     * @param folderPath The hierarchical path elements of the target folder.
     * @param consumer Callback invoked for each extracted MailMessage metadata.
     * @param offset Number of messages to skip from the beginning.
     * @param limit Maximum number of emails to stream, or -1 for no limit.
     * @throws Exception if extraction fails.
     */
    default void streamEmailsMetadata(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int offset, int limit) throws Exception {
        streamEmailsMetadata(file, folderPath, consumer, offset, limit, null);
    }

    /**
     * Streams only basic email metadata from the specified folder path, skipping the first 'offset' messages,
     * checking if already migrated via skipCheck, up to the limit.
     *
     * @param file The mailbox file.
     * @param folderPath The hierarchical path elements of the target folder.
     * @param consumer Callback invoked for each extracted MailMessage metadata.
     * @param offset Number of messages to skip from the beginning.
     * @param limit Maximum number of emails to stream, or -1 for no limit.
     * @param skipCheck Predicate checking if a message ID has already been migrated.
     * @throws Exception if extraction fails.
     */
    default void streamEmailsMetadata(File file, List<String> folderPath, java.util.function.Consumer<MailMessage> consumer, int offset, int limit, java.util.function.Predicate<String> skipCheck) throws Exception {
        int[] count = {0};
        streamEmails(file, folderPath, msg -> {
            if (count[0] >= offset) {
                if (limit > 0 && (count[0] - offset) >= limit) {
                    return;
                }
                if (skipCheck == null || !skipCheck.test(msg.getUniqueIdentifier())) {
                    consumer.accept(msg);
                }
            }
            count[0]++;
        }, limit > 0 ? (offset + limit) : -1);
    }


    /**
     * Returns the unique source type code (e.g., "PST", "MBOX", "EML").
     *
     * @return String identifier.
     */
    String getSourceType();

    /**
     * Returns the friendly display name of the source type (e.g., "Outlook PST File").
     *
     * @return String display name.
     */
    String getDisplayName();

    /**
     * Releases system/file resources for a specific mailbox file.
     *
     * @param file The mailbox file.
     */
    default void releaseResources(File file) {}

    /**
     * Releases all cached system/file resources managed by this adapter.
     */
    default void releaseAllResources() {}

    /**
     * Checks if the given folder name represents a system folder in this mailbox format.
     *
     * @param folderName The name of the folder.
     * @return true if it is a system folder, false otherwise.
     */
    default boolean isSystemFolder(String folderName) {
        return false;
    }

    /**
     * Retrieves the visual emoji or icon symbol representing the system folder.
     *
     * @param folderName The name of the folder.
     * @return String containing emoji, or a default folder icon.
     */
    default String getFolderEmoji(String folderName) {
        return "📁";
    }

    /**
     * Auto-detects local mailbox files of this format in standard installation/data locations.
     *
     * @return List of detected files, or an empty list if unsupported.
     */
    default List<File> detectLocalMailboxes() {
        return java.util.Collections.emptyList();
    }

    /**
     * Returns the list of item types supported by this adapter.
     * Common values include: "Emails", "Calendar Items", "Contacts", "Tasks", "Notes", "Journal Entries".
     *
     * @return List of supported item types.
     */
    default List<String> getSupportedItemTypes() {
        return List.of("Emails");
    }

    /**
     * Fetches the full email body (HTML/Text) on-demand for message preview or export.
     *
     * @param file The mailbox file.
     * @param folderPath The hierarchical path of the folder.
     * @param msg The MailMessage instance.
     * @return Full email body string.
     * @throws Exception if fetching fails.
     */
    default String getFullEmailBody(File file, List<String> folderPath, MailMessage msg) throws Exception {
        if (msg == null) return "";
        return msg.getBody();
    }
}

