package dev.sense.framework.fusion;

/** Timestamp-based playback, independent of display refresh rate. All methods use one owner thread. */
public final class FxPlayback {
    private final double first, last, framesPerSecond;
    private final boolean loop;
    private double frame;
    private long previousNanos, originNanos;
    private double originFrame;
    private boolean running, anchored;

    /** Endpoints are sample coordinates: the span from frame 0 to 29 is 29 / FPS seconds. */
    public FxPlayback(double first, double last, double framesPerSecond, boolean loop) {
        if (!Double.isFinite(first) || !Double.isFinite(last) || last < first ||
                !Double.isFinite(framesPerSecond) || framesPerSecond <= 0)
            throw new IllegalArgumentException("Invalid playback span/rate");
        this.first = first; this.last = last; this.framesPerSecond = framesPerSecond; this.loop = loop; frame = first;
    }
    public static FxPlayback marker(FxScene.Node timeline, FxScene.Marker marker, boolean loop) {
        return new FxPlayback(marker.start, marker.lastFrame(),
                timeline.scalar("FramesPerSecond", 30) * timeline.scalar("PlaybackSpeed", 1), loop);
    }
    public double frame() { return frame; }
    public boolean isRunning() { return running; }
    public void play() { if (frame >= last) frame = first; running = last > first; anchored = false; }
    public void pause() { running = false; anchored = false; }
    public void seek(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid frame");
        frame = Math.max(first, Math.min(last, value)); anchored = false;
    }
    /** Rebase after a hidden/detached view without advancing through its time off screen. */
    public void rebase() { anchored = false; }
    public double advance(long frameTimeNanos) {
        if (!running) return frame;
        if (!anchored) {
            previousNanos = originNanos = frameTimeNanos; originFrame = frame;
            anchored = true; return frame;
        }
        if (frameTimeNanos < previousNanos) return frame;
        double elapsed = ((double) frameTimeNanos - originNanos) / 1_000_000_000d;
        previousNanos = frameTimeNanos;
        frame = originFrame + elapsed * framesPerSecond;
        if (frame >= last) {
            if (loop) frame = first + (frame - first) % (last - first);
            else { frame = last; running = false; }
        }
        return frame;
    }
}
