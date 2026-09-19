package com.pstconverter.core.output.handlers;

import com.pstconverter.core.output.OutputHandler;
import com.pstconverter.model.MailMessage;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;
import java.util.List;

public class PdfOutputHandler implements OutputHandler {
    @Override
    public void export(File targetFolder, List<MailMessage> emails) throws Exception {
        export(targetFolder, emails, "Keep Attachments in Folder");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling) throws Exception {
        export(targetFolder, emails, attachmentHandling, "Individual File per Email (One file per message)");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling, String exportStructure) throws Exception {
        export(targetFolder, emails, attachmentHandling, exportStructure, "Original Subject");
    }

    @Override
    public void export(File targetFolder, List<MailMessage> emails, String attachmentHandling, String exportStructure, String namingConvention) throws Exception {
        if (emails == null || emails.isEmpty()) return;

        boolean incSubject = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_subject", "true"));
        boolean incFrom = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_from", "true"));
        boolean incTo = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_to", "true"));
        boolean incCcBcc = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_ccbcc", "true"));
        boolean incDate = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_date", "true"));
        boolean incBody = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_body", "true"));

        if (exportStructure != null && exportStructure.startsWith("Single Combined File")) {
            File file = new File(targetFolder, targetFolder.getName() + ".pdf");
            try (PDDocument document = new PDDocument(MemoryUsageSetting.setupTempFileOnly())) {
                int index = 1;
                for (MailMessage msg : emails) {
                    renderEmailPage(document, msg, index, true, incSubject, incFrom, incTo, incCcBcc, incDate, incBody);
                    
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    handlePdfAttachments(document, msg, targetFolder, index, safeSubject, attachmentHandling);
                    index++;
                }
                document.save(file);
            } catch (Exception e) {
                System.err.println("Error exporting to combined PDF: " + e.getMessage());
                throw e;
            }
        } else {
            int index = 1;
            for (MailMessage msg : emails) {
                String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "pdf");
                File file = new File(targetFolder, fileName);
                try (PDDocument document = new PDDocument(MemoryUsageSetting.setupTempFileOnly())) {
                    renderEmailPage(document, msg, index, false, incSubject, incFrom, incTo, incCcBcc, incDate, incBody);
                    
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    handlePdfAttachments(document, msg, targetFolder, index, safeSubject, attachmentHandling);
                    document.save(file);
                    
                    index++;
                } catch (Exception e) {
                    System.err.println("Error exporting to PDF: " + e.getMessage());
                    throw e;
                }
            }
        }
    }

    private void writePdfHeaders(PDPageContentStream contentStream, MailMessage msg, boolean incSubject, boolean incFrom, boolean incTo, boolean incCcBcc, boolean incDate) throws Exception {
        if (incSubject) {
            contentStream.showText("Subject: " + OutputHandler.sanitizePdfText(msg.getSubject()));
            contentStream.newLineAtOffset(0, -15);
        }
        if (incFrom) {
            contentStream.showText("From: " + OutputHandler.sanitizePdfText(msg.getFrom()));
            contentStream.newLineAtOffset(0, -15);
            String senderAddr = msg.getSenderAddress();
            if (!senderAddr.isEmpty()) {
                contentStream.showText("Sender Address: " + OutputHandler.sanitizePdfText(senderAddr));
                contentStream.newLineAtOffset(0, -15);
            }
        }
        if (incTo) {
            String displayTo = msg.getTo();
            if (!displayTo.isEmpty()) {
                contentStream.showText("To: " + OutputHandler.sanitizePdfText(displayTo));
                contentStream.newLineAtOffset(0, -15);
            }
        }
        if (incCcBcc) {
            String cc = msg.getCc();
            if (!cc.isEmpty()) {
                contentStream.showText("Cc: " + OutputHandler.sanitizePdfText(cc));
                contentStream.newLineAtOffset(0, -15);
            }
            String bcc = msg.getBcc();
            if (!bcc.isEmpty()) {
                contentStream.showText("Bcc: " + OutputHandler.sanitizePdfText(bcc));
                contentStream.newLineAtOffset(0, -15);
            }
        }
        if (incDate) {
            contentStream.showText("Date: " + OutputHandler.sanitizePdfText(msg.getDate()));
            contentStream.newLineAtOffset(0, -15);
            String msgId = msg.getMessageId();
            if (!msgId.isEmpty()) {
                contentStream.showText("Message-ID: " + OutputHandler.sanitizePdfText(msgId));
                contentStream.newLineAtOffset(0, -15);
            }
        }
        if (incSubject) {
            String status = msg.getStatus();
            if (!status.isEmpty()) {
                contentStream.showText("Status: " + OutputHandler.sanitizePdfText(status));
                contentStream.newLineAtOffset(0, -15);
            }
            String importance = msg.getImportance();
            if (!importance.isEmpty()) {
                contentStream.showText("Importance: " + OutputHandler.sanitizePdfText(importance));
                contentStream.newLineAtOffset(0, -15);
            }
            String flags = msg.getMessageFlags();
            if (!flags.isEmpty() && !flags.equals("None")) {
                contentStream.showText("Flags: " + OutputHandler.sanitizePdfText(flags));
                contentStream.newLineAtOffset(0, -15);
            }
        }
    }

    private class RenderState implements AutoCloseable {
        final PDDocument document;
        PDPage currentPage;
        PDPageContentStream contentStream;
        float currentY;
        final float marginX = 50;
        final float marginY = 50;
        final float width;
        final float height;

        RenderState(PDDocument document) throws Exception {
            this.document = document;
            this.currentPage = createPageFromSettings();
            this.document.addPage(this.currentPage);
            this.contentStream = new PDPageContentStream(document, this.currentPage);
            org.apache.pdfbox.pdmodel.common.PDRectangle mediaBox = this.currentPage.getMediaBox();
            this.width = mediaBox.getWidth();
            this.height = mediaBox.getHeight();
            this.currentY = this.height - marginY;
        }

        void nextPage() throws Exception {
            if (contentStream != null) {
                contentStream.close();
            }
            currentPage = createPageFromSettings();
            document.addPage(currentPage);
            contentStream = new PDPageContentStream(document, currentPage);
            currentY = height - marginY;
        }

        @Override
        public void close() throws Exception {
            if (contentStream != null) {
                contentStream.close();
            }
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void writePdfBody(RenderState state, MailMessage msg, org.apache.pdfbox.pdmodel.font.PDFont font, float fontSize, float[] color, float lineSpacing) throws Exception {
        String rawBody = msg.getBody();
        if (rawBody == null || rawBody.trim().isEmpty()) return;

        java.util.regex.Pattern dataImgPattern = java.util.regex.Pattern.compile("(?is)<img\\b[^>]*?\\bsrc=[\"'](data:image/[^;]+;base64,([A-Za-z0-9+/=\\s]+))[\"'][^>]*?>");
        java.util.regex.Matcher matcher = dataImgPattern.matcher(rawBody);

        int lastEnd = 0;
        int imgCount = 1;
        while (matcher.find()) {
            String textChunk = rawBody.substring(lastEnd, matcher.start());
            String plainText = OutputHandler.getPlainTextBody(textChunk);
            if (!plainText.trim().isEmpty()) {
                String safePlainText = OutputHandler.sanitizePdfText(plainText, true);
                drawTextParagraphs(state, safePlainText, font, fontSize, color, lineSpacing);
            }

            String base64Data = matcher.group(2).replaceAll("\\s+", "");
            try {
                byte[] imgBytes = java.util.Base64.getDecoder().decode(base64Data);
                if (imgBytes != null && imgBytes.length > 0) {
                    org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject imgObj = 
                        org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(state.document, imgBytes, "inline_img_" + imgCount++);
                    
                    float imgWidth = imgObj.getWidth();
                    float imgHeight = imgObj.getHeight();
                    float maxWidth = state.width - 2 * state.marginX;
                    float maxHeight = state.height - 2 * state.marginY - 40;

                    float scale = 1.0f;
                    if (imgWidth > maxWidth) {
                        scale = maxWidth / imgWidth;
                    }
                    if (imgHeight * scale > maxHeight) {
                        scale = Math.min(scale, maxHeight / imgHeight);
                    }

                    float renderWidth = Math.max(1, imgWidth * scale);
                    float renderHeight = Math.max(1, imgHeight * scale);

                    if (state.currentY - renderHeight < state.marginY) {
                        state.nextPage();
                    }

                    float imgX = state.marginX + Math.max(0, (maxWidth - renderWidth) / 2f);
                    state.contentStream.drawImage(imgObj, imgX, state.currentY - renderHeight, renderWidth, renderHeight);
                    state.currentY -= (renderHeight + lineSpacing);
                }
            } catch (Exception ex) {
                // Ignore malformed image data and continue smoothly
            }

            lastEnd = matcher.end();
        }

        // Render remaining text
        String remainingText = rawBody.substring(lastEnd);
        String plainText = OutputHandler.getPlainTextBody(remainingText);
        if (!plainText.trim().isEmpty()) {
            String safePlainText = OutputHandler.sanitizePdfText(plainText, true);
            drawTextParagraphs(state, safePlainText, font, fontSize, color, lineSpacing);
        }
    }

    private float safeGetStringWidth(org.apache.pdfbox.pdmodel.font.PDFont font, String text) {
        if (text == null || text.isEmpty()) return 0f;
        String safe = OutputHandler.sanitizePdfText(text, false);
        if (safe.isEmpty()) return 0f;
        try {
            return font.getStringWidth(safe);
        } catch (Exception e) {
            float total = 0f;
            for (int i = 0; i < safe.length(); i++) {
                char c = safe.charAt(i);
                try {
                    total += font.getStringWidth(String.valueOf(c));
                } catch (Exception ex2) {
                    total += 500f;
                }
            }
            return total;
        }
    }

    private void drawTextParagraphs(RenderState state, String body, org.apache.pdfbox.pdmodel.font.PDFont font, float fontSize, float[] color, float lineSpacing) throws Exception {
        if (body == null || body.trim().isEmpty()) return;

        state.contentStream.setNonStrokingColor(color[0], color[1], color[2]);
        String[] paragraphs = body.split("\\r?\\n");
        float startX = state.marginX;
        float maxWidth = state.width - 2 * state.marginX;

        boolean lastWasEmpty = false;
        for (String paragraph : paragraphs) {
            String trimmedPara = paragraph.trim();
            if (trimmedPara.isEmpty()) {
                if (!lastWasEmpty) {
                    state.currentY -= (lineSpacing * 0.4f);
                    if (state.currentY < state.marginY) {
                        state.nextPage();
                    }
                    lastWasEmpty = true;
                }
                continue;
            }
            lastWasEmpty = false;

            String[] words = paragraph.split("(?<=\\s)|(?=\\s)");
            StringBuilder currentLine = new StringBuilder();
            float currentLineWidth = 0;

            for (String word : words) {
                if (word.isEmpty()) continue;
                float wordWidth = (safeGetStringWidth(font, word) / 1000f) * fontSize;

                if (currentLineWidth + wordWidth <= maxWidth) {
                    currentLine.append(word);
                    currentLineWidth += wordWidth;
                } else {
                    if (wordWidth > maxWidth) {
                        for (int i = 0; i < word.length(); i++) {
                            char c = word.charAt(i);
                            float charWidth = (safeGetStringWidth(font, String.valueOf(c)) / 1000f) * fontSize;
                            if (currentLineWidth + charWidth <= maxWidth) {
                                currentLine.append(c);
                                currentLineWidth += charWidth;
                            } else {
                                drawTextLine(state, font, fontSize, color, currentLine.toString(), startX, lineSpacing);
                                currentLine = new StringBuilder();
                                currentLine.append(c);
                                currentLineWidth = charWidth;
                            }
                        }
                    } else {
                        drawTextLine(state, font, fontSize, color, currentLine.toString(), startX, lineSpacing);
                        currentLine = new StringBuilder(word);
                        currentLineWidth = wordWidth;
                    }
                }
            }
            if (currentLine.length() > 0) {
                drawTextLine(state, font, fontSize, color, currentLine.toString(), startX, lineSpacing);
            }
        }
    }

    private void drawTextLine(RenderState state, org.apache.pdfbox.pdmodel.font.PDFont font, float fontSize, float[] color, String text, float x, float lineSpacing) throws Exception {
        if (state.currentY - lineSpacing < state.marginY) {
            state.nextPage();
        }
        drawText(state.contentStream, font, fontSize, color, text, x, state.currentY);
        state.currentY -= lineSpacing;
    }

    private float drawWrappedText(PDPageContentStream contentStream, org.apache.pdfbox.pdmodel.font.PDFont font, float fontSize, float[] color, String text, float startX, float startY, float maxWidth, float lineSpacing) throws Exception {
        if (text == null || text.isEmpty()) return startY;
        
        String[] paragraphs = text.split("\\r?\\n");
        float currentY = startY;
        
        for (String paragraph : paragraphs) {
            String[] words = paragraph.split("(?<=\\s)|(?=\\s)");
            StringBuilder currentLine = new StringBuilder();
            float currentLineWidth = 0;
            
            for (String word : words) {
                if (word.isEmpty()) continue;
                float wordWidth = (safeGetStringWidth(font, word) / 1000f) * fontSize;
                
                if (currentLineWidth + wordWidth <= maxWidth) {
                    currentLine.append(word);
                    currentLineWidth += wordWidth;
                } else {
                    if (wordWidth > maxWidth) {
                        for (int i = 0; i < word.length(); i++) {
                            char c = word.charAt(i);
                            float charWidth = (safeGetStringWidth(font, String.valueOf(c)) / 1000f) * fontSize;
                            if (currentLineWidth + charWidth <= maxWidth) {
                                currentLine.append(c);
                                currentLineWidth += charWidth;
                            } else {
                                drawText(contentStream, font, fontSize, color, currentLine.toString(), startX, currentY);
                                currentY -= lineSpacing;
                                currentLine = new StringBuilder();
                                currentLine.append(c);
                                currentLineWidth = charWidth;
                            }
                        }
                    } else {
                        drawText(contentStream, font, fontSize, color, currentLine.toString(), startX, currentY);
                        currentY -= lineSpacing;
                        currentLine = new StringBuilder(word);
                        currentLineWidth = wordWidth;
                    }
                }
            }
            if (currentLine.length() > 0) {
                drawText(contentStream, font, fontSize, color, currentLine.toString(), startX, currentY);
                currentY -= lineSpacing;
            }
        }
        return currentY;
    }

    private float computeWrappedHeight(org.apache.pdfbox.pdmodel.font.PDFont font, float fontSize, String text, float maxWidth, float lineSpacing) throws Exception {
        if (text == null || text.trim().isEmpty()) return 0;
        
        String[] paragraphs = text.split("\\r?\\n");
        int totalLines = 0;
        
        for (String paragraph : paragraphs) {
            String[] words = paragraph.split("(?<=\\s)|(?=\\s)");
            float currentLineWidth = 0;
            int paragraphLines = 0;
            boolean hasContent = false;
            
            for (String word : words) {
                if (word.isEmpty()) continue;
                hasContent = true;
                float wordWidth = (safeGetStringWidth(font, word) / 1000f) * fontSize;
                if (currentLineWidth + wordWidth <= maxWidth) {
                    currentLineWidth += wordWidth;
                    if (paragraphLines == 0) paragraphLines = 1;
                } else {
                    if (wordWidth > maxWidth) {
                        for (int i = 0; i < word.length(); i++) {
                            char c = word.charAt(i);
                            float charWidth = (safeGetStringWidth(font, String.valueOf(c)) / 1000f) * fontSize;
                            if (currentLineWidth + charWidth <= maxWidth) {
                                currentLineWidth += charWidth;
                                if (paragraphLines == 0) paragraphLines = 1;
                            } else {
                                paragraphLines++;
                                currentLineWidth = charWidth;
                            }
                        }
                    } else {
                        paragraphLines++;
                        currentLineWidth = wordWidth;
                    }
                }
            }
            if (paragraphLines == 0 && hasContent) {
                paragraphLines = 1;
            }
            totalLines += paragraphLines;
        }
        return totalLines * lineSpacing;
    }

    private void embedAttachments(PDDocument document, List<MailMessage.Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return;
        try {
            org.apache.pdfbox.pdmodel.PDDocumentCatalog catalog = document.getDocumentCatalog();
            org.apache.pdfbox.pdmodel.PDDocumentNameDictionary names = catalog.getNames();
            if (names == null) {
                names = new org.apache.pdfbox.pdmodel.PDDocumentNameDictionary(catalog);
                catalog.setNames(names);
            }
            org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode efContainer = names.getEmbeddedFiles();
            if (efContainer == null) {
                efContainer = new org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode();
                names.setEmbeddedFiles(efContainer);
            }
            java.util.Map<String, org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification> embeddedFileMap = new java.util.HashMap<>();
            
            for (MailMessage.Attachment att : attachments) {
                String filename = OutputHandler.sanitizeFileName(att.getFilename());
                byte[] data = att.getData();
                if (data == null) continue;
                
                org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification fs = new org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification();
                fs.setFile(filename);
                
                java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(data);
                org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile ef = new org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile(document, bis);
                ef.setSize(data.length);
                ef.setCreationDate(new java.util.GregorianCalendar());
                fs.setEmbeddedFile(ef);
                
                embeddedFileMap.put(filename, fs);
            }
            
            java.util.Map<String, org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification> existingMap = efContainer.getNames();
            if (existingMap != null) {
                java.util.Map<String, org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification> mergedMap = new java.util.HashMap<>(existingMap);
                mergedMap.putAll(embeddedFileMap);
                efContainer.setNames(mergedMap);
            } else {
                efContainer.setNames(embeddedFileMap);
            }
        } catch (Exception e) {
            System.err.println("Failed to embed attachments in PDF: " + e.getMessage());
        }
    }

    @Override
    public OutputHandler.Session openSession(File targetFolder, String attachmentHandling, String exportStructure, String namingConvention) throws Exception {
        boolean isCombined = exportStructure != null && exportStructure.startsWith("Single Combined File");
        
        boolean incSubject = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_subject", "true"));
        boolean incFrom = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_from", "true"));
        boolean incTo = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_to", "true"));
        boolean incCcBcc = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_ccbcc", "true"));
        boolean incDate = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_date", "true"));
        boolean incBody = Boolean.parseBoolean(com.pstconverter.util.SettingsManager.getSetting("export_field_body", "true"));

        if (isCombined) {
            File file = new File(targetFolder, targetFolder.getName() + ".pdf");
            PDDocument document = new PDDocument(MemoryUsageSetting.setupTempFileOnly());
            return new OutputHandler.Session() {
                private int index = 1;
                @Override
                public void writeMessage(MailMessage msg) throws Exception {
                    renderEmailPage(document, msg, index, true, incSubject, incFrom, incTo, incCcBcc, incDate, incBody);
                    
                    String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                    handlePdfAttachments(document, msg, targetFolder, index, safeSubject, attachmentHandling);
                    index++;
                }

                @Override
                public void close() throws Exception {
                    try {
                        document.save(file);
                    } finally {
                        document.close();
                    }
                }
            };
        } else {
            return new OutputHandler.Session() {
                private int index = 1;
                @Override
                public void writeMessage(MailMessage msg) throws Exception {
                    String fileName = OutputHandler.getFormattedFileName(msg, namingConvention, index, "pdf");
                    File file = new File(targetFolder, fileName);
                    try (PDDocument document = new PDDocument(MemoryUsageSetting.setupTempFileOnly())) {
                        renderEmailPage(document, msg, index, false, incSubject, incFrom, incTo, incCcBcc, incDate, incBody);
                        
                        String safeSubject = OutputHandler.sanitizeFileName(msg.getSubject());
                        handlePdfAttachments(document, msg, targetFolder, index, safeSubject, attachmentHandling);
                        document.save(file);
                    }
                    index++;
                }

                @Override
                public void close() throws Exception {
                }
            };
        }
    }

    private PDPage createPageFromSettings() {
        String pageSizeSetting = com.pstconverter.util.SettingsManager.getSetting("pdf_page_size", "A4");
        String pageOrientSetting = com.pstconverter.util.SettingsManager.getSetting("pdf_page_orientation", "Portrait");

        org.apache.pdfbox.pdmodel.common.PDRectangle rect = org.apache.pdfbox.pdmodel.common.PDRectangle.A4;
        if ("Letter".equalsIgnoreCase(pageSizeSetting)) {
            rect = org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER;
        } else if ("Legal".equalsIgnoreCase(pageSizeSetting)) {
            rect = org.apache.pdfbox.pdmodel.common.PDRectangle.LEGAL;
        }

        if ("Landscape".equalsIgnoreCase(pageOrientSetting)) {
rect = new org.apache.pdfbox.pdmodel.common.PDRectangle(rect.getHeight(), rect.getWidth());
        }
        return new PDPage(rect);
    }

    private void drawText(PDPageContentStream contentStream, org.apache.pdfbox.pdmodel.font.PDFont font, float fontSize, float[] color, String text, float x, float y) throws Exception {
        if (text == null || text.isEmpty()) return;
        String safe = OutputHandler.sanitizePdfText(text, false);
        if (safe.trim().isEmpty()) return;
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.setNonStrokingColor(color[0], color[1], color[2]);
        contentStream.newLineAtOffset(x, y);
        try {
            contentStream.showText(safe);
        } catch (Exception e) {
            StringBuilder clean = new StringBuilder();
            for (char c : safe.toCharArray()) {
                if (c >= 32 && c <= 126) {
                    clean.append(c);
                } else {
                    clean.append(' ');
                }
            }
            contentStream.showText(clean.toString());
        }
        contentStream.endText();
    }

    private void renderEmailPage(PDDocument document, MailMessage msg, int index, boolean isCombined,
                                 boolean incSubject, boolean incFrom, boolean incTo, boolean incCcBcc, boolean incDate, boolean incBody) throws Exception {
        msg = getSanitizedMessageCopy(msg);
        try (RenderState state = new RenderState(document)) {
            String template = com.pstconverter.util.SettingsManager.getSetting("pdf_layout_template", "Standard Card Layout");
            float width = state.width;
            float marginX = state.marginX;

            float[] primaryColor = {15/255f, 23/255f, 42/255f};    // #0f172a (dark gray/black)
            float[] secondaryColor = {71/255f, 85/255f, 105/255f}; // #475569 (slate gray)
            float[] labelColor = {51/255f, 65/255f, 85/255f};      // #334155 (classic label gray)
            float[] accentColor = {79/255f, 70/255f, 229/255f};    // #4f46e5 (indigo/purple)
            float[] redColor = {239/255f, 68/255f, 68/255f};       // #ef4444 (red)
            float[] lightRed = {254/255f, 226/255f, 226/255f};     // #fee2e2 (light red badge)

            org.apache.pdfbox.pdmodel.font.PDFont fontRegular = PDType1Font.HELVETICA;
            org.apache.pdfbox.pdmodel.font.PDFont fontBold = PDType1Font.HELVETICA_BOLD;
            org.apache.pdfbox.pdmodel.font.PDFont fontMono = PDType1Font.COURIER;

            String subjectText = incSubject ? msg.getSubject() : "";
            if (isCombined) {
                subjectText = "Email #" + index + (subjectText.isEmpty() ? "" : " - " + subjectText);
            } else if (subjectText.isEmpty()) {
                subjectText = "Email Message";
            }

            List<MailMessage.Attachment> attList = msg.getAttachmentList();
            String attSummary = "";
            if (attList != null && !attList.isEmpty()) {
                StringBuilder attSb = new StringBuilder();
                for (int i = 0; i < attList.size(); i++) {
                    MailMessage.Attachment att = attList.get(i);
                    if (i > 0) attSb.append(", ");
                    attSb.append(att.getFilename());
                    byte[] d = att.getData();
                    if (d != null && d.length > 0) {
                        attSb.append(" (").append(formatFileSize(d.length)).append(")");
                    }
                }
                attSummary = attSb.toString();
            }

            float bodyStartY = state.currentY - 180;

            if ("Minimalist Clean Text".equalsIgnoreCase(template)) {
                // Subject (wrapped)
                float subjHeight = computeWrappedHeight(fontBold, 12, subjectText, width - 2 * marginX, 16);
                float nextY = state.currentY - 25;
                drawWrappedText(state.contentStream, fontBold, 12, primaryColor, subjectText, marginX, nextY, width - 2 * marginX, 16);
                nextY -= subjHeight;

                // Meta (wrapped)
                StringBuilder meta = new StringBuilder();
                if (incFrom) meta.append("From: ").append(msg.getFrom()).append("    |    ");
                if (incDate) meta.append("Date: ").append(msg.getDate());
                if (!attSummary.isEmpty()) meta.append("    |    Attachments: ").append(attSummary);
                float metaHeight = computeWrappedHeight(fontRegular, 8.5f, meta.toString(), width - 2 * marginX, 12);
                drawWrappedText(state.contentStream, fontRegular, 8.5f, secondaryColor, meta.toString(), marginX, nextY - 5, width - 2 * marginX, 12);
                nextY -= (metaHeight + 5);

                // Separator line
                state.contentStream.setStrokingColor(203/255f, 213/255f, 225/255f); // #cbd5e1
                state.contentStream.setLineWidth(1f);
                state.contentStream.moveTo(marginX, nextY - 5);
                state.contentStream.lineTo(width - marginX, nextY - 5);
                state.contentStream.stroke();
                
                bodyStartY = nextY - 20;
            } else if ("Compact Grid Header".equalsIgnoreCase(template)) {
                // Subject (wrapped)
                float subjHeight = computeWrappedHeight(fontBold, 12, subjectText, width - 2 * marginX, 16);
                float nextY = state.currentY - 25;
                drawWrappedText(state.contentStream, fontBold, 12, primaryColor, subjectText, marginX, nextY, width - 2 * marginX, 16);
                nextY -= subjHeight;

                // Grid layout rows - column width is half of the content width minus margin spacing
                float colWidth = (width - 2 * marginX) / 2 - 10;
                float firstRowHeight = 0;
                if (incFrom) {
                    firstRowHeight = Math.max(firstRowHeight, computeWrappedHeight(fontRegular, 9, "From: " + msg.getFrom(), colWidth, 13));
                }
                if (incTo) {
                    firstRowHeight = Math.max(firstRowHeight, computeWrappedHeight(fontRegular, 9, "To: " + msg.getTo(), colWidth, 13));
                }
                if (incFrom) {
                    drawWrappedText(state.contentStream, fontRegular, 9, secondaryColor, "From: " + msg.getFrom(), marginX, nextY - 10, colWidth, 13);
                }
                if (incTo) {
                    drawWrappedText(state.contentStream, fontRegular, 9, secondaryColor, "To: " + msg.getTo(), marginX + colWidth + 20, nextY - 10, colWidth, 13);
                }
                nextY -= (firstRowHeight > 0 ? firstRowHeight + 10 : 0);

                float secondRowHeight = 0;
                if (incDate) {
                    secondRowHeight = Math.max(secondRowHeight, computeWrappedHeight(fontRegular, 9, "Date: " + msg.getDate(), colWidth, 13));
                }
                if (incCcBcc && !msg.getCc().isEmpty()) {
                    secondRowHeight = Math.max(secondRowHeight, computeWrappedHeight(fontRegular, 9, "Cc: " + msg.getCc(), colWidth, 13));
                }
                if (incDate) {
                    drawWrappedText(state.contentStream, fontRegular, 9, secondaryColor, "Date: " + msg.getDate(), marginX, nextY - 10, colWidth, 13);
                }
                if (incCcBcc && !msg.getCc().isEmpty()) {
                    drawWrappedText(state.contentStream, fontRegular, 9, secondaryColor, "Cc: " + msg.getCc(), marginX + colWidth + 20, nextY - 10, colWidth, 13);
                }
                nextY -= (secondRowHeight > 0 ? secondRowHeight + 10 : 0);

                if (!attSummary.isEmpty()) {
                    float attRowHeight = computeWrappedHeight(fontRegular, 8.5f, "Attachments: " + attSummary, width - 2 * marginX, 13);
                    drawWrappedText(state.contentStream, fontRegular, 8.5f, accentColor, "Attachments: " + attSummary, marginX, nextY - 10, width - 2 * marginX, 13);
                    nextY -= (attRowHeight + 10);
                }

                // Separator line
                state.contentStream.setStrokingColor(203/255f, 213/255f, 225/255f); // #cbd5e1
                state.contentStream.setLineWidth(1f);
                state.contentStream.moveTo(marginX, nextY - 10);
                state.contentStream.lineTo(width - marginX, nextY - 10);
                state.contentStream.stroke();

                bodyStartY = nextY - 25;
            } else if ("Detailed Metadata Header".equalsIgnoreCase(template)) {
                // Subject (wrapped)
                float subjHeight = computeWrappedHeight(fontBold, 12, subjectText, width - 2 * marginX, 16);
                float nextY = state.currentY - 25;
                drawWrappedText(state.contentStream, fontBold, 12, primaryColor, subjectText, marginX, nextY, width - 2 * marginX, 16);
                nextY -= (subjHeight + 10);

                // Compute dynamic box height
                float boxTop = nextY;
                float boxWidth = width - 2 * marginX;
                float innerWidth = boxWidth - 30; // padding inside the box
                
                float boxContentHeight = 10; // top/bottom padding
                if (!msg.getMessageId().isEmpty()) {
                    boxContentHeight += computeWrappedHeight(fontMono, 7.5f, "ID: " + msg.getMessageId(), innerWidth, 13);
                }
                if (incFrom) {
                    boxContentHeight += computeWrappedHeight(fontRegular, 8.5f, "From: " + msg.getFrom(), innerWidth, 13);
                }
                if (incTo) {
                    boxContentHeight += computeWrappedHeight(fontRegular, 8.5f, "To: " + msg.getTo(), innerWidth, 13);
                }
                if (incCcBcc && (!msg.getCc().isEmpty() || !msg.getBcc().isEmpty())) {
                    String ccBcc = "";
                    if (!msg.getCc().isEmpty()) ccBcc += "Cc: " + msg.getCc() + "  ";
                    if (!msg.getBcc().isEmpty()) ccBcc += "Bcc: " + msg.getBcc();
                    boxContentHeight += computeWrappedHeight(fontRegular, 8.5f, ccBcc, innerWidth, 13);
                }
                if (incDate) {
                    boxContentHeight += computeWrappedHeight(fontRegular, 8.5f, "Date: " + msg.getDate(), innerWidth, 13);
                }
                if (!attSummary.isEmpty()) {
                    boxContentHeight += computeWrappedHeight(fontRegular, 8.5f, "Attachments: " + attSummary, innerWidth, 13);
                }
                
                float boxY = boxTop - boxContentHeight;
                
                // Card background
                state.contentStream.setNonStrokingColor(248/255f, 250/255f, 252/255f);
                state.contentStream.addRect(marginX, boxY, boxWidth, boxContentHeight);
                state.contentStream.fill();
                
                // Card border
                state.contentStream.setStrokingColor(226/255f, 232/255f, 240/255f);
                state.contentStream.setLineWidth(1f);
                state.contentStream.addRect(marginX, boxY, boxWidth, boxContentHeight);
                state.contentStream.stroke();
                
                // Draw metadata
                float drawY = boxTop - 15;
                if (!msg.getMessageId().isEmpty()) {
                    float h = computeWrappedHeight(fontMono, 7.5f, "ID: " + msg.getMessageId(), innerWidth, 13);
                    drawWrappedText(state.contentStream, fontMono, 7.5f, secondaryColor, "ID: " + msg.getMessageId(), marginX + 15, drawY, innerWidth, 13);
                    drawY -= h;
                }
                if (incFrom) {
                    float h = computeWrappedHeight(fontRegular, 8.5f, "From: " + msg.getFrom(), innerWidth, 13);
                    drawWrappedText(state.contentStream, fontRegular, 8.5f, secondaryColor, "From: " + msg.getFrom(), marginX + 15, drawY, innerWidth, 13);
                    drawY -= h;
                }
                if (incTo) {
                    float h = computeWrappedHeight(fontRegular, 8.5f, "To: " + msg.getTo(), innerWidth, 13);
                    drawWrappedText(state.contentStream, fontRegular, 8.5f, secondaryColor, "To: " + msg.getTo(), marginX + 15, drawY, innerWidth, 13);
                    drawY -= h;
                }
                if (incCcBcc && (!msg.getCc().isEmpty() || !msg.getBcc().isEmpty())) {
                    String ccBcc = "";
                    if (!msg.getCc().isEmpty()) ccBcc += "Cc: " + msg.getCc() + "  ";
                    if (!msg.getBcc().isEmpty()) ccBcc += "Bcc: " + msg.getBcc();
                    float h = computeWrappedHeight(fontRegular, 8.5f, ccBcc, innerWidth, 13);
                    drawWrappedText(state.contentStream, fontRegular, 8.5f, secondaryColor, ccBcc, marginX + 15, drawY, innerWidth, 13);
                    drawY -= h;
                }
                if (incDate) {
                    float h = computeWrappedHeight(fontRegular, 8.5f, "Date: " + msg.getDate(), innerWidth, 13);
                    drawWrappedText(state.contentStream, fontRegular, 8.5f, secondaryColor, "Date: " + msg.getDate(), marginX + 15, drawY, innerWidth, 13);
                    
                    // High importance badge
                    if ("High".equalsIgnoreCase(msg.getImportance())) {
                        state.contentStream.setNonStrokingColor(lightRed[0], lightRed[1], lightRed[2]);
                        state.contentStream.addRect(marginX + 220, drawY - 2, 35, 11);
                        state.contentStream.fill();
                        
                        state.contentStream.setStrokingColor(252/255f, 165/255f, 165/255f);
                        state.contentStream.setLineWidth(0.5f);
                        state.contentStream.addRect(marginX + 220, drawY - 2, 35, 11);
                        state.contentStream.stroke();
                        
                        drawText(state.contentStream, fontBold, 6.5f, redColor, "HIGH", marginX + 228, drawY);
                    }
                    drawY -= h;
                }
                if (!attSummary.isEmpty()) {
                    float h = computeWrappedHeight(fontRegular, 8.5f, "Attachments: " + attSummary, innerWidth, 13);
                    drawWrappedText(state.contentStream, fontRegular, 8.5f, accentColor, "Attachments: " + attSummary, marginX + 15, drawY, innerWidth, 13);
                    drawY -= h;
                }
                
                // Separator line below card
                state.contentStream.setStrokingColor(203/255f, 213/255f, 225/255f);
                state.contentStream.setLineWidth(1f);
                state.contentStream.moveTo(marginX, boxY - 15);
                state.contentStream.lineTo(width - marginX, boxY - 15);
                state.contentStream.stroke();
                
                bodyStartY = boxY - 30;
            } else if ("Classic Email Archive".equalsIgnoreCase(template)) {
                float currentY = state.currentY - 25;
                float labelWidth = 60;
                float valueWidth = width - 2 * marginX - labelWidth;
                
                if (incSubject) {
                    drawText(state.contentStream, fontBold, 10, labelColor, "Subject: ", marginX, currentY);
                    float h = computeWrappedHeight(fontRegular, 10, subjectText, valueWidth, 15);
                    drawWrappedText(state.contentStream, fontRegular, 10, primaryColor, subjectText, marginX + labelWidth, currentY, valueWidth, 15);
                    currentY -= h;
                }
                if (incFrom) {
                    drawText(state.contentStream, fontBold, 10, labelColor, "From: ", marginX, currentY);
                    float h = computeWrappedHeight(fontRegular, 10, msg.getFrom(), valueWidth, 15);
                    drawWrappedText(state.contentStream, fontRegular, 10, primaryColor, msg.getFrom(), marginX + labelWidth, currentY, valueWidth, 15);
                    currentY -= h;
                }
                if (incTo) {
                    drawText(state.contentStream, fontBold, 10, labelColor, "To: ", marginX, currentY);
                    float h = computeWrappedHeight(fontRegular, 10, msg.getTo(), valueWidth, 15);
                    drawWrappedText(state.contentStream, fontRegular, 10, primaryColor, msg.getTo(), marginX + labelWidth, currentY, valueWidth, 15);
                    currentY -= h;
                }
                if (incCcBcc && (!msg.getCc().isEmpty() || !msg.getBcc().isEmpty())) {
                    String ccBcc = msg.getCc() + " / " + msg.getBcc();
                    drawText(state.contentStream, fontBold, 10, labelColor, "Cc/Bcc: ", marginX, currentY);
                    float h = computeWrappedHeight(fontRegular, 10, ccBcc, valueWidth, 15);
                    drawWrappedText(state.contentStream, fontRegular, 10, primaryColor, ccBcc, marginX + labelWidth, currentY, valueWidth, 15);
                    currentY -= h;
                }
                if (incDate) {
                    drawText(state.contentStream, fontBold, 10, labelColor, "Date: ", marginX, currentY);
                    float h = computeWrappedHeight(fontRegular, 10, msg.getDate(), valueWidth, 15);
                    drawWrappedText(state.contentStream, fontRegular, 10, primaryColor, msg.getDate(), marginX + labelWidth, currentY, valueWidth, 15);
                    currentY -= h;
                }
                if (!attSummary.isEmpty()) {
                    drawText(state.contentStream, fontBold, 10, labelColor, "Attachments: ", marginX, currentY);
                    float h = computeWrappedHeight(fontRegular, 9.5f, attSummary, valueWidth, 14);
                    drawWrappedText(state.contentStream, fontRegular, 9.5f, accentColor, attSummary, marginX + labelWidth, currentY, valueWidth, 14);
                    currentY -= h;
                }
                
                float lineY = currentY - 5;
                state.contentStream.setStrokingColor(0, 0, 0);
                state.contentStream.setLineWidth(1.5f);
                state.contentStream.moveTo(marginX, lineY);
                state.contentStream.lineTo(width - marginX, lineY);
                state.contentStream.stroke();
                
                state.contentStream.setLineWidth(0.5f);
                state.contentStream.moveTo(marginX, lineY - 4);
                state.contentStream.lineTo(width - marginX, lineY - 4);
                state.contentStream.stroke();
                
                bodyStartY = lineY - 20;
            } else {
                // "Standard Card Layout" or fallback
                float cardTop = state.currentY - 10;
                float cardWidth = width - 2 * marginX;
                float innerWidth = cardWidth - 30; // padding inside the card
                
                // Compute dynamic card height
                float boxContentHeight = 10; // base padding
                // Subject is at the top inside the card
                boxContentHeight += computeWrappedHeight(fontBold, 12, subjectText, innerWidth, 16);
                boxContentHeight += 10; // spacer after subject
                
                if (incFrom) {
                    boxContentHeight += computeWrappedHeight(fontRegular, 9, "From: " + msg.getFrom(), innerWidth, 14);
                }
                if (incTo) {
                    boxContentHeight += computeWrappedHeight(fontRegular, 9, "To: " + msg.getTo(), innerWidth, 14);
                }
                if (incCcBcc && (!msg.getCc().isEmpty() || !msg.getBcc().isEmpty())) {
                    String ccBcc = "";
                    if (!msg.getCc().isEmpty()) ccBcc += "Cc: " + msg.getCc() + "  ";
                    if (!msg.getBcc().isEmpty()) ccBcc += "Bcc: " + msg.getBcc();
                    boxContentHeight += computeWrappedHeight(fontRegular, 9, ccBcc, innerWidth, 14);
                }
                if (incDate) {
                    boxContentHeight += computeWrappedHeight(fontRegular, 9, "Date: " + msg.getDate(), innerWidth, 14);
                }
                if (!attSummary.isEmpty()) {
                    boxContentHeight += computeWrappedHeight(fontRegular, 8.5f, "Attachments: " + attSummary, innerWidth, 14);
                }
                
                float cardY = cardTop - boxContentHeight;
                float cardHeight = boxContentHeight;
                
                // Draw card background
                state.contentStream.setNonStrokingColor(248/255f, 250/255f, 252/255f);
                state.contentStream.addRect(marginX, cardY, cardWidth, cardHeight);
                state.contentStream.fill();
                
                // Draw card border
                state.contentStream.setStrokingColor(226/255f, 232/255f, 240/255f);
                state.contentStream.setLineWidth(1f);
                state.contentStream.addRect(marginX, cardY, cardWidth, cardHeight);
                state.contentStream.stroke();
                
                // Subject
                float drawY = cardTop - 20;
                float subjHeight = computeWrappedHeight(fontBold, 12, subjectText, innerWidth, 16);
                drawWrappedText(state.contentStream, fontBold, 12, accentColor, subjectText, marginX + 15, drawY, innerWidth, 16);
                drawY -= (subjHeight + 10);
                
                if (incFrom) {
                    float h = computeWrappedHeight(fontRegular, 9, "From: " + msg.getFrom(), innerWidth, 14);
                    drawWrappedText(state.contentStream, fontRegular, 9, secondaryColor, "From: " + msg.getFrom(), marginX + 15, drawY, innerWidth, 14);
                    drawY -= h;
                }
                if (incTo) {
                    float h = computeWrappedHeight(fontRegular, 9, "To: " + msg.getTo(), innerWidth, 14);
                    drawWrappedText(state.contentStream, fontRegular, 9, secondaryColor, "To: " + msg.getTo(), marginX + 15, drawY, innerWidth, 14);
                    drawY -= h;
                }
                if (incCcBcc && (!msg.getCc().isEmpty() || !msg.getBcc().isEmpty())) {
                    String ccBcc = "";
                    if (!msg.getCc().isEmpty()) ccBcc += "Cc: " + msg.getCc() + "  ";
                    if (!msg.getBcc().isEmpty()) ccBcc += "Bcc: " + msg.getBcc();
                    float h = computeWrappedHeight(fontRegular, 9, ccBcc, innerWidth, 14);
                    drawWrappedText(state.contentStream, fontRegular, 9, secondaryColor, ccBcc, marginX + 15, drawY, innerWidth, 14);
                    drawY -= h;
                }
                if (incDate) {
                    float h = computeWrappedHeight(fontRegular, 9, "Date: " + msg.getDate(), innerWidth, 14);
                    drawWrappedText(state.contentStream, fontRegular, 9, secondaryColor, "Date: " + msg.getDate(), marginX + 15, drawY, innerWidth, 14);
                    drawY -= h;
                }
                if (!attSummary.isEmpty()) {
                    drawWrappedText(state.contentStream, fontRegular, 8.5f, accentColor, "Attachments: " + attSummary, marginX + 15, drawY, innerWidth, 14);
                }
                
                // Separator line below card
                state.contentStream.setStrokingColor(203/255f, 213/255f, 225/255f);
                state.contentStream.setLineWidth(1f);
                state.contentStream.moveTo(marginX, cardY - 15);
                state.contentStream.lineTo(width - marginX, cardY - 15);
                state.contentStream.stroke();
                
                bodyStartY = cardY - 30;
            }

            state.currentY = bodyStartY;

            // Write Body
            if (incBody) {
                writePdfBody(state, msg, fontRegular, 10, primaryColor, 14);
            }
        }
    }

    private void handlePdfAttachments(PDDocument document, MailMessage msg, File targetFolder, int index, String safeSubject, String attachmentHandling) {
        boolean shouldEmbed = false;
        boolean shouldSaveLocally = false;

        if (attachmentHandling != null) {
            if (attachmentHandling.contains("Embed Attachments")) {
                shouldEmbed = true;
                shouldSaveLocally = false;
            } else if (attachmentHandling.contains("Separate") || attachmentHandling.contains("Keep Attachments")) {
                shouldEmbed = false;
                shouldSaveLocally = true;
            } else if (attachmentHandling.contains("Skip") || attachmentHandling.contains("Drop") || attachmentHandling.contains("Ignore")) {
                shouldEmbed = false;
                shouldSaveLocally = false;
            } else {
                shouldEmbed = false;
                shouldSaveLocally = true;
            }
        } else {
            String pdfAttachMode = com.pstconverter.util.SettingsManager.getSetting("pdf_attachment_mode", "Extract and save in separate folder next to output");
            if (pdfAttachMode.equalsIgnoreCase("Embed directly into output file")) {
                shouldEmbed = true;
                shouldSaveLocally = false;
            } else if (pdfAttachMode.equalsIgnoreCase("Extract and save in separate folder next to output")) {
                shouldEmbed = false;
                shouldSaveLocally = true;
            }
        }

        if (shouldEmbed) {
            embedAttachments(document, msg.getAttachmentList());
        }
        if (shouldSaveLocally) {
            OutputHandler.saveAttachments(targetFolder, msg, index, safeSubject, attachmentHandling != null ? attachmentHandling : "Separate Attachment Files");
        }
    }

    private MailMessage getSanitizedMessageCopy(MailMessage original) {
        MailMessage copy = new MailMessage(
            OutputHandler.sanitizePdfText(original.getFrom()),
            OutputHandler.sanitizePdfText(original.getSubject()),
            OutputHandler.sanitizePdfText(original.getDate()),
            original.getBody(),
            original.getItemType()
        );
        copy.setSenderAddress(OutputHandler.sanitizePdfText(original.getSenderAddress()));
        copy.setTo(OutputHandler.sanitizePdfText(original.getTo()));
        copy.setCc(OutputHandler.sanitizePdfText(original.getCc()));
        copy.setBcc(OutputHandler.sanitizePdfText(original.getBcc()));
        copy.setMessageId(OutputHandler.sanitizePdfText(original.getMessageId()));
        copy.setStatus(OutputHandler.sanitizePdfText(original.getStatus()));
        copy.setImportance(OutputHandler.sanitizePdfText(original.getImportance()));
        copy.setMessageFlags(OutputHandler.sanitizePdfText(original.getMessageFlags()));
        
        for (MailMessage.Attachment att : original.getAttachmentList()) {
            copy.addAttachment(att.getFilename(), att.getData());
        }
        for (java.util.Map.Entry<String, String> entry : original.getMetadata().entrySet()) {
            copy.addMetadata(entry.getKey(), OutputHandler.sanitizePdfText(entry.getValue()));
        }
        return copy;
    }
}