package com.pstconverter.util;

import com.pstconverter.controller.conversion.ConversionTelemetryTracker.FolderTelemetry;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class PdfReportGenerator {

    private static class ReportState implements AutoCloseable {
        final PDDocument document;
        PDPage currentPage;
        PDPageContentStream contentStream;
        float currentY;
        final float marginX = 45;
        final float marginY = 45;
        float width;
        float height;
        int pageNumber = 0;
        final String timestamp;

        ReportState(PDDocument document) throws Exception {
            this.document = document;
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            newPage();
        }

        void newPage() throws Exception {
            if (contentStream != null) {
                contentStream.close();
            }
            currentPage = new PDPage(PDRectangle.A4);
            document.addPage(currentPage);
            pageNumber++;
            
            PDRectangle mediaBox = currentPage.getMediaBox();
            this.width = mediaBox.getWidth();
            this.height = mediaBox.getHeight();
            
            contentStream = new PDPageContentStream(document, currentPage);
            currentY = height - marginY;
            
            // Draw page border
            contentStream.setLineWidth(0.5f);
            contentStream.setStrokingColor(200, 200, 200);
            contentStream.addRect(marginX - 10, marginY - 10, width - 2 * marginX + 20, height - 2 * marginY + 20);
            contentStream.stroke();
            
            drawHeader();
            drawFooter();
            
            currentY -= 40;
        }

        void checkPageBreak(float requiredSpace) throws Exception {
            if (currentY - requiredSpace < marginY) {
                newPage();
            }
        }

        void drawHeader() throws Exception {
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 8);
            contentStream.setNonStrokingColor(128, 128, 128);
            contentStream.newLineAtOffset(marginX, height - marginY + 12);
            contentStream.showText("MIGRATION EXECUTION COMPLIANCE REPORT - CONFIDENTIAL");
            contentStream.endText();

            contentStream.setLineWidth(0.5f);
            contentStream.setStrokingColor(180, 180, 180);
            contentStream.moveTo(marginX, height - marginY + 6);
            contentStream.lineTo(width - marginX, height - marginY + 6);
            contentStream.stroke();
        }

        void drawFooter() throws Exception {
            PDPageContentStream fs = new PDPageContentStream(document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
            fs.beginText();
            fs.setFont(PDType1Font.HELVETICA, 8);
            fs.setNonStrokingColor(128, 128, 128);
            fs.newLineAtOffset(marginX, marginY - 20);
            fs.showText("Generated: " + timestamp + "  |  " + com.pstconverter.config.BrandConfig.TOOL_NAME);
            fs.endText();
            
            String pgNumStr = "Page " + pageNumber;
            fs.beginText();
            fs.setFont(PDType1Font.HELVETICA, 8);
            fs.newLineAtOffset(width - marginX - 35, marginY - 20);
            fs.showText(pgNumStr);
            fs.endText();
            fs.close();
        }

        void drawText(String text, PDType1Font font, float fontSize, int r, int g, int b) throws Exception {
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.setNonStrokingColor(r, g, b);
            contentStream.newLineAtOffset(marginX, currentY);
            contentStream.showText(sanitizeText(text));
            contentStream.endText();
            currentY -= (fontSize + 6);
        }

        void drawLabelValueRow(String label, String value, float fontSize) throws Exception {
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
            contentStream.setNonStrokingColor(60, 60, 60);
            contentStream.newLineAtOffset(marginX, currentY);
            contentStream.showText(sanitizeText(label));
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, fontSize);
            contentStream.setNonStrokingColor(100, 100, 100);
            contentStream.newLineAtOffset(marginX + 170, currentY);
            contentStream.showText(sanitizeText(value));
            contentStream.endText();
            
            currentY -= (fontSize + 6);
        }

        void drawTableRow(String col1, String col2, String col3, String col4, String col5, boolean isHeader) throws Exception {
            float c1 = marginX;
            float c2 = marginX + 245;
            float c3 = marginX + 310;
            float c4 = marginX + 375;
            float c5 = marginX + 440;
            float maxCol1Width = 235f;

            PDType1Font font = isHeader ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
            int r = isHeader ? 30 : 60;
            int g = isHeader ? 41 : 60;
            int b = isHeader ? 59 : 60;

            String cleanCol1 = sanitizeText(col1);
            List<String> col1Lines = isHeader ? List.of(cleanCol1) : wrapTextToWidth(cleanCol1, maxCol1Width, font, 8f);
            float rowHeight = Math.max(16f, col1Lines.size() * 11f + 4f);
            checkPageBreak(rowHeight + 4f);

            float y = currentY;
            contentStream.beginText();
            contentStream.setFont(font, 8);
            contentStream.setNonStrokingColor(r, g, b);
            contentStream.newLineAtOffset(c1, y);
            contentStream.showText(col1Lines.get(0));
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(font, 8);
            contentStream.setNonStrokingColor(r, g, b);
            contentStream.newLineAtOffset(c2, y);
            contentStream.showText(sanitizeText(col2));
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(font, 8);
            contentStream.setNonStrokingColor(r, g, b);
            contentStream.newLineAtOffset(c3, y);
            contentStream.showText(sanitizeText(col3));
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(font, 8);
            contentStream.setNonStrokingColor(r, g, b);
            contentStream.newLineAtOffset(c4, y);
            contentStream.showText(sanitizeText(col4));
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(font, 8);
            contentStream.setNonStrokingColor(r, g, b);
            contentStream.newLineAtOffset(c5, y);
            contentStream.showText(sanitizeText(col5));
            contentStream.endText();

            for (int i = 1; i < col1Lines.size(); i++) {
                y -= 11f;
                contentStream.beginText();
                contentStream.setFont(font, 8);
                contentStream.setNonStrokingColor(r, g, b);
                contentStream.newLineAtOffset(c1, y);
                contentStream.showText(col1Lines.get(i));
                contentStream.endText();
            }

            contentStream.setLineWidth(0.3f);
            contentStream.setStrokingColor(220, 220, 220);
            contentStream.moveTo(marginX, y - 4f);
            contentStream.lineTo(width - marginX, y - 4f);
            contentStream.stroke();

            currentY = y - 14f;
        }

        private List<String> wrapTextToWidth(String text, float maxWidth, PDType1Font font, float fontSize) {
            List<String> lines = new java.util.ArrayList<>();
            if (text == null || text.trim().isEmpty()) {
                lines.add("");
                return lines;
            }
            String[] tokens = text.split("(?<=[/\\\\\\s])");
            StringBuilder currentLine = new StringBuilder();
            for (String token : tokens) {
                String test = currentLine.length() == 0 ? token : currentLine.toString() + token;
                if (getStringWidth(test, font, fontSize) <= maxWidth) {
                    currentLine.append(token);
                } else {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(token);
                    } else {
                        for (char c : token.toCharArray()) {
                            if (getStringWidth(currentLine.toString() + c, font, fontSize) <= maxWidth) {
                                currentLine.append(c);
                            } else {
                                lines.add(currentLine.toString());
                                currentLine = new StringBuilder(String.valueOf(c));
                            }
                        }
                    }
                }
            }
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
            return lines;
        }

        private float getStringWidth(String text, PDType1Font font, float fontSize) {
            try {
                return font.getStringWidth(text) / 1000f * fontSize;
            } catch (Exception e) {
                return text.length() * (fontSize * 0.5f);
            }
        }

        private String sanitizeText(String text) {
            if (text == null) return "";
            return text.replace("\u2022", "- ")
                       .replace("\u2013", "-")
                       .replace("\u2014", "-")
                       .replace("\u2018", "'")
                       .replace("\u2019", "'")
                       .replace("\u201c", "\"")
                       .replace("\u201d", "\"")
                       .replaceAll("[^\\x20-\\x7E]", "")
                       .trim();
        }

        @Override
        public void close() throws Exception {
            if (contentStream != null) {
                contentStream.close();
            }
        }
    }

    public static void generateReport(
        File outputFile, 
        long successCount, 
        long skippedCount, 
        long failedCount, 
        String elapsedDuration, 
        String sizeStr, 
        String outputPath,
        String targetFormat,
        String exportStructure,
        String attachmentHandling,
        String namingConvention,
        List<String> activeFilters, 
        List<String> importedFiles, 
        Map<String, FolderTelemetry> folderTelemetryMap, 
        Map<String, Integer> skipReasons
    ) throws Exception {

        try (PDDocument document = new PDDocument(MemoryUsageSetting.setupTempFileOnly())) {
            try (ReportState state = new ReportState(document)) {
                
                boolean brandingEnabled = Boolean.parseBoolean(SettingsManager.getSetting("pdf_branding_enabled", "false"));
                String companyName = SettingsManager.getSetting("pdf_branding_company", "");
                String contactInfo = SettingsManager.getSetting("pdf_branding_contact", "");
                String logoPath = SettingsManager.getSetting("pdf_branding_logo", "");

                if (brandingEnabled && logoPath != null && !logoPath.trim().isEmpty()) {
                    File logoFile = new File(logoPath);
                    if (logoFile.exists() && logoFile.isFile()) {
                        try {
                            org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject pdImage = 
                                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromFileByExtension(logoFile, document);
                            float imgWidth = pdImage.getWidth();
                            float imgHeight = pdImage.getHeight();
                            float scale = Math.min(120f / imgWidth, 40f / imgHeight);
                            float targetW = imgWidth * scale;
                            float targetH = imgHeight * scale;
                            state.contentStream.drawImage(pdImage, state.width - state.marginX - targetW, state.currentY - 30, targetW, targetH);
                        } catch (Exception e) {
                            System.err.println("Failed to embed logo in PDF report: " + e.getMessage());
                        }
                    }
                }

                // --- TITLE SECTOR ---
                state.drawText("MIGRATION COMPLIANCE RUN REPORT", PDType1Font.HELVETICA_BOLD, 18, 30, 41, 59);
                if (brandingEnabled && !companyName.isEmpty()) {
                    state.drawText("Prepared by: " + companyName, PDType1Font.HELVETICA_BOLD, 10, 79, 70, 229);
                    if (!contactInfo.isEmpty()) {
                        state.drawText("Contact: " + contactInfo, PDType1Font.HELVETICA_OBLIQUE, 9, 100, 100, 100);
                    }
                } else {
                    state.drawText("Official Proof of Mailbox Migration Execution", PDType1Font.HELVETICA_OBLIQUE, 10, 100, 110, 120);
                }
                state.currentY -= 10;

                // Section Divider
                state.contentStream.setLineWidth(1.5f);
                state.contentStream.setStrokingColor(99, 102, 241);
                state.contentStream.moveTo(state.marginX, state.currentY);
                state.contentStream.lineTo(state.width - state.marginX, state.currentY);
                state.contentStream.stroke();
                state.currentY -= 20;

                // --- SUMMARY STATISTICS SECTOR ---
                state.drawText("1. RUN METRICS SUMMARY", PDType1Font.HELVETICA_BOLD, 12, 67, 56, 202);
                state.currentY -= 5;
                state.drawLabelValueRow("Successfully Converted:", String.valueOf(successCount), 9.5f);
                state.drawLabelValueRow("Skipped (Exclusions):", String.valueOf(skippedCount), 9.5f);
                state.drawLabelValueRow("Failed Items:", String.valueOf(failedCount), 9.5f);
                state.drawLabelValueRow("Migration Duration:", elapsedDuration, 9.5f);
                state.drawLabelValueRow("Exported Data Size:", sizeStr, 9.5f);
                state.currentY -= 15;

                // --- MIGRATION CONFIGURATION SECTOR ---
                state.checkPageBreak(140);
                state.drawText("2. MIGRATION CONFIGURATION", PDType1Font.HELVETICA_BOLD, 12, 67, 56, 202);
                state.currentY -= 5;
                state.drawLabelValueRow("Target Export Format:", targetFormat != null ? targetFormat : "N/A", 9);
                state.drawLabelValueRow("Output Destination:", outputPath != null ? outputPath : "Cloud Migration Repository", 9);
                state.drawLabelValueRow("File Output Structure:", exportStructure != null ? exportStructure : "N/A", 9);
                state.drawLabelValueRow("Attachment Handling:", attachmentHandling != null ? attachmentHandling : "N/A", 9);
                state.drawLabelValueRow("Naming Convention:", namingConvention != null ? namingConvention : "N/A", 9);
                
                state.currentY -= 5;
                state.drawText("Imported Source Files:", PDType1Font.HELVETICA_BOLD, 9, 60, 60, 60);
                if (importedFiles == null || importedFiles.isEmpty()) {
                    state.drawText("  - None", PDType1Font.HELVETICA, 8.5f, 100, 100, 100);
                } else {
                    for (String file : importedFiles) {
                        state.checkPageBreak(18);
                        state.drawText("  - " + file, PDType1Font.HELVETICA, 8.5f, 100, 100, 100);
                    }
                }

                state.currentY -= 5;
                state.drawText("Applied Filter & Exclusion Rules:", PDType1Font.HELVETICA_BOLD, 9, 60, 60, 60);
                if (activeFilters == null || activeFilters.isEmpty()) {
                    state.drawText("  - No active filters (Full migration execution)", PDType1Font.HELVETICA, 8.5f, 100, 100, 100);
                } else {
                    for (String filter : activeFilters) {
                        state.checkPageBreak(18);
                        state.drawText("  - " + filter, PDType1Font.HELVETICA, 8.5f, 100, 100, 100);
                    }
                }
                state.currentY -= 20;

                // --- FOLDER BREAKDOWN TABLE SECTOR ---
                state.checkPageBreak(100);
                state.drawText("3. DETAILED FOLDER BREAKDOWN", PDType1Font.HELVETICA_BOLD, 12, 67, 56, 202);
                state.currentY -= 8;
                
                // Draw table header
                state.drawTableRow("Mailbox Folder Path", "Total", "Success", "Skipped", "Failed", true);
                
                if (folderTelemetryMap == null || folderTelemetryMap.isEmpty()) {
                    state.drawTableRow("No folder records found.", "—", "—", "—", "—", false);
                } else {
                    for (Map.Entry<String, FolderTelemetry> entry : folderTelemetryMap.entrySet()) {
                        String pathKey = entry.getKey();
                        FolderTelemetry tel = entry.getValue();
                        state.drawTableRow(
                            pathKey, 
                            String.valueOf(tel.totalCount()), 
                            String.valueOf(tel.successCount()), 
                            String.valueOf(tel.skippedCount()), 
                            String.valueOf(tel.failedCount()), 
                            false
                        );
                    }
                }
                state.currentY -= 15;

                // --- SKIPPED BREAKDOWN SECTOR ---
                state.checkPageBreak(100);
                state.drawText("4. EXCLUSIONS & SKIPS BREAKDOWN", PDType1Font.HELVETICA_BOLD, 12, 67, 56, 202);
                state.currentY -= 5;
                if (skipReasons != null && !skipReasons.isEmpty()) {
                    for (Map.Entry<String, Integer> entry : skipReasons.entrySet()) {
                        state.checkPageBreak(20);
                        state.drawLabelValueRow("  " + entry.getKey() + ":", entry.getValue() + " items excluded", 9);
                    }
                } else {
                    state.drawText("  - No items were excluded or skipped in this migration run.", PDType1Font.HELVETICA, 8.5f, 100, 100, 100);
                }
                state.currentY -= 15;

                // --- COMPLIANCE SIGNATURE BLOCK ---
                state.checkPageBreak(120);
                state.currentY -= 10;
                state.drawText("5. COMPLIANCE & VERIFICATION VERDICT", PDType1Font.HELVETICA_BOLD, 12, 67, 56, 202);
                state.currentY -= 5;
                
                String verdict = failedCount > 0 ? 
                    "MIGRATION RUN WARNING: Process completed but encountered " + failedCount + " failed items. Check logs." :
                    "MIGRATION RUN SUCCESS: Process finished successfully with zero failures.";
                
                state.drawText(verdict, PDType1Font.HELVETICA_BOLD, 9, failedCount > 0 ? 220 : 16, failedCount > 0 ? 38 : 124, failedCount > 0 ? 38 : 65);
                state.currentY -= 20;

                state.checkPageBreak(80);
                // Signatures lines
                state.drawText("Certified By: ___________________________        Verification Date: ___________________", PDType1Font.HELVETICA, 10, 60, 60, 60);
                state.drawText("               (IT Lead / Compliance Officer)", PDType1Font.HELVETICA_OBLIQUE, 8, 120, 120, 120);
            }
            
            document.save(outputFile);
        }
    }
}
