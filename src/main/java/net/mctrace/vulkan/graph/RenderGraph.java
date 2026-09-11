package net.mctrace.vulkan.graph;

import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import net.mctrace.render.gbuffer.GBufferManager;
import net.mctrace.render.gbuffer.VelocityPass;
import net.mctrace.vulkan.VulkanCapabilities;
import net.mctrace.vulkan.rt.CompositePipeline;
import net.mctrace.vulkan.rt.DenoiserPipeline;
import net.mctrace.vulkan.rt.FsrPipeline;
import net.mctrace.vulkan.rt.RayTracingPipeline;
import net.mctrace.vulkan.rt.TlasManager;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector3f;

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
    private static CameraRenderState currentCameraState = null;
    private static int frameCounter = 0;

    public static void initialize() {
        if (initialized) {
            return;
        }

        PASSES.clear();

        // Initialize compute pipelines
        CompositePipeline.initialize();
        RayTracingPipeline.initialize();
        DenoiserPipeline.initialize();
        FsrPipeline.initialize();
        VelocityPass.initialize();

        // Pass 1: G-Buffer Allocation & Verification
        RenderPassNode gbufferPass = new RenderPassNode("gbuffer_pass", () -> {
            // G-Buffer attachments verified
        }).produces("hdr_color")
          .produces("normals")
          .produces("depth")
          .produces("pbr_material");

        // Pass 2: BVH Acceleration Structure Build (BLAS & TLAS)
        RenderPassNode bvhPass = new RenderPassNode("bvh_build_pass", () -> {
            TlasManager.buildTlas(VulkanCapabilities.getActiveDevice(), currentCameraState);
        }).dependsOn("chunk_meshes")
          .produces("tlas");

        // Pass 3: Screen-Space Velocity Buffer Generation (Camera & Dynamic Entities)
        RenderPassNode velocityPass = new RenderPassNode("velocity_pass", () -> {
            int rW = GBufferManager.getRenderWidth();
            int rH = GBufferManager.getRenderHeight();
            int dW = GBufferManager.getNativeWidth();
            int dH = GBufferManager.getNativeHeight();
            VelocityPass.dispatch(rW, rH, dW, dH);
        }).dependsOn("depth")
          .produces("velocity");

        // Pass 4: Hardware Ray Query (Direct Shadows + AO via rayQueryEXT across full 3D TLAS)
        RenderPassNode rayQueryPass = new RenderPassNode("rayquery_pass", () -> {
            Vector3f sunDir = new Vector3f(0.5f, 0.8f, 0.3f).normalize();
            Vector3f sunColor = new Vector3f(1.0f, 0.95f, 0.85f);
            RayTracingPipeline.prepareDispatch(currentCameraState, sunDir, sunColor, frameCounter);
        }).dependsOn("depth")
          .dependsOn("normals")
          .dependsOn("tlas")
          .produces("raw_illumination");

        // Pass 5: SVGF Denoising (Temporal + Spatial A-Trous Wavelet Filtering)
        RenderPassNode denoiserPass = new RenderPassNode("svgf_denoiser_pass", () -> {
            DenoiserPipeline.advanceFrame();
        }).dependsOn("raw_illumination")
          .dependsOn("velocity")
          .dependsOn("depth")
          .dependsOn("normals")
          .produces("denoised_illumination");

        // Pass 6: AMD FSR Upscaler (Temporal Reconstruction + RCAS)
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

        // Pass 7: Final Deferred Composite & Swapchain Presentation
        RenderPassNode compositePass = new RenderPassNode("composite_pass", () -> {
            int w = GBufferManager.getNativeWidth();
            int h = GBufferManager.getNativeHeight();
            Vector3f sunDir = new Vector3f(0.5f, 0.8f, 0.3f).normalize();
            CompositePipeline.dispatch(w, h, sunDir, 0.5f);
        }).dependsOn("fsr_upscaled");

        PASSES.add(gbufferPass);
        PASSES.add(bvhPass);
        PASSES.add(velocityPass);
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

        currentCameraState = cameraState;
        frameCounter++;

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
        boolean velocityEnabled = fsrEnabled || denoiserEnabled;

        for (RenderPassNode pass : PASSES) {
            switch (pass.getName()) {
                case "bvh_build_pass" -> pass.setEnabled(rtEnabled);
                case "velocity_pass" -> pass.setEnabled(velocityEnabled);
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
