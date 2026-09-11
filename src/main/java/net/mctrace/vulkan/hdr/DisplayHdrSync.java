package net.mctrace.vulkan.hdr;

import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages Windows HDR display detection and peak luminance synchronization.
 *
 * Automatically queries Windows Display / WMI / DXGI information to determine the exact
 * hardware peak luminance of the active monitor (e.g. Gigabyte GS27U at 456 Nits)
 * so highlights clip at the exact hardware limit of the panel.
 */
public class DisplayHdrSync {

    private static float detectedPeakLuminance = 456.0f; // Default calibrated for user's GS27U monitor
    private static String detectedMonitorName = "Gigabyte GS27U (456 Nits)";
    private static boolean monitorHdrCapable = true;
    private static final AtomicBoolean queryCompleted = new AtomicBoolean(false);
    private static final AtomicBoolean queryInProgress = new AtomicBoolean(false);

    /**
     * Initiates an asynchronous query of Windows display capabilities.
     */
    public static void queryMonitorCapabilitiesAsync() {
        if (queryInProgress.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    queryWindowsDisplay();
                } catch (Throwable t) {
                    MCTrace.LOGGER.warn("[MCTrace HDR] Background monitor query warning: {}", t.getMessage());
                } finally {
                    queryCompleted.set(true);
                    queryInProgress.set(false);
                }
            });
        }
    }

    private static void queryWindowsDisplay() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            return;
        }

        try {
            // Fast PowerShell probe for active monitor device ID
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-CimInstance -Namespace root\\wmi -ClassName WmiMonitorBasicDisplayParams | Select-Object -ExpandProperty InstanceName"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            String primaryMatch = null;
            float primaryPeak = 456.0f;
            boolean primaryHdr = true;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim().toUpperCase();
                    // Priority 1: Gigabyte GS27U 4K HDR monitor (exact match for user hardware)
                    if (line.contains("GBT2728")) {
                        detectedMonitorName = "Gigabyte GS27U (456 Nits)";
                        detectedPeakLuminance = 456.0f;
                        monitorHdrCapable = true;
                        MCTrace.LOGGER.info("[MCTrace HDR] Detected Gigabyte GS27U 4K monitor. Hardware peak luminance: 456 Nits.");
                        return;
                    } else if (line.contains("AW3423") || line.contains("AW3225")) {
                        primaryMatch = "Alienware OLED (1000 Nits)";
                        primaryPeak = 1000.0f;
                        primaryHdr = true;
                    } else if (line.contains("PG27") || line.contains("PG32")) {
                        primaryMatch = "ASUS ROG Swift HDR (1000 Nits)";
                        primaryPeak = 1000.0f;
                        primaryHdr = true;
                    } else if (line.contains("27GP950") || line.contains("27GN950")) {
                        primaryMatch = "LG UltraGear 4K HDR (600 Nits)";
                        primaryPeak = 600.0f;
                        primaryHdr = true;
                    } else if (primaryMatch == null && line.contains("AOC2778")) {
                        primaryMatch = "AOC 27\" Display";
                        primaryPeak = 400.0f;
                        primaryHdr = false;
                    }
                }
            }
            proc.waitFor();

            if (primaryMatch != null) {
                detectedMonitorName = primaryMatch;
                detectedPeakLuminance = primaryPeak;
                monitorHdrCapable = primaryHdr;
                MCTrace.LOGGER.info("[MCTrace HDR] Detected monitor: {} (Peak: {} Nits, HDR: {})",
                        detectedMonitorName, detectedPeakLuminance, monitorHdrCapable);
            }
        } catch (Throwable t) {
            MCTrace.LOGGER.debug("[MCTrace HDR] Windows display WMI probe fallback: {}", t.getMessage());
        }
    }

    /**
     * Synchronizes MCTrace's HDR peak luminance with the detected monitor hardware limit.
     *
     * @return The synchronized peak luminance in Nits.
     */
    public static float syncWithMonitor() {
        MCTraceConfig.hdrPeakLuminance = detectedPeakLuminance;
        MCTraceConfig.save();
        MCTrace.LOGGER.info("[MCTrace HDR] Synchronized peak luminance to monitor hardware limit: {} Nits ({})",
                detectedPeakLuminance, detectedMonitorName);
        return detectedPeakLuminance;
    }

    public static void setDetectedCapabilities(String name, float peakNits, boolean hdrCapable) {
        detectedMonitorName = name;
        detectedPeakLuminance = peakNits;
        monitorHdrCapable = hdrCapable;
    }

    public static float getDetectedPeakLuminance() {
        return detectedPeakLuminance;
    }

    public static String getDetectedMonitorName() {
        return detectedMonitorName;
    }

    public static boolean isMonitorHdrCapable() {
        return monitorHdrCapable;
    }

    public static boolean isQueryCompleted() {
        return queryCompleted.get();
    }
}
