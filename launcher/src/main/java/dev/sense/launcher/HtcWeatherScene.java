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
    private final RainCoverage rainCoverage=new RainCoverage();
    private final int wiperIndex,streakIndex;
    private final float[] anchor=new float[3];
    private float sourceHingeX,sourceHingeY;
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
            int wiper=-1,streak=-1;
            for(int i=0;i<nodes.length;i++){
                if("wiper".equals(nodes[i].coverageRole))wiper=i;
                if("streaks".equals(nodes[i].coverageRole))streak=i;
            }
            wiperIndex=wiper;streakIndex=streak;
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
        if(fit<=0||clip.isEmpty())return;
        boolean extendRain=wiperIndex>=0&&streakIndex>=0;
        if(extendRain) {
            Node wiper=nodes[wiperIndex],streak=nodes[streakIndex];
            nodeAnchor(wiper,anchor);sourceHingeX=anchor[0];sourceHingeY=anchor[1];
            float wetBottom=0,bladeLength=0;
            for(int v=0;v<4;v++) {
                float x=streak.vertices[v*5],y=streak.vertices[v*5+1],z=streak.vertices[v*5+2];
                wetBottom=Math.max(wetBottom,streak.world[1]*x+streak.world[5]*y+streak.world[9]*z+streak.world[13]);
            }
            wiper.pivot.sample(wiper.localFrame,pivot,false);
            for(int v=0;v<4;v++)bladeLength=Math.max(bladeLength,wiper.vertices[v*5+1]+pivot[1]);
            bladeLength*=(float)Math.hypot(wiper.world[4],wiper.world[5]);
            rainCoverage.configure(logicalWidth,logicalHeight,wetBottom,sourceHingeX,sourceHingeY,
                    clip.width()/fit,clip.height()/fit,bladeLength);
        }
        int saved=canvas.save();canvas.clipRect(clip);
        for(int index:drawOrder) {
            Node n=nodes[index];
            if(n.texture==null||n.worldOpacity<=.001f)continue;
            boolean particle=extendRain&&("wet".equals(n.coverageRole)||"falling".equals(n.coverageRole));
            float shiftX=0,shiftY=0;
            if(particle){
                nodeAnchor(n,anchor);
                rainCoverage.mapAnchor(anchor[0],anchor[1],"wet".equals(n.coverageRole));
                shiftX=rainCoverage.mappedX-anchor[0];shiftY=rainCoverage.mappedY-anchor[1];
            }
            if(extendRain&&"wiper".equals(n.coverageRole))n.pivot.sample(n.localFrame,pivot,false);
            for(int v=0;v<4;v++) {
                float x=n.vertices[v*5],y=n.vertices[v*5+1],z=n.vertices[v*5+2];
                if(extendRain&&"wiper".equals(n.coverageRole))y=(y+pivot[1])*rainCoverage.bladeScale-pivot[1];
                float[] m=n.world;
                float wx=m[0]*x+m[4]*y+m[8]*z+m[12];
                float wy=m[1]*x+m[5]*y+m[9]*z+m[13];
                float wz=m[2]*x+m[6]*y+m[10]*z+m[14];
                float distance=extendRain?50*logicalWidth/36:1000;
                float perspective=distance/Math.max(100,distance-wz);
                if(extendRain){
                    wx=logicalWidth/2+(wx-logicalWidth/2)*perspective;
                    wy=logicalHeight/2+(wy-logicalHeight/2)*perspective;
                    if("streaks".equals(n.coverageRole)){
                        rainCoverage.mapFilm(wx,wy);wx=rainCoverage.mappedX;wy=rainCoverage.mappedY;
                    }else if(particle){wx+=shiftX;wy+=shiftY;}
                    else{wx+=rainCoverage.hingeX-sourceHingeX;wy+=rainCoverage.hingeY-sourceHingeY;}
                    destination[v*2]=clip.left+wx*fit;destination[v*2+1]=clip.top+wy*fit;
                }else{
                    destination[v*2]=bounds.centerX()+(wx-logicalWidth/2)*fit*perspective;
                    destination[v*2+1]=bounds.centerY()+(wy-logicalHeight/2)*fit*perspective;
                }
                source[v*2]=n.vertices[v*5+3]*n.texture.getWidth();
                source[v*2+1]=n.vertices[v*5+4]*n.texture.getHeight();
            }
            paint.setAlpha(Math.round(Math.max(0,Math.min(1,n.worldOpacity))*255));
            if(projection.setPolyToPoly(source,0,destination,0,4)) {
                canvas.drawBitmap(n.texture,projection,paint);
            }
        }
        canvas.restoreToCount(saved);
    }
    private void nodeAnchor(Node node,float[] result){
        node.pivot.sample(node.localFrame,pivot,false);
        if(!node.pivotEnabled)Arrays.fill(pivot,0);
        float[] m=node.world;
        float x=m[12]-m[0]*pivot[0]-m[4]*pivot[1]-m[8]*pivot[2];
        float y=m[13]-m[1]*pivot[0]-m[5]*pivot[1]-m[9]*pivot[2];
        float z=m[14]-m[2]*pivot[0]-m[6]*pivot[1]-m[10]*pivot[2];
        float distance=50*logicalWidth/36,perspective=distance/Math.max(100,distance-z);
        result[0]=logicalWidth/2+(x-logicalWidth/2)*perspective;
        result[1]=logicalHeight/2+(y-logicalHeight/2)*perspective;result[2]=z;
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
        if(!n.pivotEnabled)Arrays.fill(pivot,0);
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
        final boolean visible,pivotEnabled;
        final String coverageRole;
        final float inFrame,duration,childStart;
        float localFrame;boolean active;
        final float[] local=new float[16],chain=new float[16],world=new float[16];
        final Bitmap texture;
        final float[] vertices;
        float worldOpacity;
        Node(Context context,JSONObject data,boolean fullscreen)throws JSONException {
            childStart=(float)data.optDouble("childStart",0);
            inFrame=(float)data.getDouble("inFrame");duration=(float)data.getDouble("duration");
            parent=data.getInt("parent");transformParent=data.getInt("transformParent");visible=data.getBoolean("visible");
            pivotEnabled=data.optBoolean("pivotEnabled",true);coverageRole=fullscreen?data.optString("coverageRole",""):"";
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
                vertices=new float[20];JSONArray points=data.getJSONArray("vertices");
                for(int i=0;i<4;i++)for(int c=0;c<5;c++)vertices[i*5+c]=(float)points.getJSONArray(i).getDouble(c);
            } else {texture=null;vertices=null;}
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
