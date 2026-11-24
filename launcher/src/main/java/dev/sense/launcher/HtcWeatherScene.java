package dev.sense.launcher;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import org.json.*;
import java.util.Arrays;

/** Native quad playback of original per-condition scenes, including sibling layer-parent links. */
final class HtcWeatherScene {
    private static final LruCache<String,Bitmap> TEXTURES=new LruCache<>(12*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    final int code;
    final float start,entranceEnd,end;
    final long duration;
    private final Node[] nodes;
    private final int[] drawOrder;
    private final byte[] chainState,worldState,timeState;
    private final float[] p=new float[3],q=new float[4],scale=new float[3],pivot=new float[3],alpha=new float[1];
    private final Matrix projection=new Matrix();
    private final float[] source=new float[8],destination=new float[8];
    private final RectF layerBounds=new RectF();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private float frame;
    private final float logicalWidth,logicalHeight;

    HtcWeatherScene(Context context,int code) {
        this(context,String.format(java.util.Locale.ROOT,"htc_weather_scene_%02d",code),code);
    }
    HtcWeatherScene(Context context,String resourceName,int code) {
        this.code=code;
        try {
            int resource=context.getResources().getIdentifier(resourceName,"raw",context.getPackageName());
            if(resource==0)throw new JSONException("No scene for HTC code "+code);
            JSONObject data;
            try(var input=context.getResources().openRawResource(resource)) {
                data=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            }
            logicalWidth=(float)data.optDouble("width",480);logicalHeight=(float)data.optDouble("height",480);
            JSONObject entrance=data.getJSONObject("entrance");
            start=(float)entrance.getDouble("start");entranceEnd=(float)entrance.getDouble("last_frame");
            float settled=entranceEnd;
            JSONArray timelines=data.getJSONArray("timelines");
            for(int i=0;i<timelines.length();i++) {
                JSONArray markers=timelines.getJSONObject(i).getJSONArray("markers");
                for(int j=0;j<markers.length();j++) {
                    JSONObject marker=markers.getJSONObject(j);
                    if("Rest".equals(marker.getString("name")))settled=Math.max(settled,(float)marker.getDouble("last_frame"));
                }
            }
            end=settled;
            duration=Math.max(1,Math.round((end-start)*1000/(data.getDouble("fps")*data.getDouble("speed"))));
            JSONArray list=data.getJSONArray("nodes");nodes=new Node[list.length()];
            for(int i=0;i<nodes.length;i++)nodes[i]=new Node(context,list.getJSONObject(i),resourceName.startsWith("htc_overlay_scene_"));
            JSONArray order=data.getJSONArray("drawOrder");drawOrder=new int[order.length()];
            for(int i=0;i<drawOrder.length;i++)drawOrder[i]=order.getInt(i);
            chainState=new byte[nodes.length];worldState=new byte[nodes.length];timeState=new byte[nodes.length];
        } catch(java.io.IOException|JSONException error){throw new IllegalStateException("Invalid original weather scene "+code,error);}
    }
    void draw(Canvas canvas,RectF bounds,float frame) {draw(canvas,bounds,bounds,frame);}
    void draw(Canvas canvas,RectF bounds,RectF clip,float frame) {
        this.frame=frame;
        Arrays.fill(chainState,(byte)0);Arrays.fill(worldState,(byte)0);Arrays.fill(timeState,(byte)0);
        for(int i=0;i<nodes.length;i++)time(i);
        for(int i=0;i<nodes.length;i++)world(i);
        float fit=Math.min(bounds.width()/logicalWidth,bounds.height()/logicalHeight);
        int saved=canvas.save();canvas.clipRect(clip);
        for(int index:drawOrder) {
            Node n=nodes[index];
            if(n.texture==null||n.worldOpacity<=.001f)continue;
            for(int v=0;v<4;v++) {
                float x=n.vertices[v*5],y=n.vertices[v*5+1],z=n.vertices[v*5+2];
                float[] m=n.world;
                float wx=m[0]*x+m[4]*y+m[8]*z+m[12];
                float wy=m[1]*x+m[5]*y+m[9]*z+m[13];
                float wz=m[2]*x+m[6]*y+m[10]*z+m[14];
                // The effective Fusion camera is still under investigation; retain a shared adapted camera.
                float perspective=1000f/Math.max(100f,1000f-wz);
                destination[v*2]=bounds.centerX()+(wx-logicalWidth/2)*fit*perspective;
                destination[v*2+1]=bounds.centerY()+(wy-logicalHeight/2)*fit*perspective;
                source[v*2]=n.vertices[v*5+3]*n.texture.getWidth();
                source[v*2+1]=n.vertices[v*5+4]*n.texture.getHeight();
            }
            paint.setAlpha(Math.round(Math.max(0,Math.min(1,n.worldOpacity))*255));
            if(projection.setPolyToPoly(source,0,destination,0,4)) {
                if(n.edgeFade==null)canvas.drawBitmap(n.texture,projection,paint);
                else {
                    // This source texture ends in opaque streaks. Its lower edge
                    // is exposed on modern tall displays, so blend it out in
                    // texture space without changing the wiper or its tracks.
                    layerBounds.set(destination[0],destination[1],destination[0],destination[1]);
                    for(int v=1;v<4;v++){
                        layerBounds.left=Math.min(layerBounds.left,destination[v*2]);layerBounds.right=Math.max(layerBounds.right,destination[v*2]);
                        layerBounds.top=Math.min(layerBounds.top,destination[v*2+1]);layerBounds.bottom=Math.max(layerBounds.bottom,destination[v*2+1]);
                    }
                    if(layerBounds.intersect(clip)){
                        int layer=canvas.saveLayer(layerBounds,null);
                        canvas.drawBitmap(n.texture,projection,paint);
                        canvas.concat(projection);
                        canvas.drawRect(0,0,n.texture.getWidth(),n.texture.getHeight(),n.edgeFade);
                        canvas.restoreToCount(layer);
                    }
                }
            }
        }
        canvas.restoreToCount(saved);
    }
    private void time(int index) {
        if(timeState[index]==2)return;
        if(timeState[index]==1)throw new IllegalStateException("Cyclic weather timing hierarchy");
        timeState[index]=1;Node n=nodes[index];
        float parentFrame=frame;
        if(n.parent>=0){time(n.parent);parentFrame=nodes[n.parent].localFrame+nodes[n.parent].childStart;}
        n.localFrame=parentFrame-Math.max(0,n.inFrame);
        n.active=n.localFrame>=0 && (n.duration<0 || n.localFrame<n.duration);
        timeState[index]=2;
    }
    private void chain(int index) {
        if(chainState[index]==2)return;
        if(chainState[index]==1)throw new IllegalStateException("Cyclic weather layer parents");
        chainState[index]=1;Node n=nodes[index];
        n.position.sample(n.localFrame,p,false);n.rotation.sample(n.localFrame,q,true);n.scale.sample(n.localFrame,scale,false);n.pivot.sample(n.localFrame,pivot,false);
        WeatherTransform.compose(p,q,scale,pivot,n.local);
        if(n.transformParent>=0) {
            chain(n.transformParent);WeatherTransform.multiply(nodes[n.transformParent].chain,n.local,n.chain);
        } else System.arraycopy(n.local,0,n.chain,0,16);
        chainState[index]=2;
    }
    private void world(int index) {
        if(worldState[index]==2)return;
        if(worldState[index]==1)throw new IllegalStateException("Cyclic weather scene hierarchy");
        worldState[index]=1;chain(index);Node n=nodes[index];
        n.opacity.sample(n.localFrame,alpha,false);float opacity=n.visible && n.active ? alpha[0]:0;
        if(n.parent>=0) {
            world(n.parent);WeatherTransform.multiply(nodes[n.parent].world,n.chain,n.world);
            n.worldOpacity=opacity*nodes[n.parent].worldOpacity;
        } else {System.arraycopy(n.chain,0,n.world,0,16);n.worldOpacity=opacity;}
        worldState[index]=2;
    }
    private static final class Node {
        final int parent,transformParent;
        final Track position,rotation,scale,pivot,opacity;
        final boolean visible;
        final float inFrame,duration,childStart;
        float localFrame;boolean active;
        final float[] local=new float[16],chain=new float[16],world=new float[16];
        final Bitmap texture;
        final Paint edgeFade;
        final float[] vertices;
        float worldOpacity;
        Node(Context context,JSONObject data,boolean fullscreen)throws JSONException {
            childStart=(float)data.optDouble("childStart",0);
            inFrame=(float)data.getDouble("inFrame");duration=(float)data.getDouble("duration");
            parent=data.getInt("parent");transformParent=data.getInt("transformParent");visible=data.getBoolean("visible");
            JSONObject tracks=data.getJSONObject("tracks");
            position=new Track(tracks.optJSONObject("Position"),new float[]{0,0,0});
            rotation=new Track(tracks.optJSONObject("Rotation"),new float[]{0,0,0,1});
            scale=new Track(tracks.optJSONObject("Scale"),new float[]{1,1,1});
            pivot=new Track(tracks.optJSONObject("PivotPosition"),new float[]{0,0,0});
            opacity=new Track(tracks.optJSONObject("Opacity"),new float[]{1});
            if(data.has("texture")) {
                String name=data.getString("texture");Bitmap image=TEXTURES.get(name);
                if(image==null) {
                    int id=context.getResources().getIdentifier(name,"drawable",context.getPackageName());
                    if(id==0)throw new JSONException("Missing weather texture "+name);
                    image=BitmapFactory.decodeResource(context.getResources(),id);
                    if(image==null)throw new JSONException("Invalid weather texture "+name);
                    TEXTURES.put(name,image);
                }
                texture=image;
                if(fullscreen && "b_raindrop_streaks.png".equals(data.optString("name"))) {
                    edgeFade=new Paint(Paint.ANTI_ALIAS_FLAG);
                    edgeFade.setShader(new LinearGradient(0,image.getHeight()*.85f,0,image.getHeight(),
                            Color.WHITE,Color.TRANSPARENT,Shader.TileMode.CLAMP));
                    edgeFade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                }else edgeFade=null;
                vertices=new float[20];JSONArray points=data.getJSONArray("vertices");
                for(int i=0;i<4;i++)for(int c=0;c<5;c++)vertices[i*5+c]=(float)points.getJSONArray(i).getDouble(c);
            } else {texture=null;vertices=null;edgeFade=null;}
        }
    }
    private static final class Track {
        final float[] frames;
        final float[][] values;
        Track(JSONObject data,float[] fallback)throws JSONException {
            if(data==null||data.has("v")) {
                frames=new float[]{0};values=new float[][]{fallback.clone()};
                if(data!=null)for(int c=0;c<fallback.length;c++)values[0][c]=(float)data.getJSONArray("v").getDouble(c);
            } else {
                JSONArray samples=data.getJSONArray("s");frames=new float[samples.length()];values=new float[samples.length()][fallback.length];
                if(frames.length==0)throw new JSONException("Empty weather track");
                for(int i=0;i<frames.length;i++) {
                    JSONArray sample=samples.getJSONArray(i);frames[i]=(float)sample.getDouble(0);
                    for(int c=0;c<fallback.length;c++)values[i][c]=(float)sample.getDouble(c+1);
                }
            }
        }
        void sample(float frame,float[] output,boolean quaternion) {
            int right=Arrays.binarySearch(frames,frame);
            if(right<0)right=-right-1;
            int b=Math.min(frames.length-1,right),a=Math.max(0,b-1);
            float t=frames[b]==frames[a]?0:Math.max(0,Math.min(1,(frame-frames[a])/(frames[b]-frames[a])));
            if(quaternion)WeatherProjection.interpolate(values[a],values[b],t,output);
            else for(int c=0;c<output.length;c++)output[c]=values[a][c]+(values[b][c]-values[a][c])*t;
        }
    }
}
