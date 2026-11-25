package dev.sense.launcher;

import android.app.AlertDialog;
import android.appwidget.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Persistent paged cell layout and Android widget host. */
final class Workspace extends FrameLayout {
    final MainActivity activity;
    final android.content.SharedPreferences prefs;
    final AppWidgetHost host;
    JSONArray items;
    int columns, rows, page = 1, pages;
    private boolean dragging;
    private PageOverview pageOverview;
    private float pinchSpan;
    private boolean pinching;
    private WidgetResizeFrame resizeFrame;
    private JSONObject resizeAfterDrag;
    private JSONObject draggedItem;
    private int dragColumns = 1, dragRows = 1;
    private int dropColumn = -1, dropRow = -1;
    private float dragX, dragY;
    private final android.graphics.Paint gridPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Paint dropPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF dropBounds = new android.graphics.RectF();
    private float downX, downY;
    private boolean discoverGesture;
    private float discoverDownRawX;
    private float touchRawX,touchRawY;
    private boolean pageGesture,desktopGesture,verticalEligible;
    private int pageSwipeFrom;
    private VelocityTracker swipeVelocity;
    private android.graphics.Picture outgoingPage;
    private final java.util.List<View> departingViews=new java.util.ArrayList<>();
    private float gestureStartProgress;
    private android.animation.ValueAnimator pageAnimator;
    private float transitionProgress = 1f;
    private int transitionDirection;
    private final android.graphics.Camera pageCamera = new android.graphics.Camera();
    private final android.graphics.Matrix pageMatrix = new android.graphics.Matrix();
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int edgeDirection;
    private final Runnable edgeTurn = () -> { if(edgeDirection != 0) { turnTo(Math.max(1, Math.min(pages, page + edgeDirection))); } };
    Workspace(MainActivity a, AppWidgetHost h) {
        this(a,h,a.getSharedPreferences("workspace",0));
    }
    Workspace(MainActivity a, AppWidgetHost h, android.content.SharedPreferences preferences) {
        super(a); activity=a; host=h; prefs=preferences;
        columns=prefs.getInt("columns",4); rows=prefs.getInt("rows",6); pages=prefs.getInt("pages",3);
        try { items=new JSONArray(prefs.getString("items","[]")); } catch(JSONException e) {items=new JSONArray();}
        if (!prefs.contains("items")) {
            JSONObject clock=new JSONObject();put(clock,"type","clock");put(clock,"page",1);put(clock,"x",0);put(clock,"y",0);put(clock,"w",columns);put(clock,"h",2);items.put(clock);save();
        }
        page=Math.max(1,Math.min(pages,prefs.getInt("currentPage",1)));
        setOnLongClickListener(v->{showPageOverview();return true;});
        setOnDragListener((v,e)-> {
            if(e.getAction()==DragEvent.ACTION_DRAG_STARTED) {
                if(e.getClipDescription()==null || !e.getClipDescription().hasMimeType("text/vnd.sense.app")) return false;
                finishResize();resizeAfterDrag=null;
                dragging=true;
                draggedItem=e.getLocalState() instanceof JSONObject item ? item : null;
                dragColumns=draggedItem==null ? 1 : draggedItem.optInt("w",1);
                dragRows=draggedItem==null ? 1 : draggedItem.optInt("h",1);
                dropColumn=dropRow=-1;
                activity.setHomeDragging(this,true);
                invalidate();
                return true;
            }
            if(e.getAction()==DragEvent.ACTION_DRAG_ENTERED || e.getAction()==DragEvent.ACTION_DRAG_LOCATION) updateDropTarget(e.getX(),e.getY());
            if(e.getAction()==DragEvent.ACTION_DRAG_EXITED) { dropColumn=dropRow=-1;handler.removeCallbacks(edgeTurn);edgeDirection=0;invalidate(); }
            if(e.getAction()==DragEvent.ACTION_DRAG_ENDED) {
                dragging=false; draggedItem=null; dropColumn=dropRow=-1; activity.setHomeDragging(this,false);invalidate();
                if(resizeAfterDrag!=null){JSONObject selected=resizeAfterDrag;resizeAfterDrag=null;post(()->beginResize(selected));}
            }
            if(e.getAction()==DragEvent.ACTION_DRAG_LOCATION) {
                int direction=e.getX()<dp(28)?-1:e.getX()>getWidth()-dp(28)?1:0;
                if(direction!=edgeDirection){handler.removeCallbacks(edgeTurn);edgeDirection=direction;if(direction!=0)handler.postDelayed(edgeTurn,650);}
            }
            if(e.getAction()==DragEvent.ACTION_DRAG_ENDED || e.getAction()==DragEvent.ACTION_DROP){handler.removeCallbacks(edgeTurn);edgeDirection=0;}
            if(e.getAction()==DragEvent.ACTION_DROP) {
                if(page==0)page=1;
                JSONObject item;
                if(e.getLocalState() instanceof JSONObject old) item=old;
                else {item=new JSONObject();put(item,"type","app");put(item,"component",e.getClipData().getItemAt(0).getText().toString());}
                updateDropTarget(e.getX(),e.getY());
                int x=dropColumn, y=dropRow;
                if(free(page,x,y,item.optInt("w",1),item.optInt("h",1),item)){
                    put(item,"page",page);put(item,"x",x);put(item,"y",y);
                    if(!(e.getLocalState() instanceof JSONObject))items.put(item);
                    if(!"app".equals(item.optString("type")))resizeAfterDrag=item;
                    save();render();
                } else toast("That space is occupied");
            }
            return true;
        });
        render();
    }
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    static void put(JSONObject j,String k,Object v){try{j.put(k,v);}catch(JSONException e){throw new IllegalStateException(e);}}
    void save(){prefs.edit().putString("items",items.toString()).putInt("columns",columns).putInt("rows",rows).putInt("pages",pages).apply();}
    boolean free(int p,int x,int y,int w,int h,JSONObject ignore){
        if(x<0||y<0||x+w>columns||y+h>rows)return false;
        for(int i=0;i<items.length();i++){JSONObject j=items.optJSONObject(i);if(j==ignore||j.optInt("page")!=p)continue;
            if(x<j.optInt("x")+j.optInt("w",1)&&x+w>j.optInt("x")&&y<j.optInt("y")+j.optInt("h",1)&&y+h>j.optInt("y"))return false;
        }return true;
    }
    void addSearch(){JSONObject item=new JSONObject();put(item,"type","search");put(item,"w",Math.min(columns,4));put(item,"h",1);place(item);save();render();}
    void addShortcut(ComponentName component){JSONObject item=new JSONObject();put(item,"type","app");put(item,"component",component.flattenToString());put(item,"w",1);put(item,"h",1);place(item);save();render();}
    void addWeather(String requestedStyle){String style=SenseWeatherView.validStyle(requestedStyle);JSONObject item=new JSONObject();put(item,"type","weather");put(item,"weatherStyle",style);put(item,"w",Math.min(columns,Integer.parseInt(style.substring(0,1))));put(item,"h",Math.min(rows,Integer.parseInt(style.substring(2,3))));place(item);save();render();}
    void addMusic(){addMusic(2);}
    void addMusic(int rowsRequested){int style=rowsRequested==1 || rowsRequested==4?rowsRequested:2;JSONObject item=new JSONObject();put(item,"type","music");put(item,"musicRows",style);put(item,"w",Math.min(columns,4));put(item,"h",Math.min(rows,style));place(item);save();render();}
    void addWidget(int id){
        AppWidgetProviderInfo info=AppWidgetManager.getInstance(activity).getAppWidgetInfo(id);
        if(info==null){host.deleteAppWidgetId(id);return;}
        int w=Math.min(columns,Math.max(1,info.targetCellWidth>0?info.targetCellWidth:(int)Math.ceil((double)info.minWidth/Math.max(1,getWidth()/columns))));
        int h=Math.min(rows,Math.max(1,info.targetCellHeight>0?info.targetCellHeight:(int)Math.ceil((double)info.minHeight/Math.max(1,getHeight()/rows))));
        JSONObject j=new JSONObject();put(j,"type","widget");put(j,"id",id);put(j,"w",w);put(j,"h",h);
        place(j);save();render();post(()->beginResize(j));
    }
    void place(JSONObject j){
        for(int p=Math.max(1,page);;p++){for(int y=0;y<rows;y++)for(int x=0;x<columns;x++)if(free(p,x,y,j.optInt("w",1),j.optInt("h",1),j)){put(j,"page",p);put(j,"x",x);put(j,"y",y);items.put(j);pages=Math.max(pages,p);page=p;return;}}
    }
    TextView text(String s){TextView t=new TextView(activity);t.setText(s);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);return t;}
    void render(){
        resizeFrame=null;
        if(!pageGesture)prefs.edit().putInt("currentPage",page).apply();
        removeAllViews();
        if(page==0){
            LinearLayout feed=new LinearLayout(activity);feed.setOrientation(LinearLayout.VERTICAL);feed.setGravity(Gravity.CENTER);feed.setPadding(dp(24),dp(24),dp(24),dp(24));
            TextView title=text("Google Discover");title.setTextSize(24);feed.addView(title);
            TextView status=text(activity.discover()==null ? "Connecting…" : activity.discover().status());status.setTextSize(16);feed.addView(status);
            Button retry=new Button(activity);retry.setText("Retry connection");retry.setOnClickListener(v->{if(activity.discover()!=null)activity.discover().retry();});feed.addView(retry);
            Button open=new Button(activity);open.setText("Open Google app");open.setOnClickListener(v->activity.openGoogle());feed.addView(open);
            addView(feed,new LayoutParams(-1,-1));
        }
        else {
            for(int i=0;i<items.length();i++){
                JSONObject j=items.optJSONObject(i);if(j.optInt("page")!=page)continue;
                View child;
                if("clock".equals(j.optString("type"))) child=new SenseClockView(activity);
                else if("search".equals(j.optString("type"))) child=new SenseSearchView(activity);
                else if("music".equals(j.optString("type"))) child=new SenseMusicView(activity,j.optInt("musicRows",2));
                else if("weather".equals(j.optString("type"))) child=new SenseWeatherView(activity,j.optString("weatherStyle","2x2"));
                else if("widget".equals(j.optString("type"))){
                    AppWidgetProviderInfo info=AppWidgetManager.getInstance(activity).getAppWidgetInfo(j.optInt("id"));if(info==null)continue;
                    AppWidgetHostView widget=host.createView(activity,j.optInt("id"),info);child=widget;
                }else{
                    child=createShortcut(j);if(child==null)continue;
                }
                child.setTag(j);child.setOnLongClickListener(v->{startMove(v,j);return true;});addView(child);
            }
        }
        if(dragging && dropColumn>=0) updateDropTarget(dragX,dragY);
        requestLayout();activity.updatePage(page,pages);
    }
    private View createShortcut(JSONObject j){
        ComponentName component=ComponentName.unflattenFromString(j.optString("component"));if(component==null)return null;
                    LinearLayout icon=new LinearLayout(activity);
                    icon.setOrientation(LinearLayout.VERTICAL);
                    icon.setGravity(Gravity.CENTER);
                    icon.setPadding(dp(3),dp(5),dp(3),0);
                    android.widget.ImageView image=new android.widget.ImageView(activity);
                    icon.addView(image,new LinearLayout.LayoutParams(dp(44),dp(44)));
                    TextView label=text("");label.setTextSize(12);label.setSingleLine(true);
                    label.setIncludeFontPadding(false);
                    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    label.setShadowLayer(dp(1),0,dp(1),Color.BLACK);
                    LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(-1,-2);
                    labelParams.topMargin=dp(4);
                    icon.addView(label,labelParams);
                    try{label.setText(activity.getPackageManager().getActivityInfo(component,0).loadLabel(activity.getPackageManager()));image.setImageDrawable(activity.getPackageManager().getActivityIcon(component));}catch(Exception e){label.setText("Unavailable");}
                    icon.setContentDescription(label.getText());
                    icon.setOnClickListener(v->activity.open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));return icon;
    }
    /** Snapshot in workspace coordinates; never allocate or rebind widget host IDs. */
    android.graphics.Bitmap pagePreview(int target,int maxWidth,int maxHeight){
        int sourceWidth=Math.max(1,getWidth()),sourceHeight=Math.max(1,getHeight());
        float scale=Math.min(maxWidth/(float)sourceWidth,maxHeight/(float)sourceHeight);
        int width=Math.max(1,Math.round(sourceWidth*scale)),height=Math.max(1,Math.round(sourceHeight*scale));
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.scale(width/(float)sourceWidth,height/(float)sourceHeight);
        if(target==page){
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);
                if(child.getVisibility()!=VISIBLE || !(child.getTag() instanceof JSONObject item) || item.optInt("page")!=target)continue;
                if(child.isLayoutRequested() || child.getWidth()==0 || child.getHeight()==0){
                    int x=item.optInt("x")*sourceWidth/columns,y=item.optInt("y")*sourceHeight/rows;
                    int w=item.optInt("w",1)*sourceWidth/columns,h=item.optInt("h",1)*sourceHeight/rows;
                    child.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));child.layout(x,y,x+w,y+h);
                }
                int saved=canvas.save();canvas.translate(child.getLeft(),child.getTop());child.draw(canvas);canvas.restoreToCount(saved);
            }
            return bitmap;
        }
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null || item.optInt("page")!=target)continue;
            int x=item.optInt("x")*sourceWidth/columns,y=item.optInt("y")*sourceHeight/rows;
            int w=item.optInt("w",1)*sourceWidth/columns,h=item.optInt("h",1)*sourceHeight/rows;
            if(w<=0 || h<=0)continue;
            String type=item.optString("type");View preview=null;
            if("clock".equals(type))preview=new SenseClockView(activity);
            else if("search".equals(type))preview=new SenseSearchView(activity);
            else if("music".equals(type))preview=new SenseMusicView(activity,item.optInt("musicRows",2));
            else if("weather".equals(type)){
                var image=new ImageView(activity);image.setImageResource(WeatherWidgetAssets.preview(activity,SenseWeatherView.validStyle(item.optString("weatherStyle"))));image.setScaleType(ImageView.ScaleType.FIT_CENTER);preview=image;
            }
            else if("widget".equals(type)){
                AppWidgetProviderInfo info=AppWidgetManager.getInstance(activity).getAppWidgetInfo(item.optInt("id"));
                if(info==null)continue;
                android.graphics.drawable.Drawable artwork=null;
                try{artwork=info.loadPreviewImage(activity,getResources().getDisplayMetrics().densityDpi);
                    if(artwork==null)artwork=info.loadIcon(activity,getResources().getDisplayMetrics().densityDpi);
                }catch(RuntimeException ignored){}
                if(artwork!=null){
                    float fit=Math.min(w/(float)Math.max(1,artwork.getIntrinsicWidth()),h/(float)Math.max(1,artwork.getIntrinsicHeight()));
                    int pw=Math.max(1,Math.round(artwork.getIntrinsicWidth()*fit)),ph=Math.max(1,Math.round(artwork.getIntrinsicHeight()*fit));
                    artwork.setBounds(x+(w-pw)/2,y+(h-ph)/2,x+(w+pw)/2,y+(h+ph)/2);artwork.draw(canvas);
                }
                continue;
            }else preview=createShortcut(item);
            if(preview==null)continue;
            preview.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));preview.layout(0,0,w,h);
            int saved=canvas.save();canvas.translate(x,y);preview.draw(canvas);canvas.restoreToCount(saved);
        }
        return bitmap;
    }
    void removeDraggedItem(JSONObject item){
        resizeAfterDrag=null;dropColumn=dropRow=-1;handler.removeCallbacks(edgeTurn);edgeDirection=0;
        remove(item);
    }
    void startMove(View v,JSONObject j){ClipData data=new ClipData("Home item",new String[]{"text/vnd.sense.app"},new ClipData.Item(j.optString("component")));v.startDragAndDrop(data,new View.DragShadowBuilder(v),j,0);}
    void remove(JSONObject j){if(j==null)return;boolean present=false;for(int i=0;i<items.length();i++)if(items.optJSONObject(i)==j)present=true;if(!present)return;JSONArray next=new JSONArray();for(int i=0;i<items.length();i++)if(items.optJSONObject(i)!=j)next.put(items.optJSONObject(i));items=next;if("widget".equals(j.optString("type")))host.deleteAppWidgetId(j.optInt("id"));save();render();}
    void beginResize(JSONObject item) {
        finishResize();
        if(item.optInt("page")!=page)return;
        WidgetResizeFrame frame=new WidgetResizeFrame(this,item);
        if(!frame.supported()){toast("This widget does not support resizing");return;}
        resizeFrame=frame;addView(frame,new LayoutParams(-1,-1));
    }
    void finishResize(){if(resizeFrame!=null){removeView(resizeFrame);resizeFrame=null;}}

    void resize(JSONObject j){if(!"app".equals(j.optString("type"))){beginResize(j);return;}LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);NumberPicker w=new NumberPicker(activity),h=new NumberPicker(activity);w.setMinValue(1);w.setMaxValue(columns);w.setValue(j.optInt("w",1));h.setMinValue(1);h.setMaxValue(rows);h.setValue(j.optInt("h",1));box.addView(text("Columns / rows"));box.addView(w);box.addView(h);new AlertDialog.Builder(activity).setTitle("Item size").setView(box).setPositiveButton("Apply",(d,n)->{if(free(page,j.optInt("x"),j.optInt("y"),w.getValue(),h.getValue(),j)){put(j,"w",w.getValue());put(j,"h",h.getValue());save();render();}else toast("Not enough empty space");}).setNegativeButton("Cancel",null).show();}
    AlertDialog menu(){
        return new AlertDialog.Builder(activity).setTitle("Home")
                .setItems(new String[]{"Add Widget","Manage Pages","Choose Wallpaper","Sense Settings","Android Settings"},(dialog,index)->{
                    switch(index){
                        case 0->activity.pickWidget();
                        case 1->showPageOverview();
                        case 2->activity.open(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER),"Choose wallpaper"));
                        case 3->activity.openLauncherSettings("desktop");
                        case 4->activity.open(new Intent(android.provider.Settings.ACTION_SETTINGS));
                    }
                }).show();
    }
    void showPageOverview() {
        if (pageAnimator != null) pageAnimator.end();
        if(pageOverview==null || !pageOverview.isShowing()){pageOverview=new PageOverview(this);pageOverview.show();}
    }

    void movePage(int from, int to) {
        if (from < 1 || from > pages || to < 1 || to > pages || from == to) return;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            put(item, "page", reorderedPage(item.optInt("page"), from, to));
        }
        page = reorderedPage(page, from, to);
        save();
        render();
    }

    static int reorderedPage(int value, int from, int to) {
        if (value == from) return to;
        if (from < to && value > from && value <= to) return value - 1;
        if (from > to && value >= to && value < from) return value + 1;
        return value;
    }

    void removePage(int index) {
        if (pages <= 1 || index < 1 || index > pages) return;
        JSONArray remaining = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            int oldPage = item.optInt("page");
            if (oldPage == index) {
                if ("widget".equals(item.optString("type"))) host.deleteAppWidgetId(item.optInt("id"));
            } else {
                if (oldPage > index) put(item, "page", oldPage - 1);
                remaining.put(item);
            }
        }
        items = remaining;
        pages--;
        page = page == index ? Math.min(index, pages) : page > index ? page - 1 : page;
        save();
        render();
    }

    void manage(){String[] names=new String[items.length()];for(int i=0;i<names.length;i++){JSONObject j=items.optJSONObject(i);names[i]="Page "+j.optInt("page")+": "+j.optString("type")+" "+j.optString("component",String.valueOf(j.optInt("id")));}new AlertDialog.Builder(activity).setTitle("Select an item").setItems(names,(d,n)->{JSONObject j=items.optJSONObject(n);page=j.optInt("page");render();new AlertDialog.Builder(activity).setItems(new String[]{"Resize","Remove"},(a,b)->{if(b==0)resize(j);else remove(j);}).show();}).show();}
    void gridSettings(){activity.openLauncherSettings("desktop");}
    void reloadSavedSettings(){
        int savedColumns=prefs.getInt("columns",4),savedRows=prefs.getInt("rows",6),savedPages=prefs.getInt("pages",3);
        String savedItems=prefs.getString("items","[]");
        if(savedColumns==columns && savedRows==rows && savedPages==pages && savedItems.equals(items.toString()))return;
        int savedPage=prefs.getInt("currentPage",page);
        if(pageAnimator!=null)pageAnimator.end();finishResize();
        columns=savedColumns;rows=savedRows;pages=Math.max(1,savedPages);
        try{items=new JSONArray(savedItems);}catch(JSONException error){items=new JSONArray();}
        page=Math.max(1,Math.min(pages,savedPage));render();
    }
    void toast(String s){Toast.makeText(activity,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        for(int i=0;i<getChildCount();i++){View v=getChildAt(i);if(v.getTag() instanceof JSONObject j){int x=j.optInt("x")*getWidth()/columns,y=j.optInt("y")*getHeight()/rows,w=j.optInt("w",1)*getWidth()/columns,h=j.optInt("h",1)*getHeight()/rows;v.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));v.layout(x,y,x+w,y+h);}else{int h=v instanceof SenseClockView?Math.min(dp(230),getHeight()):getHeight();v.measure(MeasureSpec.makeMeasureSpec(getWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));v.layout(0,0,getWidth(),h);}}
    }
    /** Adapted carousel: the ROM's Page_template_* quaternion tracks span -30 to +30 degrees.
     * Duration/easing and perspective are modern choices; this does not emulate Fusion's scene graph.
     */
    void refreshDiscover() {
        if(page!=0)return;
        if(activity.discover()!=null && activity.discover().isReady()){page=1;render();activity.discover().open();}
        else render();
    }
    private boolean beginDiscoverGesture(float x,float y) {
        DiscoverOverlay discover=activity.discover();
        if(discoverGesture)return true;
        if(dragging || resizeFrame!=null || pageAnimator!=null || page!=1 || discover==null || !discover.isReady())return false;
        float dx=x-downX,dy=y-downY;
        if(dx>ViewConfiguration.get(activity).getScaledTouchSlop() && dx>Math.abs(dy)){
            cancelLongPress();discoverGesture=discover.beginScroll();
        }
        return discoverGesture;
    }
    private void preparePageSwipe(int target){
        departingViews.clear();
        for(int i=0;i<getChildCount();i++)departingViews.add(getChildAt(i));
        // Record drawing commands rather than allocating, rasterizing and uploading
        // a full-screen ARGB bitmap on the first intercepted move.
        outgoingPage=new android.graphics.Picture();
        android.graphics.Canvas recording=outgoingPage.beginRecording(getWidth(),getHeight());
        try{super.dispatchDraw(recording);}finally{outgoingPage.endRecording();}
        pageSwipeFrom=page;transitionDirection=Integer.signum(target-page);pageGesture=true;
        gestureStartProgress=0;page=target;render();transitionProgress=0;
    }
    private void restoreDepartingViews(){
        removeAllViews();resizeFrame=null;
        for(View view:departingViews)addView(view);
        departingViews.clear();requestLayout();
    }
    private boolean beginPageSwipe(float dx){
        if(dragging || resizeFrame!=null || pageAnimator!=null || getWidth()==0 || getHeight()==0)return false;
        int target=Math.max(0,Math.min(pages,page+(dx<0?1:-1)));
        if(target==page)return false;
        if(target==0 && beginDiscoverGesture(downX+dx,downY))return true;
        preparePageSwipe(target);return true;
    }
    private void updatePageSwipe(float dx){
        if(!pageGesture)return;
        float effective=dx-transitionDirection*gestureStartProgress*getWidth();
        if(effective*transitionDirection>0){
            touchRawX+=transitionDirection*gestureStartProgress*getWidth();
            page=pageSwipeFrom;pageGesture=false;outgoingPage=null;restoreDepartingViews();
            if(!beginPageSwipe(effective)){activity.updatePage(page,pages);invalidate();return;}
            if(discoverGesture){activity.discover().scroll(effective/Math.max(1,getWidth()));return;}
        }
        transitionProgress=Math.min(1,Math.abs(effective)/Math.max(1,getWidth()));
        activity.updatePage(pageSwipeFrom+transitionDirection*transitionProgress,pages);postInvalidateOnAnimation();
    }
    private void settlePageSwipe(boolean commit){settlePageSwipe(commit,0);}
    private void settlePageSwipe(boolean commit,float velocityX){
        if(!pageGesture)return;
        pageGesture=false;
        int destination=commit?page:pageSwipeFrom;
        Runnable finish=()->{
            pageAnimator=null;transitionProgress=1;outgoingPage=null;
            if(destination!=page){page=destination;restoreDepartingViews();}
            else departingViews.clear();
            prefs.edit().putInt("currentPage",page).apply();
            activity.updatePage(page,pages);invalidate();
        };
        if(!android.animation.ValueAnimator.areAnimatorsEnabled()){finish.run();return;}
        float end=commit?1:0,distance=end-transitionProgress;
        if(Math.abs(distance)<.0001f){finish.run();return;}
        float velocity=-velocityX*transitionDirection/Math.max(1,getWidth());
        long duration=PageSettleMotion.duration(distance,velocity);
        float slope=PageSettleMotion.slope(distance,velocity,duration);
        pageAnimator=android.animation.ValueAnimator.ofFloat(transitionProgress,end);
        pageAnimator.setDuration(duration);
        pageAnimator.setInterpolator(t->PageSettleMotion.interpolate(t,slope));
        pageAnimator.addUpdateListener(animation->{transitionProgress=(float)animation.getAnimatedValue();activity.updatePage(pageSwipeFrom+transitionDirection*transitionProgress,pages);postInvalidateOnAnimation();});
        pageAnimator.addListener(new android.animation.AnimatorListenerAdapter(){
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation){cancelled=true;}
            @Override public void onAnimationEnd(android.animation.Animator animation){if(!cancelled)finish.run();}
        });
        pageAnimator.start();
    }
    private void turnTo(int target){
        if(target==0 && activity.discover()!=null && activity.discover().isReady()){activity.discover().open();return;}
        if(target==page || pageAnimator!=null || pageGesture)return;
        if(getWidth()==0 || getHeight()==0){page=target;render();return;}
        preparePageSwipe(target);settlePageSwipe(true);
    }
    private void transformPage(android.graphics.Canvas canvas,float x,float yaw) {
        pageCamera.save();
        pageCamera.setLocation(0,0,-8*getResources().getDisplayMetrics().density);
        pageCamera.rotateY(yaw);
        pageCamera.getMatrix(pageMatrix);
        pageCamera.restore();
        pageMatrix.preTranslate(-getWidth()/2f,-getHeight()/2f);
        pageMatrix.postTranslate(getWidth()/2f+x,getHeight()/2f);
        canvas.concat(pageMatrix);
    }
    @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
        if(outgoingPage==null){super.dispatchDraw(canvas);drawPlacementGrid(canvas);return;}
        int saved=canvas.save();
        transformPage(canvas,-transitionDirection*getWidth()*transitionProgress,-transitionDirection*30f*transitionProgress);
        canvas.drawPicture(outgoingPage);
        canvas.restoreToCount(saved);
        saved=canvas.save();
        transformPage(canvas,transitionDirection*getWidth()*(1-transitionProgress),transitionDirection*30f*(1-transitionProgress));
        super.dispatchDraw(canvas);
        canvas.restoreToCount(saved);
        drawPlacementGrid(canvas);
    }
    private void updateDropTarget(float x, float y) {
        dragX=x;dragY=y;
        // Snap the full footprint inside the grid, using exactly the same target for preview and drop.
        dropColumn=Math.max(0,Math.min(columns-dragColumns,(int)(x*columns/Math.max(1,getWidth()))));
        dropRow=Math.max(0,Math.min(rows-dragRows,(int)(y*rows/Math.max(1,getHeight()))));
        invalidate();
    }

    private void drawPlacementGrid(android.graphics.Canvas canvas) {
        if(!dragging || page==0) return;
        float cellWidth=getWidth()/(float)columns, cellHeight=getHeight()/(float)rows;
        gridPaint.setStyle(android.graphics.Paint.Style.FILL);
        gridPaint.setColor(0x28101818);
        canvas.drawRect(0,0,getWidth(),getHeight(),gridPaint);
        gridPaint.setStyle(android.graphics.Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setColor(0x70ffffff);
        for(int x=0;x<columns;x++)for(int y=0;y<rows;y++) {
            canvas.drawRoundRect(x*cellWidth+dp(3),y*cellHeight+dp(3),
                    (x+1)*cellWidth-dp(3),(y+1)*cellHeight-dp(3),dp(6),dp(6),gridPaint);
        }
        if(dropColumn<0) return;
        boolean available=free(page,dropColumn,dropRow,dragColumns,dragRows,draggedItem);
        dropBounds.set(dropColumn*cellWidth+dp(3),dropRow*cellHeight+dp(3),
                (dropColumn+dragColumns)*cellWidth-dp(3),(dropRow+dragRows)*cellHeight-dp(3));
        dropPaint.setStyle(android.graphics.Paint.Style.FILL);
        dropPaint.setColor(available ? 0x607eb941 : 0x60ef665e);
        canvas.drawRoundRect(dropBounds,dp(8),dp(8),dropPaint);
        dropPaint.setStyle(android.graphics.Paint.Style.STROKE);
        dropPaint.setStrokeWidth(dp(2));
        dropPaint.setColor(available ? 0xffb4e477 : 0xffff9188);
        canvas.drawRoundRect(dropBounds,dp(8),dp(8),dropPaint);
        String size=dragColumns+" × "+dragRows+(available ? "" : " · Occupied");
        dropPaint.setStyle(android.graphics.Paint.Style.FILL);
        dropPaint.setTextSize(dp(14));
        dropPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        float labelWidth=dropPaint.measureText(size)+dp(20);
        float left=Math.max(dp(4),Math.min(getWidth()-labelWidth-dp(4),dropBounds.centerX()-labelWidth/2));
        float top=dropBounds.top>=dp(38) ? dropBounds.top-dp(34) : dropBounds.top+dp(6);
        dropPaint.setColor(0xee172320);
        canvas.drawRoundRect(left,top,left+labelWidth,top+dp(28),dp(7),dp(7),dropPaint);
        dropPaint.setColor(Color.WHITE);
        canvas.drawText(size,left+dp(10),top+dp(19),dropPaint);
    }
    @Override protected void onDetachedFromWindow(){
        activity.setHomeDragging(this,false);
        releaseSwipeVelocity();
        if(pageAnimator!=null){pageAnimator.cancel();pageAnimator=null;pageGesture=true;}
        if(pageGesture){page=pageSwipeFrom;pageGesture=false;outgoingPage=null;restoreDepartingViews();prefs.edit().putInt("currentPage",page).apply();}
        handler.removeCallbacks(edgeTurn);
        super.onDetachedFromWindow();
    }
    private void releaseSwipeVelocity(){if(swipeVelocity!=null){swipeVelocity.recycle();swipeVelocity=null;}}
    private void startTouch(MotionEvent event){
        if(pageAnimator!=null){
            pageAnimator.cancel();pageAnimator=null;pageGesture=true;
            gestureStartProgress=transitionProgress;
        }
        if(resizeFrame!=null && !resizeFrame.contains(event.getX(),event.getY()))finishResize();
        downX=event.getX();downY=event.getY();touchRawX=event.getRawX();touchRawY=event.getRawY();
        discoverDownRawX=event.getRawX();discoverGesture=false;desktopGesture=false;
        verticalEligible=resizeFrame==null && !dragging;
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);
            if(child.getTag() instanceof JSONObject item && !"app".equals(item.optString("type")) && downX>=child.getLeft() && downX<child.getRight() && downY>=child.getTop() && downY<child.getBottom())verticalEligible=false;
        }
        releaseSwipeVelocity();swipeVelocity=VelocityTracker.obtain();swipeVelocity.addMovement(event);
    }
    private boolean claimSwipe(MotionEvent event){
        if(dragging || resizeFrame!=null)return false;
        if(pageGesture || desktopGesture || discoverGesture)return true;
        float dx=event.getRawX()-touchRawX,dy=event.getRawY()-touchRawY;
        int slop=ViewConfiguration.get(activity).getScaledTouchSlop()*2;
        if(Math.abs(dx)>slop && Math.abs(dx)>Math.abs(dy)*1.3f){
            cancelLongPress();
            if(beginDiscoverGesture(event.getX(),event.getY()))return true;
            return beginPageSwipe(dx);
        }
        if(verticalEligible && Math.abs(dy)>slop && Math.abs(dy)>Math.abs(dx)*1.3f){cancelLongPress();desktopGesture=true;return true;}
        return false;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){pinchSpan=0;pinching=false;}
        if(action==MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount()==2 && !dragging && resizeFrame==null && page>0){
            pinchSpan=(float)Math.hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1));
            cancelLongPress();for(int i=0;i<getChildCount();i++)getChildAt(i).cancelLongPress();
        }
        if(pinchSpan>0 && action==MotionEvent.ACTION_MOVE && event.getPointerCount()==2){
            float span=(float)Math.hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1));
            if(!pinching && pinchSpan-span>ViewConfiguration.get(activity).getScaledTouchSlop()*2){
                MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();
                if(pageAnimator!=null)pageAnimator.end();
                pinching=true;pageOverview=new PageOverview(this);pageOverview.show(true);
            }
            if(pinching){pageOverview.setGestureProgress((1-span/pinchSpan)/.55f);return true;}
        }
        if(pinching && (action==MotionEvent.ACTION_POINTER_UP || action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL)){
            pageOverview.finishGesture(action==MotionEvent.ACTION_CANCEL);pinching=false;pinchSpan=0;return true;
        }
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL)pinchSpan=0;
        if(pinching)return true;
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){boolean settling=pageAnimator!=null;startTouch(event);if(settling)return true;}
        if(pageAnimator!=null)return true;
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE)return claimSwipe(event);
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(pageAnimator!=null)return true;
        boolean handled=super.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startTouch(event);if(pageGesture)cancelLongPress();return true;}
        if(swipeVelocity!=null)swipeVelocity.addMovement(event);
        float dx=event.getRawX()-touchRawX,dy=event.getRawY()-touchRawY;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_MOVE -> {
                if(claimSwipe(event)){
                    if(discoverGesture)activity.discover().scroll(dx/Math.max(1,getWidth()));
                    else if(pageGesture)updatePageSwipe(dx);
                }
                if(Math.abs(dx)>ViewConfiguration.get(activity).getScaledTouchSlop() || Math.abs(dy)>ViewConfiguration.get(activity).getScaledTouchSlop())cancelLongPress();
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                float speed=0;if(swipeVelocity!=null){swipeVelocity.computeCurrentVelocity(1000);speed=swipeVelocity.getXVelocity();}
                if(discoverGesture){activity.discover().scroll(dx/Math.max(1,getWidth()));activity.discover().endScroll();discoverGesture=false;}
                else if(pageGesture)settlePageSwipe(transitionProgress>.25f || speed*transitionDirection < -dp(600),speed);
                else if(desktopGesture && Math.abs(dy)>dp(48))activity.runDesktopGesture(dy<0);
                desktopGesture=false;releaseSwipeVelocity();return true;
            }
            case MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN -> {
                if(discoverGesture){activity.discover().scroll(0);activity.discover().endScroll();activity.discover().close();discoverGesture=false;}
                if(pageGesture)settlePageSwipe(false);
                desktopGesture=false;verticalEligible=false;releaseSwipeVelocity();cancelLongPress();return true;
            }
            default -> {return handled;}
        }
    }
}
