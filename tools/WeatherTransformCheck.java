package dev.sense.launcher;

/** Verify sibling parenting and pivot composition rather than independent sprite placement. */
public final class WeatherTransformCheck {
    static void near(float actual,float expected){if(Math.abs(actual-expected)>.001f)throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        float[] identity={0,0,0,1},unit={1,1,1},zero={0,0,0};
        float[] parent=new float[16],child=new float[16],world=new float[16];
        WeatherTransform.compose(new float[]{240,240,0},identity,unit,new float[]{-240,-240,0},parent);
        near(parent[12],0);near(parent[13],0);
        WeatherTransform.compose(new float[]{240,240,-50},identity,unit,zero,parent);
        WeatherTransform.compose(new float[]{2,-38,0},identity,unit,new float[]{-128,-127,0},child);
        WeatherTransform.multiply(parent,child,world);
        near(world[12],114);near(world[13],75);near(world[14],-50);
        near(world[12]+world[0]*128+world[4]*127,242);
        near(world[13]+world[1]*128+world[5]*127,202);
        float half=(float)Math.sqrt(.5);
        WeatherTransform.compose(zero,new float[]{0,0,half,half},unit,zero,parent);
        WeatherTransform.compose(new float[]{10,0,0},identity,unit,zero,child);
        WeatherTransform.multiply(parent,child,world);near(world[12],0);near(world[13],10);
        System.out.println("Weather transform checks: neutral timeline pivot, layer-parent translation/depth, sprite anchor and inherited rotation passed");
    }
}
