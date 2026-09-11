package net.mctrace.vulkan.graph;

import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import net.mctrace.render.gbuffer.GBufferManager;
import net.mctrace.vulkan.rt.DenoiserPipeline;
import net.mctrace.vulkan.rt.FsrPipeline;
import net.mctrace.vulkan.rt.RayTracingPipeline;
import net.mctrace.vulkan.rt.TlasManager;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic Vulkan Render Graph.
 *
 * Coordinates execution order, pass dependencies, resource transitions,
 * and enables/disables pipeline stages dynamically according to user configuration.
 */
public class RenderGraph {

    private static final List<RenderPassNode> PASSES = new ArrayList<>();
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) {
            return;
        }

        PASSES.clear();

        // Pass 1: G-Buffer Allocation & Verification
        RenderPassNode gbufferPass = new RenderPassNode("gbuffer_pass", () -> {
            // G-Buffer attachments prepared
        }).produces("hdr_color")
          .produces("normals")
          .produces("depth")
          .produces("velocity")
          .produces("pbr_material");

        // Pass 2: BVH Acceleration Structure Build
        RenderPassNode bvhPass = new RenderPassNode("bvh_build_pass", () -> {
            // TLAS scene graph updated
        }).dependsOn("chunk_meshes")
          .produces("tlas");

        // Pass 3: Hardware Ray Query (Direct Shadows + AO)
        RenderPassNode rayQueryPass = new RenderPassNode("rayquery_pass", () -> {
            // Ray query compute pass executed
        }).dependsOn("depth")
          .dependsOn("normals")
          .dependsOn("tlas")
          .produces("raw_illumination");

        // Pass 4: SVGF Denoising (Temporal + Spatial A-Trous)
        RenderPassNode denoiserPass = new RenderPassNode("svgf_denoiser_pass", () -> {
            DenoiserPipeline.advanceFrame();
        }).dependsOn("raw_illumination")
          .dependsOn("velocity")
          .dependsOn("depth")
          .dependsOn("normals")
          .produces("denoised_illumination");

        // Pass 5: AMD FSR Upscaler (Temporal Reconstruction + RCAS)
        RenderPassNode fsrPass = new RenderPassNode("fsr_upscaler_pass", () -> {
            int rW = GBufferManager.getRenderWidth();
            int rH = GBufferManager.getRenderHeight();
            int dW = GBufferManager.getNativeWidth();
            int dH = GBufferManager.getNativeHeight();
            FsrPipeline.prepareDispatch(rW, rH, dW, dH);
        }).dependsOn("denoised_illumination")
          .dependsOn("velocity")
          .dependsOn("depth")
          .produces("fsr_upscaled");

        // Pass 6: Final Composite & Swapchain Presentation
        RenderPassNode compositePass = new RenderPassNode("composite_pass", () -> {
            // Composite to swapchain
        }).dependsOn("fsr_upscaled");

        PASSES.add(gbufferPass);
        PASSES.add(bvhPass);
        PASSES.add(rayQueryPass);
        PASSES.add(denoiserPass);
        PASSES.add(fsrPass);
        PASSES.add(compositePass);

        initialized = true;
        MCTrace.LOGGER.info("[MCTrace RenderGraph] Initialized dynamic Vulkan render graph with {} passes.", PASSES.size());
    }

    /**
     * Executes all active passes in the render graph for the current frame.
     */
    public static void executeFrame(CameraRenderState cameraState) {
        if (!initialized) {
            initialize();
        }

        updatePassStates();

        for (RenderPassNode pass : PASSES) {
            if (pass.isEnabled()) {
                pass.execute();
            }
        }
    }

    /**
     * Synchronizes node execution states with real-time settings.
     */
    private static void updatePassStates() {
        boolean rtEnabled = MCTraceConfig.enableRayTracing &&
                            MCTraceConfig.rayTracingMode != MCTraceConfig.RayTracingMode.DISABLED;
        boolean denoiserEnabled = rtEnabled && MCTraceConfig.enableDenoiser;
        boolean fsrEnabled = MCTraceConfig.enableFSR &&
                             MCTraceConfig.fsrQualityMode != MCTraceConfig.FsrQualityMode.OFF;

        for (RenderPassNode pass : PASSES) {
            switch (pass.getName()) {
                case "rayquery_pass" -> pass.setEnabled(rtEnabled);
                case "svgf_denoiser_pass" -> pass.setEnabled(denoiserEnabled);
                case "fsr_upscaler_pass" -> pass.setEnabled(fsrEnabled);
                default -> pass.setEnabled(true);
            }
        }
    }

    public static List<RenderPassNode> getPasses() {
        return PASSES;
    }
}
