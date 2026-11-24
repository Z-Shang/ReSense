package dev.sense.launcher;

import android.content.SharedPreferences;
import org.json.*;

/** Persist complete workspace changes before the settings activity returns home. */
final class WorkspaceSettings {
    static final String HIDE_NAVIGATION_BAR="hideNavigationBar";
    static boolean hideNavigationBar(SharedPreferences preferences){return preferences.getBoolean(HIDE_NAVIGATION_BAR,true);}

    static JSONArray items(SharedPreferences preferences){try{return new JSONArray(preferences.getString("items","[]"));}catch(JSONException error){return new JSONArray();}}
    static boolean hasClock(SharedPreferences preferences){JSONArray items=items(preferences);for(int i=0;i<items.length();i++)if("clock".equals(items.optJSONObject(i).optString("type")))return true;return false;}
    static void grid(SharedPreferences preferences,int columns,int rows){
        columns=Math.max(3,Math.min(8,columns));rows=Math.max(4,Math.min(10,rows));
        JSONArray old=items(preferences),packed=new JSONArray();int pages=Math.max(1,preferences.getInt("pages",3));
        for(int i=0;i<old.length();i++){
            JSONObject item=old.optJSONObject(i);if(item==null)continue;
            Workspace.put(item,"w",Math.max(1,Math.min(columns,item.optInt("w",1))));Workspace.put(item,"h",Math.max(1,Math.min(rows,item.optInt("h",1))));
            pages=Math.max(pages,place(packed,item,columns,rows,1));
        }
        preferences.edit().putString("items",packed.toString()).putInt("columns",columns).putInt("rows",rows).putInt("pages",pages).putInt("currentPage",1).apply();
    }
    static void clock(SharedPreferences preferences,boolean visible){
        JSONArray old=items(preferences),next=new JSONArray();JSONObject clock=null;
        for(int i=0;i<old.length();i++){
            JSONObject item=old.optJSONObject(i);if(item==null)continue;
            if("clock".equals(item.optString("type"))){if(visible && clock==null){clock=item;next.put(item);}}
            else next.put(item);
        }
        int pages=Math.max(1,preferences.getInt("pages",3)),page=Math.max(1,Math.min(pages,preferences.getInt("currentPage",1)));
        if(visible && clock==null){
            clock=new JSONObject();Workspace.put(clock,"type","clock");Workspace.put(clock,"w",preferences.getInt("columns",4));Workspace.put(clock,"h",2);
            page=place(next,clock,preferences.getInt("columns",4),preferences.getInt("rows",6),page);pages=Math.max(pages,page);
        }
        preferences.edit().putString("items",next.toString()).putInt("pages",pages).putInt("currentPage",page).apply();
    }
    private static int place(JSONArray placed,JSONObject item,int columns,int rows,int start){
        int width=item.optInt("w",1),height=item.optInt("h",1);
        for(int page=Math.max(1,start);;page++)for(int y=0;y<=rows-height;y++)for(int x=0;x<=columns-width;x++){
            boolean free=true;
            for(int i=0;i<placed.length();i++){
                JSONObject other=placed.optJSONObject(i);if(other.optInt("page")!=page)continue;
                if(x<other.optInt("x")+other.optInt("w",1) && x+width>other.optInt("x") && y<other.optInt("y")+other.optInt("h",1) && y+height>other.optInt("y")){free=false;break;}
            }
            if(free){Workspace.put(item,"page",page);Workspace.put(item,"x",x);Workspace.put(item,"y",y);placed.put(item);return page;}
        }
    }
}
