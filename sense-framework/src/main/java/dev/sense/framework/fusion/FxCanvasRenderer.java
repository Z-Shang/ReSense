package dev.sense.framework.fusion;

import android.graphics.*;
import java.util.HashMap;
import java.util.Map;

/** View-composited quad backend for launchers: participates in clipping, page transforms and snapshots.
 * Prepared off-thread. Near-plane-crossing meshes require the GLES backend instead.
 */
public final class FxCanvasRenderer {
    public interface NodePainter { void paint(Canvas localCanvas, FxSceneInstance.State state); }
    private final Map<Integer,Bitmap> textures;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Matrix mapping = new Matrix(), local = new Matrix();
    private final float[] source = new float[8], destination = new float[8];
    public FxCanvasRenderer(Map<Integer,FxTexture> images) {
        Map<Integer,Bitmap> decoded = new HashMap<>();
        for (var entry:images.entrySet()) {
            FxTexture image=entry.getValue();var bytes=image.pixels();int[] pixels=new int[image.width*image.height];
            for(int i=0;i<pixels.length;i++) {int r=bytes.get()&255,g=bytes.get()&255,b=bytes.get()&255,a=bytes.get()&255;pixels[i]=Color.argb(a,r,g,b);}
            decoded.put(entry.getKey(),Bitmap.createBitmap(pixels,image.width,image.height,Bitmap.Config.ARGB_8888));
        }
        textures=Map.copyOf(decoded);
    }
    /** Bind a non-mesh image control in its local rectangle. Insets are an explicit host policy. */
    public void image(Canvas canvas,int textureId,float width,float height,float alpha,float capX,float capY) {
        Bitmap image=textures.get(textureId);if(image==null)throw new IllegalArgumentException("Unprepared image control");
        paint.setAlpha(Math.round(Math.max(0,Math.min(1,alpha))*255));paint.setColorFilter(null);
        if(capX<=0 || capY<=0){canvas.drawBitmap(image,null,new RectF(0,0,width,height),paint);return;}
        float sx=Math.min(12,image.getWidth()/3f),sy=Math.min(12,image.getHeight()/3f);
        float dx=Math.min(capX,width/2),dy=Math.min(capY,height/2);
        float[] xs={0,sx,image.getWidth()-sx,image.getWidth()},ys={0,sy,image.getHeight()-sy,image.getHeight()};
        float[] xd={0,dx,width-dx,width},yd={0,dy,height-dy,height};
        for(int y=0;y<3;y++)for(int x=0;x<3;x++)canvas.drawBitmap(image,new Rect((int)xs[x],(int)ys[y],(int)xs[x+1],(int)ys[y+1]),new RectF(xd[x],yd[y],xd[x+1],yd[y+1]),paint);
    }
    /** Instance and renderer belong to one UI thread. Camera/view policy is explicit, as in GLES. */
    public void draw(Canvas canvas,FxSceneInstance instance,FxCamera camera,float[] view,int width,int height,NodePainter bindings) {
        float[] projectionView=FxTransform.multiply(camera.projection(),view);
        int saved=canvas.save();canvas.clipRect(0,0,width,height);
        try {
            for(var state:instance.drawOrder()) {
                if(state.opacity()<=0)continue;
                float[] matrix=FxTransform.multiply(projectionView,state.worldData());
                if(state.hasMesh() && state.usesTexture()) {
                    Bitmap image=textures.get(state.textureId);
                    if(image==null && state.textureId!=0)throw new IllegalStateException("Unprepared canvas texture "+state.textureId);
                    if(image!=null) {
                        float[] vertices=state.meshData();boolean valid=true;
                        for(int i=0;i<4;i++) {
                            valid &= project(matrix,vertices[i*5],vertices[i*5+1],vertices[i*5+2],width,height,destination,i*2);
                            source[i*2]=vertices[i*5+3]*image.getWidth();source[i*2+1]=vertices[i*5+4]*image.getHeight();
                        }
                        if(valid && mapping.setPolyToPoly(source,0,destination,0,4)) {
                            paint.setAlpha(Math.round(state.opacity()*state.tintData()[3]*255));
                            float[] tint=state.tintData();
                            paint.setColorFilter(tint[0]==1&&tint[1]==1&&tint[2]==1?null:new LightingColorFilter(Color.rgb(Math.round(tint[0]*255),Math.round(tint[1]*255),Math.round(tint[2]*255)),0));
                            canvas.drawBitmap(image,mapping,paint);
                        }
                    }
                }
                if(bindings!=null) {
                    boolean valid=true;
                    float[] corners={0,0,1,0,1,1,0,1};
                    for(int i=0;i<4;i++)valid &= project(matrix,corners[i*2],corners[i*2+1],0,width,height,destination,i*2);
                    if(valid && local.setPolyToPoly(corners,0,destination,0,4)) {
                        int node=canvas.save();canvas.concat(local);
                        try{bindings.paint(canvas,state);}finally{canvas.restoreToCount(node);}
                    }
                }
            }
        }finally{canvas.restoreToCount(saved);}
    }
    private static boolean project(float[] matrix,float x,float y,float z,int width,int height,float[] out,int offset) {
        float[] p=FxTransform.point(matrix,x,y,z);
        if(p[3]<=0 || p[2]<-p[3] || p[2]>p[3])return false;
        out[offset]=(p[0]/p[3]+1)*width*.5f;out[offset+1]=(1-p[1]/p[3])*height*.5f;
        return true; // XY clipping is left to Canvas, preserving partially visible cloud edges.
    }
}
