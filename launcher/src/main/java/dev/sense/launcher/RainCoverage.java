package dev.sense.launcher;

/** Viewport adaptation in source units. Keeps drop size and angle to the wiper hinge. */
final class RainCoverage {
    float hingeX,hingeY,bladeScale;
    float mappedX,mappedY;
    private float sourceWidth,sourceHeight,wetBottom,sourceHingeX,sourceHingeY,width,height;

    void configure(float sourceWidth,float sourceHeight,float wetBottom,float sourceHingeX,float sourceHingeY,
                   float width,float height,float bladeLength) {
        this.sourceWidth=sourceWidth;this.sourceHeight=sourceHeight;this.wetBottom=wetBottom;
        this.sourceHingeX=sourceHingeX;this.sourceHingeY=sourceHingeY;this.width=width;this.height=height;
        hingeX=width/2+sourceHingeX-sourceWidth/2;hingeY=sourceHingeY;
        float reach=(float)Math.hypot(Math.max(Math.abs(hingeX),Math.abs(width-hingeX)),height-hingeY);
        bladeScale=Math.max(1,reach/Math.max(1,bladeLength));
    }

    void mapAnchor(float x,float y,boolean wet) {
        float dx=x-sourceHingeX,dy=y-sourceHingeY;
        float original=exit(dx,dy,sourceHingeX,sourceHingeY,sourceWidth,wet?wetBottom:sourceHeight);
        float target=exit(dx,dy,hingeX,hingeY,width,height);
        float ratio=Float.isFinite(original)&&Float.isFinite(target)&&original>0?target/original:1;
        mappedX=hingeX+dx*ratio;mappedY=hingeY+dy*ratio;
    }

    void mapFilm(float x,float y) {
        mappedX=x*width/sourceWidth;mappedY=y*height/Math.max(1,wetBottom);
    }

    private static float exit(float dx,float dy,float px,float py,float width,float height) {
        // The hinge is above the glass. Use the far edge of each ray, allowing
        // offscreen particle anchors to keep their original entrance/exit phase.
        float edge=Float.POSITIVE_INFINITY;
        if(dx>0)edge=(width-px)/dx;else if(dx<0)edge=-px/dx;
        if(dy>0)edge=Math.min(edge,(height-py)/dy);
        return edge>0?edge:Float.POSITIVE_INFINITY;
    }
}
