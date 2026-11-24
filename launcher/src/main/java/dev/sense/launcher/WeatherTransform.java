package dev.sense.launcher;

/** Column-major transforms matching the exported position/quaternion/scale/pivot channels. */
final class WeatherTransform {
    static void compose(float[] p,float[] q,float[] s,float[] pivot,float[] m) {
        float x=q[0],y=q[1],z=q[2],w=q[3];
        m[0]=(1-2*y*y-2*z*z)*s[0];m[1]=2*(x*y+z*w)*s[0];m[2]=2*(x*z-y*w)*s[0];m[3]=0;
        m[4]=2*(x*y-z*w)*s[1];m[5]=(1-2*x*x-2*z*z)*s[1];m[6]=2*(y*z+x*w)*s[1];m[7]=0;
        m[8]=2*(x*z+y*w)*s[2];m[9]=2*(y*z-x*w)*s[2];m[10]=(1-2*x*x-2*y*y)*s[2];m[11]=0;
        m[12]=p[0]+m[0]*pivot[0]+m[4]*pivot[1]+m[8]*pivot[2];
        m[13]=p[1]+m[1]*pivot[0]+m[5]*pivot[1]+m[9]*pivot[2];
        m[14]=p[2]+m[2]*pivot[0]+m[6]*pivot[1]+m[10]*pivot[2];m[15]=1;
    }
    static void multiply(float[] a,float[] b,float[] out) {
        for(int col=0;col<4;col++)for(int row=0;row<4;row++) {
            float value=0;for(int k=0;k<4;k++)value+=a[k*4+row]*b[col*4+k];
            out[col*4+row]=value;
        }
    }
    private WeatherTransform() { }
}
