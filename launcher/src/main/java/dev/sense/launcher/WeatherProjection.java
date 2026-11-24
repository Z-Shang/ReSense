package dev.sense.launcher;

/** Quaternion and perspective math in the native widget's logical coordinate system. */
final class WeatherProjection {
    private WeatherProjection() { }

    static void interpolate(float[] a, float[] b, float t, float[] result) {
        float dot=0;
        for(int i=0;i<4;i++)dot+=a[i]*b[i];
        float sign=dot<0 ? -1 : 1;
        float length=0;
        for(int i=0;i<4;i++) {
            result[i]=a[i]+(sign*b[i]-a[i])*t;
            length+=result[i]*result[i];
        }
        length=(float)Math.sqrt(length);
        if(length<.00001f) {result[0]=result[1]=result[2]=0;result[3]=1;}
        else for(int i=0;i<4;i++)result[i]/=length;
    }

    static void project(float x,float y,float[] q,float anchorX,float anchorY,float depth,
                        float[] result,int offset) {
        // Rotate (x,y,0) by the normalized quaternion, preserving the resulting vertex depth.
        float tx=-2*q[2]*y,ty=2*q[2]*x,tz=2*(q[0]*y-q[1]*x);
        float rx=x+q[3]*tx+q[1]*tz-q[2]*ty;
        float ry=y+q[3]*ty+q[2]*tx-q[0]*tz;
        float rz=q[3]*tz+q[0]*ty-q[1]*tx;
        // A shared camera gives the layers consistent parallax. Positive scene Z faces the viewer.
        float focal=180f;
        float perspective=focal/Math.max(18f,focal-depth-rz);
        result[offset]=192+(anchorX+rx-192)*perspective;
        result[offset+1]=220+(anchorY+ry-220)*perspective;
    }
}
