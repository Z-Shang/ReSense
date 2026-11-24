import dev.sense.framework.fusion.*;
import java.nio.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Host checks for actual ROM scenes, malformed binary input and display-independent playback. */
public final class FusionCoreTest {
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void near(double actual, double expected) { check(Math.abs(actual - expected) < 0.0001, actual + " != " + expected); }
    static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Accepted invalid input");
    }
    static byte[] record(String name, int kind, byte[] data) {
        ByteBuffer result = ByteBuffer.allocate(8 + ((data.length + 3) & ~3)).order(ByteOrder.LITTLE_ENDIAN);
        result.putInt((data.length << 4) | kind).putInt(Mode10.hash(name)).put(data); return result.array();
    }
    static byte[] ints(int... values) {
        ByteBuffer b = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : values) b.putInt(value); return b.array();
    }
    static byte[] join(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] p : parts) out.writeBytes(p); return out.toByteArray();
    }
    static byte[] file(byte[] payload) { return join(ints(0x6d313021, 2), record("Root", 1, payload)); }
    static byte[] property(String name, byte[] bytes) {
        return record("Property", 1, join(record("Name", 2, ints(Mode10.hash(name))), record("Value", 2, bytes)));
    }
    static byte[] floats(float... values) {
        ByteBuffer data = ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);
        for(float value:values)data.putFloat(value);return data.array();
    }
    static Mode10.Record track(byte[] payload) { return Mode10.read(file(record("Position", 1, payload))).children.get(0); }
    static void binary() throws Exception {
        check(Mode10.hash("Root") == 0x7c8bed89, "Hash");
        byte[] valid = file(record("Unknown", 2, new byte[]{1, 2, 3}));
        Mode10.Record root = Mode10.read(valid); valid[24] = 9;
        check(root.children.get(0).bytes().get() == 1, "Document must own bytes");
        check(root.children.get(0).bytes().isReadOnly(), "Mutable payload");
        for (int i = 0; i < valid.length; i++) {
            byte[] truncated = Arrays.copyOf(valid, i); rejects(() -> Mode10.read(truncated));
        }
        rejects(() -> Mode10.read(file(record("Bad", 3, new byte[0]))));
        byte[] nested = new byte[0];
        for (int i = 0; i < 132; i++) nested = record("Group", 1, nested);
        byte[] deep = file(nested); rejects(() -> Mode10.read(deep));
        byte[] invalidString = file(record("Name", 2, new byte[]{0, (byte) 0xd8}));
        rejects(() -> Mode10.read(invalidString).children.get(0).text());
        Mode10.Record streamed = Mode10.read(new ByteArrayInputStream(file(new byte[0])));
        check(streamed.children.isEmpty(), "Input stream reader");
    }
    static void tracks() {
        float[] value = new float[3];
        byte[] samples = ints(Float.floatToIntBits(10), Float.floatToIntBits(20), Float.floatToIntBits(30),
                Float.floatToIntBits(40), Float.floatToIntBits(50), Float.floatToIntBits(60));
        FxTrack dense = new FxTrack(track(join(record("Size", 2, ints(2)), record("StartFrame", 2, ints(7)),
                record("Samples", 2, samples))), 3);
        dense.sample(7.5, FxTrack.Sampling.LINEAR, value); near(value[0], 25); near(value[2], 45);
        dense.sample(7.5, FxTrack.Sampling.STEP, value); near(value[0], 10);
        dense.sample(-100, FxTrack.Sampling.LINEAR, value); near(value[0], 10);
        dense.sample(100, FxTrack.Sampling.LINEAR, value); near(value[0], 40);
        byte[] keyed = ints(10, 123, Float.floatToIntBits(2), 20, 456, Float.floatToIntBits(8));
        FxTrack keys = new FxTrack(track(join(record("Size", 2, ints(2)), record("Samples", 2, keyed))), 1);
        keys.sample(15, FxTrack.Sampling.LINEAR, value); near(value[0], 5);
        check(keys.interpolationCode(0) == 123 && keys.interpolationCode(1) == 456, "Lost interpolation codes");
        rejects(() -> keys.sample(Double.NaN, FxTrack.Sampling.LINEAR, value));
        rejects(() -> new FxTrack(track(join(record("Size", 2, ints(2)), record("Samples", 2, ints(1)))), 3));
        rejects(() -> new FxTrack(Mode10.read(file(record("Value", 2, ints(0x7fc00000)))).children.get(0), 1));
    }
    static void playback() {
        for (int hz : new int[]{60, 90, 120, 144}) {
            FxPlayback p = new FxPlayback(0, 60, 30, false); p.play(); p.advance(0);
            for (int i = 1; i <= hz; i++) p.advance(Math.round(i * 1e9 / hz));
            near(p.frame(), 30); check(p.isRunning(), "Early completion at " + hz);
            p.pause(); p.advance(8_000_000_000L); near(p.frame(), 30);
            p.play(); p.advance(9_000_000_000L); near(p.frame(), 30);
            p.advance(10_000_000_000L); near(p.frame(), 60); check(!p.isRunning(), "Missing completion");
        }
        FxPlayback p = new FxPlayback(10, 40, 30, true); p.play(); p.advance(0); p.advance(2_500_000_000L); near(p.frame(), 25);
        p.advance(1); near(p.frame(), 25); // Stale timestamps must not move backwards.
        p.rebase(); p.advance(20_000_000_000L); near(p.frame(), 25);
        p.seek(999); near(p.frame(), 40);
        FxPlayback pose = new FxPlayback(30, 30, 30, true); pose.play(); check(!pose.isRunning(), "Pose must not spin frame callbacks");
        rejects(() -> new FxPlayback(0, 1, 0, false));
    }
    static void transforms() {
        float[] parent = FxTransform.compose(new float[]{10, 20, 30}, new float[]{0, 0, 0, 2}, new float[]{2, 2, 2});
        float[] child = FxTransform.compose(new float[]{3, 4, 5}, new float[]{0, 0, 0, 1}, new float[]{1, 1, 1});
        float[] point = FxTransform.point(FxTransform.multiply(parent, child), 0, 0, 0);
        near(point[0], 16); near(point[1], 28); near(point[2], 40);
        float[] rotation = FxTransform.compose(new float[]{0, 0, 0}, new float[]{0, 0, 1, 1}, new float[]{1, 1, 1});
        point = FxTransform.point(rotation, 1, 0, 0); near(point[0], 0); near(point[1], 1);
        float[] projected = new float[3], identity = FxTransform.identity();
        FxCamera perspective = FxCamera.perspective(90, 1, 1, 100);
        check(perspective.project(identity, 0, 0, -10, 540, 960, projected), "Visible point clipped");
        near(projected[0], 270); near(projected[1], 480);
        check(!perspective.project(identity, 0, 0, 10, 540, 960, projected), "Behind-camera point visible");
        check(!perspective.project(identity, 0, 0, -.5f, 540, 960, projected), "Near clipping");
        FxCamera ortho = FxCamera.orthographic(0, 540, 960, 0, 1, 100);
        check(ortho.project(identity, 100, 200, -10, 540, 960, projected), "Orthographic point clipped");
        near(projected[0], 100); near(projected[1], 200);
        rejects(() -> FxCamera.perspective(90, 0, 1, 100));
        rejects(() -> FxTransform.compose(new float[3], new float[4], new float[]{1, 1, 1}));
    }
    static void evaluation() {
        byte[] sibling=record("Node",1,join(property("Layer",ints(3)),property("Position",floats(2,0,0))));
        byte[] child=record("Node",1,join(property("Layer",ints(2)),property("LayerParent",ints(3)),
                property("Position",floats(3,0,0)),property("Opacity",floats(.4f)),property("InFrame",floats(10)),property("Duration",floats(20))));
        FxScene scene=new FxScene(Mode10.read(file(record("Node",1,join(property("Position",floats(10,0,0)),
                property("Opacity",floats(.5f)),sibling,child)))));
        FxSceneInstance instance=new FxSceneInstance(scene);
        FxSceneInstance.State state=instance.state(scene.nodes.get(2));
        instance.evaluate(5);near(state.opacity(),0);
        instance.evaluate(15);near(state.opacity(),.2);near(state.world()[12],15);near(state.localFrame(),5);
        instance.setTimelineFrame(scene.nodes.get(2), 7d);instance.evaluate(99);near(state.localFrame(),7);near(state.opacity(),.2);
        instance.setTimelineFrame(scene.nodes.get(2),null);instance.evaluate(99);near(state.opacity(),0);
        instance.setChildFrame(scene.nodes.get(0),25d);instance.evaluate(1);near(state.localFrame(),15);near(state.world()[12],15);near(state.opacity(),.2);
        instance.setChildFrame(scene.nodes.get(0),null);instance.evaluate(15);
        instance.setVisible(scene.nodes.get(2),false);instance.evaluate(15);near(state.opacity(),0);
        FxSceneInstance independent=new FxSceneInstance(scene);independent.evaluate(15);near(independent.state(scene.nodes.get(2)).opacity(),.2);
        instance.setVisible(scene.nodes.get(2),null);instance.evaluate(15);near(state.opacity(),.2);
        byte[] a=record("Node",1,join(property("Layer",ints(0)),property("LayerParent",ints(1))));
        byte[] b=record("Node",1,join(property("Layer",ints(1)),property("LayerParent",ints(0))));
        rejects(()->new FxSceneInstance(new FxScene(Mode10.read(file(join(a,b))))));
        byte[] samples=join(ints(0,1),floats(0,0,0,1),ints(10,2),floats(0,0,1,0));
        FxTrack rotation=new FxTrack(track(join(record("Size",2,ints(2)),record("Samples",2,samples))),4);
        float[] q=new float[4];rotation.sampleQuaternion(5,q);
        float[] point=FxTransform.point(FxTransform.compose(new float[3],q,new float[]{1,1,1}),1,0,0);
        near(point[0],0);near(point[1],1);
    }
    public static void main(String[] args) throws Exception {
        binary(); tracks(); playback(); transforms(); evaluation();
        int documents = 0, nodes = 0, markers = 0;
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] fields = line.split("\\t");
            FxScene scene = new FxScene(Mode10.read(Files.readAllBytes(Path.of(fields[0]))));
            FxSceneInstance instance = new FxSceneInstance(scene);
            new FxResources(scene.document, null, 1024);
            for (double frame : new double[]{0, 15.5, 30, 60, 120}) instance.evaluate(frame);
            check(scene.nodes.size() == Integer.parseInt(fields[1]), "Node count: " + fields[0]);
            for (FxScene.Node node : scene.nodes) {
                nodes++;
                for (FxScene.Node child : node.children()) check(child.parent == node, "Broken hierarchy");
                for (FxScene.Marker marker : node.markers()) { markers++; check(marker.lastFrame() >= marker.start, "Marker span"); }
            }
            documents++;
        }
        System.out.println("PASS binary bounds, immutable records, tracks, transforms, cameras and playback at 60/90/120/144 Hz");
        System.out.println("PASS ROM corpus: " + documents + " scenes, " + nodes + " nodes, " + markers + " markers");
    }
}
