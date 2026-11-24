package dev.sense.framework.fusion;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Owner-thread mutable scene evaluation. Documents and resource scopes may be shared. */
public final class FxSceneInstance {
    public final FxScene scene;
    private final Map<FxScene.Node, State> states = new IdentityHashMap<>();
    private final List<State> drawOrder = new ArrayList<>();
    private final List<State> ordered;
    public FxSceneInstance(FxScene scene) {
        this.scene = scene;
        for (FxScene.Node node : scene.nodes) states.put(node, new State(node));
        for (FxScene.Node node : scene.nodes) {
            State state = states.get(node); state.parent = states.get(node.parent);
            int layerParent = integer(node, "LayerParent", -1);
            if (layerParent != -1) {
                for (FxScene.Node sibling : scene.nodes) {
                    if (sibling.parent == node.parent && integer(sibling, "Layer", -1) == layerParent) {
                        if (state.link != null || sibling == node) throw Mode10.invalid("Ambiguous layer parent");
                        state.link = states.get(sibling);
                    }
                }
                if (state.link == null) throw Mode10.invalid("Missing layer parent");
            }
        }
        append(scene.roots);
        ordered = List.copyOf(drawOrder);
        evaluate(0); // Validate cycles and encodings before accepting the instance.
    }
    private void append(List<FxScene.Node> nodes) {
        List<FxScene.Node> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparingInt((FxScene.Node n) -> integer(n, "Layer", -1)).reversed());
        for (FxScene.Node node : sorted) { drawOrder.add(states.get(node)); append(node.children()); }
    }
    public State state(FxScene.Node node) {
        State state = states.get(node);
        if (state == null) throw new IllegalArgumentException("Node belongs to another scene");
        return state;
    }
    public List<State> drawOrder() { return ordered; }
    /** Explicit independent clock for a nested timeline; clear it to inherit its parent's clock again. */
    public void setTimelineFrame(FxScene.Node node, Double frame) {
        if (frame != null && !Double.isFinite(frame)) throw new IllegalArgumentException("Timeline frame");
        state(node).frameOverride = frame;
    }
    /** Independent child timeline clock, without changing this node's own pose/opacity clock. */
    public void setChildFrame(FxScene.Node node, Double frame) {
        if (frame != null && !Double.isFinite(frame)) throw new IllegalArgumentException("Child timeline frame");
        state(node).childFrameOverride = frame;
    }
    public void setVisible(FxScene.Node node, Boolean visible) { state(node).visibilityOverride = visible; }
    public void evaluate(double frame) {
        if (!Double.isFinite(frame)) throw new IllegalArgumentException("Invalid frame");
        for (State state : states.values()) { state.timeVisit = state.chainVisit = state.worldVisit = 0; }
        for (State state : drawOrder) { time(state, frame); world(state); }
    }
    private void time(State state, double frame) {
        if (state.timeVisit == 2) return;
        if (state.timeVisit == 1) throw Mode10.invalid("Timing cycle");
        state.timeVisit = 1;
        if (state.parent != null) {
            time(state.parent, frame);
            frame = state.parent.childFrameOverride != null ? state.parent.childFrameOverride : state.parent.localFrame + state.parent.childStart;
        }
        state.localFrame = state.frameOverride != null ? state.frameOverride : frame - Math.max(0, state.inFrame);
        state.active = state.localFrame >= 0 && (state.duration < 0 || state.localFrame < state.duration);
        state.timeVisit = 2;
    }
    private void chain(State state) {
        if (state.chainVisit == 2) return;
        if (state.chainVisit == 1) throw Mode10.invalid("Layer cycle");
        state.chainVisit = 1;
        float[] p = state.p, q = state.q, s = state.s, pivot = state.pivotValue;
        state.position.sample(state.localFrame, FxTrack.Sampling.LINEAR, p);
        state.rotation.sampleQuaternion(state.localFrame, q);
        state.scale.sample(state.localFrame, FxTrack.Sampling.LINEAR, s);
        state.pivot.sample(state.localFrame, FxTrack.Sampling.LINEAR, pivot);
        float[] local = state.local;
        FxTransform.composeInto(p, q, s, local);
        // Observed weather adaptation: pivot is an additional local-space displacement.
        for (int i = 0; i < 3; i++) local[12+i] += local[i]*pivot[0] + local[4+i]*pivot[1] + local[8+i]*pivot[2];
        if (state.link != null) { chain(state.link); FxTransform.multiplyInto(state.link.chain, local, state.chain); }
        else System.arraycopy(local,0,state.chain,0,16);
        state.chainVisit = 2;
    }
    private void world(State state) {
        if (state.worldVisit == 2) return;
        if (state.worldVisit == 1) throw Mode10.invalid("Scene cycle");
        state.worldVisit = 1; chain(state);
        float[] opacity = state.opacityValue; state.opacity.sample(state.localFrame, FxTrack.Sampling.LINEAR, opacity);
        state.color.sample(state.localFrame, FxTrack.Sampling.LINEAR, state.tint);
        for (int i=0;i<4;i++) state.tint[i]=Math.max(0,Math.min(1,state.tint[i]));
        state.alpha = (state.visibilityOverride == null ? state.visible : state.visibilityOverride) && state.active ? Math.max(0, Math.min(1, opacity[0])) : 0;
        if (state.parent != null) {
            world(state.parent); FxTransform.multiplyInto(state.parent.world, state.chain, state.world); state.alpha *= state.parent.alpha;
        } else System.arraycopy(state.chain,0,state.world,0,16);
        state.worldVisit = 2;
    }
    private static int integer(FxScene.Node node, String name, int fallback) {
        Mode10.Record value = node.property(name); return value == null ? fallback : value.integer();
    }
    private static FxTrack track(FxScene.Node node, String name, float... fallback) {
        Mode10.Record value = node.property(name); return value == null ? new FxTrack(fallback) : new FxTrack(value, fallback.length);
    }
    private static void requireConstant(FxScene.Node node, String name, float... expected) {
        FxTrack track = track(node, name, expected); float[] actual = new float[expected.length];
        if (track.sampleCount != 1) throw new UnsupportedOperationException("Animated " + name);
        track.sample(0, FxTrack.Sampling.STEP, actual);
        for (int i = 0; i < actual.length; i++) if (Math.abs(actual[i] - expected[i]) > 1e-6)
            throw new UnsupportedOperationException("Unverified non-default " + name);
    }
    public static final class State {
        public final FxScene.Node node;
        public final int textureId, blendCode;
        private final float[] vertices;
        private final FxTrack position, rotation, scale, pivot, opacity, color;
        private final boolean usesTexture;
        private final double inFrame, duration, childStart;
        private final boolean visible;
        private State parent, link;
        private final float[] local = FxTransform.identity(), chain = FxTransform.identity(), world = FxTransform.identity();
        private final float[] p = new float[3], q = new float[4], s = new float[3], pivotValue = new float[3], opacityValue = new float[1];
        private final float[] tint = new float[4];
        private float alpha;
        private double localFrame;
        private Double frameOverride;
        private Double childFrameOverride;
        private Boolean visibilityOverride;
        private boolean active;
        private byte timeVisit, chainVisit, worldVisit;
        private State(FxScene.Node node) {
            this.node = node;
            position = track(node, "Position", 0,0,0); rotation = track(node, "Rotation", 0,0,0,1);
            scale = track(node, "Scale", 1,1,1); pivot = track(node, "PivotPosition", 0,0,0); opacity = track(node, "Opacity", 1);
            color = track(node, "DiffuseColor", 1,1,1,1); usesTexture = node.property("DiffuseMap") != null;
            requireConstant(node, "PivotRotation", 0,0,0,1); requireConstant(node, "PivotScale", 1,1,1);
            inFrame = node.scalar("InFrame", -1); duration = node.scalar("Duration", -1); childStart = node.scalar("TimelineInFrame", 0);
            Mode10.Record visibility = node.property("Visible");
            if (visibility != null && (visibility.kind != 2 || visibility.size() != 1)) throw Mode10.invalid("Visibility encoding");
            visible = visibility == null || visibility.bytes().get() != 0;
            textureId = integer(node, "DiffuseMap", 0); blendCode = integer(node, "BlendMode", -1);
            Mode10.Record mesh = node.property("Vertices");
            if (mesh == null) vertices = new float[0];
            else {
                if (mesh.kind != 2 || mesh.size() != 80) throw new UnsupportedOperationException("Only observed Mode10 quads supported");
                vertices = new float[20]; ByteBuffer data = mesh.bytes();
                for (int i = 0; i < 20; i++) { vertices[i] = data.getFloat(); if (!Float.isFinite(vertices[i])) throw Mode10.invalid("Non-finite mesh"); }
            }
        }
        public float[] world() { return world.clone(); }
        float[] worldData() { return world; }
        float[] meshData() { return vertices; }
        float[] tintData() { return tint; }
        public boolean usesTexture() { return usesTexture; }
        public float[] vertices() { return vertices.clone(); }
        public boolean hasMesh() { return vertices.length > 0; }
        public float opacity() { return alpha; }
        public double localFrame() { return localFrame; }
    }
}
