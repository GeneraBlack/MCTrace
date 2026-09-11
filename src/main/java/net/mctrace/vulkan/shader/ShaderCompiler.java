package net.mctrace.vulkan.shader;

import net.mctrace.MCTrace;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Compiles GLSL compute shaders to Vulkan SPIR-V bytecode in-memory
 * using LWJGL's bundled Shaderc native library.
 */
public class ShaderCompiler {

    /**
     * Compiles a GLSL compute shader source string into SPIR-V bytecode.
     *
     * @param shaderName Debug name of the shader.
     * @param glslSource The GLSL source code.
     * @return ByteBuffer containing SPIR-V bytecode, or null on error.
     */
    public static ByteBuffer compileComputeShader(String shaderName, String glslSource) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        if (compiler == MemoryUtil.NULL) {
            MCTrace.LOGGER.error("[MCTrace Shaderc] Failed to initialize shaderc compiler.");
            return null;
        }

        long options = Shaderc.shaderc_compile_options_initialize();
        if (options != MemoryUtil.NULL) {
            Shaderc.shaderc_compile_options_set_target_env(
                    options,
                    Shaderc.shaderc_target_env_vulkan,
                    Shaderc.shaderc_env_version_vulkan_1_2
            );
            Shaderc.shaderc_compile_options_set_optimization_level(
                    options,
                    Shaderc.shaderc_optimization_level_performance
            );
        }

        try {
            long result = Shaderc.shaderc_compile_into_spv(
                    compiler,
                    glslSource,
                    Shaderc.shaderc_compute_shader,
                    shaderName,
                    "main",
                    options
            );

            if (result == MemoryUtil.NULL) {
                MCTrace.LOGGER.error("[MCTrace Shaderc] Null compilation result for: {}", shaderName);
                return null;
            }

            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if (status != Shaderc.shaderc_compilation_status_success) {
                String error = Shaderc.shaderc_result_get_error_message(result);
                MCTrace.LOGGER.error("[MCTrace Shaderc] Compilation error in {}:\n{}", shaderName, error);
                Shaderc.shaderc_result_release(result);
                return null;
            }

            ByteBuffer spvBytes = Shaderc.shaderc_result_get_bytes(result);
            ByteBuffer copy = MemoryUtil.memAlloc(spvBytes.remaining());
            copy.put(spvBytes);
            copy.flip();

            Shaderc.shaderc_result_release(result);
            MCTrace.LOGGER.info("[MCTrace Shaderc] Successfully compiled {} to SPIR-V ({} bytes)", shaderName, copy.remaining());
            return copy;
        } finally {
            if (options != MemoryUtil.NULL) {
                Shaderc.shaderc_compile_options_release(options);
            }
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    /**
     * Loads a shader file from the classpath resources and compiles it.
     */
    public static ByteBuffer loadAndCompile(String resourcePath) {
        try (InputStream is = ShaderCompiler.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                MCTrace.LOGGER.error("[MCTrace Shaderc] Resource not found: {}", resourcePath);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String source = reader.lines().collect(Collectors.joining("\n"));
                return compileComputeShader(resourcePath, source);
            }
        } catch (Exception e) {
            MCTrace.LOGGER.error("[MCTrace Shaderc] Exception reading shader {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }
}
