package dev.sense.launcher;

/** Geometry checks against the recovered source hinge and wet-plane bounds. */
public final class RainCoverageTest {
    private static void close(float actual,float expected){
        if(Math.abs(actual-expected)>.002f)throw new AssertionError(actual+" != "+expected);
    }
    public static void main(String[] args){
        RainCoverage coverage=new RainCoverage();int checks=0;
        for(float[] size:new float[][]{{540,960},{540,1200},{720,1600},{2133.3333f,960}}){
            coverage.configure(540,960,753.4425f,281,-71,size[0],size[1],754.2894f);
            float px=coverage.hingeX,py=coverage.hingeY;
            for(float[] point:new float[][]{{20,20},{500,100},{270,700},{50,753.4425f},{490,753.4425f}}){
                coverage.mapAnchor(point[0],point[1],true);
                float cross=(coverage.mappedX-px)*(point[1]+71)-(coverage.mappedY-py)*(point[0]-281);
                if(Math.abs(cross)>.1f)throw new AssertionError("Droplet changed sweep angle");
                if(!Float.isFinite(coverage.mappedX)||!Float.isFinite(coverage.mappedY))throw new AssertionError("Invalid anchor");
                checks++;
            }
            coverage.mapAnchor(281,753.4425f,true);close(coverage.mappedY,size[1]);
            coverage.mapAnchor(281,960,false);close(coverage.mappedY,size[1]);
            coverage.mapAnchor(281,-71,true);close(coverage.mappedX,px);close(coverage.mappedY,py);
            coverage.mapFilm(0,0);close(coverage.mappedX,0);close(coverage.mappedY,0);
            coverage.mapFilm(540,753.4425f);close(coverage.mappedX,size[0]);close(coverage.mappedY,size[1]);
            float length=754.2894f*coverage.bladeScale;
            for(float x:new float[]{0,size[0]})if(length+.001f<Math.hypot(x-px,size[1]-py))throw new AssertionError("Blade cannot reach bottom corner");
            checks+=7;
        }
        System.out.println("PASS: "+checks+" source/tall/landscape angle, hinge, water extent and blade reach checks");
    }
}
