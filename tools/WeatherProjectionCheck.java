package dev.sense.launcher;

/** Run with javac/java; checks projection invariants without an Android runtime. */
public final class WeatherProjectionCheck {
    private static void near(float actual,float expected) {
        if(Math.abs(actual-expected)>.001f)throw new AssertionError(actual+" != "+expected);
    }
    public static void main(String[] args) {
        float[] identity={0,0,0,1},out=new float[2],q=new float[4];
        WeatherProjection.project(10,20,identity,192,220,0,out,0);
        near(out[0],202);near(out[1],240);
        WeatherProjection.project(10,20,identity,192,220,30,out,0);
        if(out[0]<=202 || out[1]<=240)throw new AssertionError("Nearer layer must grow");
        float half=(float)Math.sqrt(.5);
        float[] yaw90={0,half,0,half};
        WeatherProjection.project(20,0,yaw90,192,220,0,out,0);
        near(out[0],192);near(out[1],220);
        float[] roll90={0,0,half,half};
        WeatherProjection.project(10,0,roll90,192,220,0,out,0);
        near(out[0],192);near(out[1],230);
        float[] negative={0,0,-half,-half};
        WeatherProjection.interpolate(roll90,negative,.5f,q);
        WeatherProjection.project(10,0,q,192,220,0,out,0);
        near(out[0],192);near(out[1],230);
        WeatherProjection.interpolate(identity,roll90,.5f,q);
        WeatherProjection.project(10,0,q,192,220,0,out,0);
        near(out[0],192+10*half);near(out[1],220+10*half);
        WeatherProjection.project(10,20,identity,192,220,180,out,0);
        if(!Float.isFinite(out[0])||!Float.isFinite(out[1]))throw new AssertionError("Camera plane singularity");
        System.out.println("Weather projection: identity, yaw, roll, quaternion sign, interpolation, depth and camera guard passed");
    }
}
