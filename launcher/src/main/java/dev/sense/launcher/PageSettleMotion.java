package dev.sense.launcher;

/** Monotone cubic settling: release velocity at the start, zero velocity at rest. */
final class PageSettleMotion {
    private PageSettleMotion(){}
    static long duration(float distance,float velocity){
        return Math.max(120,Math.min(420,Math.round(3000*Math.abs(distance)/Math.max(2.5f,Math.abs(velocity)))));
    }
    static float slope(float distance,float velocity,long duration){
        if(Math.abs(distance)<.00001f)return 0;
        return Math.max(0,Math.min(3,velocity*(duration/1000f)/distance));
    }
    static float interpolate(float t,float slope){
        return (-2*t*t*t+3*t*t)+(t*t*t-2*t*t+t)*slope;
    }
}
