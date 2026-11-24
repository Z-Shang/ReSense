package dev.sense.launcher;

/** Host checks for continuous, bounded, monotone release-velocity settling. */
public final class PageSettleMotionCheck {
    public static void main(String[] args){
        for(float slope:new float[]{0,.25f,1,2,3}){
            float previous=0;
            for(int i=0;i<=1000;i++){
                float value=PageSettleMotion.interpolate(i/1000f,slope);
                if(value<previous-.00001f || value<-.00001f || value>1.00001f)throw new AssertionError("Non-monotone/overshooting trajectory");
                previous=value;
            }
            if(PageSettleMotion.interpolate(0,slope)!=0 || PageSettleMotion.interpolate(1,slope)!=1)throw new AssertionError("Incorrect endpoint");
        }
        if(PageSettleMotion.slope(.5f,-2,200)!=0)throw new AssertionError("Opposing release velocity overshoots");
        if(Math.abs(PageSettleMotion.slope(.5f,2,250)-1)>0.0001f)throw new AssertionError("Release velocity not retained");
        if(PageSettleMotion.duration(.5f,10)>=PageSettleMotion.duration(.5f,1))throw new AssertionError("Fast flick did not settle sooner");
        for(float distance:new float[]{0,.01f,.5f,1,-1})for(float velocity:new float[]{-100,0,1,100}){
            long duration=PageSettleMotion.duration(distance,velocity);
            if(duration<120 || duration>420)throw new AssertionError("Unbounded duration");
        }
        System.out.println("Page settling endpoints, monotonicity, release velocity and duration checks passed");
    }
}
