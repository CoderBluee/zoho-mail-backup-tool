package com.pstconverter.controller.conversion;

import com.pstconverter.core.destination.EmailDestinationConfig;
import java.util.Properties;

/**
 * Immutable snapshot of all conversion settings, captured once from Step2/3/4
 * on the FX thread before the background task starts.
 * <p>
 * Eliminates repeated {@code controller.getStep4DestinationView()} calls from
 * worker threads, preventing both thread-safety issues and verbose code.
 */
public record ConversionConfig(
    String format,
    boolean isCloud,
    String exportStructure,
    String attachmentHandling,
    String namingConvention,
    String resolvedOutputPath,
    EmailDestinationConfig cloudConfig,
    boolean isMonolithic,
    boolean isResume,
    int threadCount,
    Properties filterProps,
    boolean isSplitPst,
    String splitSize
) {

    /**
     * Convenience check for monolithic export structures.
     */
    public static boolean checkMonolithic(String exportStructure) {
        return exportStructure != null && (
            exportStructure.equalsIgnoreCase("Single Monolithic MBOX for Entire Migration") ||
            exportStructure.equalsIgnoreCase("Single Monolithic Archive File (Entire Migration - Default)")
        );
    }
}
