package dev.sense.launcher;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import dev.sense.framework.fusion.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** One serialized worker owns Mode10 preparation; the UI receives immutable decoded assets. */
final class WeatherWidgetAssets {
    static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static FxResources shared;
    private static final Map<String,Layout> layouts=new HashMap<>();
    private static final Map<String,Typeface> fonts=new HashMap<>();
    private static final LruCache<String,Condition> conditions=new LruCache<>(48*1024*1024){
        @Override protected int sizeOf(String key,Condition value){return value.bytes;}
    };
    record Label(String name,String font,float height,int color,float width){}
    record Layout(FxScene scene,FxCanvasRenderer renderer,Map<Integer,Label> labels,Typeface normal,Typeface thin,int width,int height){}
    record Condition(FxSceneAsset asset,FxCanvasRenderer renderer,int width,int height,double start,double end,double fps,int bytes){}
    static int resource(Context c,String name,String type){int id=c.getResources().getIdentifier(name,type,c.getPackageName());if(id==0)throw new IllegalStateException("Missing widget resource "+name);return id;}
    private static FxResources shared(Context context)throws Exception {
        if(shared==null)try(var in=context.getResources().openRawResource(resource(context,"sense_weather_widget_shared","raw"))){shared=new FxResources(Mode10.read(in),null,24*1024*1024);}
        return shared;
    }
    static Layout layout(Context c,String style)throws Exception {
        Layout found=layouts.get(style);if(found!=null)return found;
        FxSceneAsset asset;
        try(var in=c.getResources().openRawResource(resource(c,"sense_weather_widget_layout_"+style,"raw"))){asset=FxSceneAsset.load(in,shared(c),32*1024*1024);}
        Map<Integer,FxTexture> textures=new HashMap<>(asset.textures);
        for(var node:asset.scene.nodes)if("common_panel.9.png".equals(node.name)||"common_divider_double.9.png".equals(node.name))textures.put(Mode10.hash(node.name),shared(c).texture(Mode10.hash(node.name)));
        JSONObject metadata;
        try(var in=c.getResources().openRawResource(resource(c,"sense_weather_widget_metadata","raw"))){metadata=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        Map<Integer,Label> labels=new HashMap<>();JSONArray list=metadata.getJSONObject(style).getJSONArray("labels");
        for(int i=0;i<list.length();i++){
            JSONObject item=list.getJSONObject(i);JSONArray color=item.getJSONArray("color");
            int argb=Color.argb((float)color.getDouble(3),(float)color.getDouble(0),(float)color.getDouble(1),(float)color.getDouble(2));
            labels.put(item.getInt("offset"),new Label(item.getString("name"),item.getString("font"),(float)item.getDouble("height"),argb,(float)item.getJSONArray("size").getDouble(0)));
        }
        Typeface normal=font(c,"sense_widget_droidsans",Typeface.SANS_SERIF),thin=font(c,"sense_widget_helveticaneueltpro35th",Typeface.create("sans-serif-thin",Typeface.NORMAL));
        int[] dimensions=dimensions(asset.scene);
        found=new Layout(asset.scene,new FxCanvasRenderer(textures),Map.copyOf(labels),normal,thin,dimensions[0],dimensions[1]);layouts.put(style,found);return found;
    }
    private static Typeface font(Context context,String name,Typeface fallback) {
        Typeface cached=fonts.get(name);if(cached!=null)return cached;
        int id=context.getResources().getIdentifier(name,"raw",context.getPackageName());
        if(id==0)return fallback;
        try(var input=context.getResources().openRawResource(id)){
            java.io.File file=new java.io.File(context.getCacheDir(),name+".ttf");
            try(var output=new java.io.FileOutputStream(file)){input.transferTo(output);}
            Typeface result=Typeface.createFromFile(file);fonts.put(name,result);return result;
        }catch(Exception error){return fallback;}
    }
    static Condition condition(Context c,int code,boolean small)throws Exception {
        if(code==0)return null;String key=(small?"s_":"")+String.format(Locale.ROOT,"%02d",code);
        Condition found=conditions.get(key);if(found!=null)return found;
        FxSceneAsset asset;
        try(var in=c.getResources().openRawResource(resource(c,"sense_weather_widget_condition_"+key,"raw"))){asset=FxSceneAsset.load(in,shared(c),32*1024*1024);}
        double start=0,end=29,fps=30;
        outer:for(var node:asset.scene.nodes)for(var marker:node.markers())if("In".equals(marker.name)||"Build".equals(marker.name)){
            start=marker.start;end=marker.lastFrame();fps=node.scalar("FramesPerSecond",30)*node.scalar("PlaybackSpeed",1);break outer;
        }
        int bytes=0;for(FxTexture image:asset.textures.values())bytes+=image.byteCount();int[] size=dimensions(asset.scene);
        found=new Condition(asset,new FxCanvasRenderer(asset.textures),size[0],size[1],start,end,fps,Math.max(1,bytes*2));conditions.put(key,found);return found;
    }
    private static int[] dimensions(FxScene scene){
        for(var node:scene.nodes){var value=node.property("Dimensions");if(value!=null&&value.kind==2&&value.size()==8){var bytes=value.bytes();return new int[]{bytes.getInt(),bytes.getInt()};}}
        throw new IllegalStateException("Missing original scene dimensions");
    }
    static int preview(Context c,String style){return resource(c,"sense_weather_widget_preview_"+style,"drawable");}
}
