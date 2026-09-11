package net.mctrace.vulkan.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a discrete rendering pass node within the MCTrace Vulkan Render Graph.
 */
public class RenderPassNode {

    private final String name;
    private final Runnable action;
    private final List<String> inputs = new ArrayList<>();
    private final List<String> outputs = new ArrayList<>();
    private boolean enabled = true;

    public RenderPassNode(String name, Runnable action) {
        this.name = name;
        this.action = action;
    }

    public RenderPassNode dependsOn(String targetName) {
        inputs.add(targetName);
        return this;
    }

    public RenderPassNode produces(String targetName) {
        outputs.add(targetName);
        return this;
    }

    public void execute() {
        if (enabled && action != null) {
            action.run();
        }
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

    public List<String> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }
}
