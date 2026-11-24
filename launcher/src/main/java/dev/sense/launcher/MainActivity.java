package dev.sense.launcher;

import dev.sense.framework.SenseTabBar;
import dev.sense.framework.SenseHeaderBar;
import android.app.Activity;
import dev.sense.launcher.ui.SenseAppIcon;
import dev.sense.launcher.ui.SenseAppRow;

import dev.sense.launcher.ui.SenseDrawerLayout;

import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.*;
import android.window.OnBackInvokedDispatcher;
import java.util.List;

/** Native launcher; local ROM artwork is prepared by the build. */
public final class MainActivity extends Activity {
    private final DrawerAppMenu drawerAppMenu=new DrawerAppMenu(this);
    private LinearLayout root;
    private FrameLayout stage;
    private FullscreenWeather fullscreenWeather;
    private LinearLayout homeSurface;
    private String pendingSettingsAction,pendingSettingsItem;
    private View dockPlaceholder;
    private FrameLayout foregroundDock;
    private AppCatalog appCatalog;
    private FrameLayout drawerContent;
    private static final int DRAWER_INDEX_WIDTH_DP=28;
    private record DrawerViews(LinearLayout row,ScrollView scroll,java.util.List<AppCatalog.Entry> catalog,long usageVersion){}
    private final java.util.Map<String,DrawerViews> drawerViews=new java.util.HashMap<>();
    private long usageVersion;
    private int warmGeneration;
    private volatile int drawerGeneration;
    private final java.util.concurrent.ExecutorService drawerWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable catalogChanged=this::onCatalogChanged;
    private boolean drawer;
    private boolean frequentApps;
    private android.appwidget.AppWidgetHost widgetHost;
    private Workspace workspace;
    private DiscoverOverlay discover;
    private BentDockLayout homeDock;
    private HtcDockMotion dockMotion;
    private DockTransitions dockTransitions;
    private boolean overviewVisible;
    private LinearLayout homeRemoveTarget,removeVisual;
    private ImageView removeIcon;
    private TextView removeLabel;
    private Workspace dragWorkspace;
    private int pendingWidget = -1;
    private static final int PICK_WIDGET=40, BIND_WIDGET=41, CONFIG_WIDGET=42;
    private SenseDrawerLayout backDrawer;
    private final android.window.OnBackAnimationCallback back=new android.window.OnBackAnimationCallback(){
        @Override public void onBackStarted(android.window.BackEvent event){
            if(drawerAppMenu.isShowing()){backDrawer=null;return;}
            backDrawer=drawer && root instanceof SenseDrawerLayout panel?panel:null;
            if(backDrawer!=null)backDrawer.beginBack();
        }
        @Override public void onBackProgressed(android.window.BackEvent event){
            if(backDrawer!=null && drawer && root==backDrawer)backDrawer.setBackProgress(event.getProgress());
        }
        @Override public void onBackCancelled(){
            if(backDrawer!=null && drawer && root==backDrawer)backDrawer.cancelBack();
            backDrawer=null;
        }
        @Override public void onBackInvoked(){
            backDrawer=null;
            if(drawerAppMenu.dismiss())return;
            if(drawer)closeDrawer();else showHome();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        readSettingsAction(getIntent());
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, back);
        widgetHost = new SenseWidgetHost(this, 1024);
        pendingWidget = state == null ? -1 : state.getInt("pendingWidget", -1);
        discover=new DiscoverOverlay(this,new DiscoverOverlay.Listener() {
            @Override public void onDiscoverProgress(float progress) {
                if(root!=null && !drawer)root.setTranslationX(progress*root.getWidth());
            }
            @Override public void onDiscoverAvailabilityChanged() {
                if(workspace!=null && !drawer)workspace.refreshDiscover();
            }
        });
        dockMotion=new HtcDockMotion(this);
        appCatalog=AppCatalog.get(this);appCatalog.addListener(catalogChanged);
        showHome();
        applyNavigationBarVisibility();
        fullscreenWeather=new FullscreenWeather(this,stage,()->!drawer && !overviewVisible && root==homeSurface && workspace!=null && workspace.page>0);
        warmDrawer();
    }

    DiscoverOverlay discover(){return discover;}

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readSettingsAction(intent);
        showHome();
        if(fullscreenWeather!=null && pendingSettingsAction==null)fullscreenWeather.enterHome();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void surface(boolean transition) {
        if(fullscreenWeather!=null)fullscreenWeather.stop();
        drawerGeneration++;
        LinearLayout previous=root;
        if(previous!=null)previous.animate().cancel();
        root = drawer ? new SenseDrawerLayout(this) : new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(drawer ? 0xff1e1e1e : Color.TRANSPARENT);
        boolean home = !drawer;
        if(home)homeSurface=root;
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            var mandatory=insets.getInsets(WindowInsets.Type.mandatorySystemGestures());
            int bottomSafe=WorkspaceSettings.hideNavigationBar(getSharedPreferences("workspace",0))?insets.getInsets(WindowInsets.Type.displayCutout()).bottom:Math.max(bars.bottom,mandatory.bottom);
            if (home) {
                v.setPadding(0, bars.top + dp(12), 0, 0);
                if(homeDock!=null)homeDock.setSystemInsets(bars.left,bars.right,bottomSafe);
                if(removeVisual!=null)removeVisual.setPadding(bars.left,0,bars.right,bottomSafe);
            } else {
                v.setBackground(new android.graphics.drawable.InsetDrawable(
                        new android.graphics.drawable.ColorDrawable(0xff1e1e1e),0,bars.top,0,0));
                // InsetDrawable supplies padding too: apply safe layout padding after the background.
                v.setPadding(bars.left,bars.top,bars.right,bottomSafe+dp(4));
                LinearLayout drawerPanel=(LinearLayout)v;
                for(int i=0;i<drawerPanel.getChildCount();i++)
                    if(drawerPanel.getChildAt(i) instanceof SenseHeaderBar header)header.setTopInset(0);
            }
            return insets;
        });
        if(stage==null) {
            stage=new FrameLayout(this);
            setContentView(stage);
        }
        LinearLayout panel=root;
        if(transition && previous!=null && previous.getParent()==stage) {
            for(int i=stage.getChildCount()-1;i>=0;i--) {
                View child=stage.getChildAt(i);
                if(child!=previous)stage.removeView(child);
            }
            if(drawer) {
                stage.addView(panel,new FrameLayout.LayoutParams(-1,-1));
                liftDockAboveDrawer();
                DockTransitions transitions=dockTransitions;
                ((SenseDrawerLayout)panel).setMotion(t->dockMotion.sample("slide_off","body",t),t->dockMotion.sample("slide_on","body",t),dockMotion.duration("slide_off"),progress->{if(transitions!=null)transitions.drawerProgress(progress);});
                ((SenseDrawerLayout)panel).reveal(()->{});
            } else {
                stage.addView(panel,0,new FrameLayout.LayoutParams(-1,-1));
                ((SenseDrawerLayout)previous).conceal(()->stage.removeView(previous));
            }
        } else {
            stage.removeAllViews();
            if(drawer && homeSurface!=null)stage.addView(homeSurface,new FrameLayout.LayoutParams(-1,-1));
            stage.addView(panel,new FrameLayout.LayoutParams(-1,-1));
            if(drawer && dockTransitions!=null){
                liftDockAboveDrawer();
                DockTransitions transitions=dockTransitions;
                ((SenseDrawerLayout)panel).setMotion(t->dockMotion.sample("slide_off","body",t),t->dockMotion.sample("slide_on","body",t),dockMotion.duration("slide_off"),transitions::drawerProgress);
                transitions.drawerProgress(1);
            }
        }
        root.requestApplyInsets();
    }

    private TextView label(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(Color.WHITE);
        text.setTextSize(size);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private Button button(String value, Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private DockButton dockButton(String title, Runnable action) {
        DockButton control = new DockButton(this);
        control.setText(title);
        control.setOnClickListener(v -> action.run());
        control.setTextColor(Color.WHITE);
        control.setAllCaps(false);
        control.setTextSize(16);
        control.setMinWidth(0);
        control.setMinimumWidth(0);
        control.setMinHeight(0);
        control.setMinimumHeight(0);
        control.setPadding(dp(8), 0, dp(8), 0);
        control.setSingleLine(true);
        control.setEllipsize(android.text.TextUtils.TruncateAt.END);
        GradientDrawable face = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xe56b7373, 0xe52b3333, 0xe5121919});
        face.setCornerRadius(dp(6));
        face.setStroke(dp(1), 0x998f9998);
        GradientDrawable rippleMask = new GradientDrawable();
        rippleMask.setColor(Color.WHITE);
        rippleMask.setCornerRadius(dp(6));
        control.setSurfaceDrawable(new RippleDrawable(ColorStateList.valueOf(0x44ffffff), face, rippleMask));
        int iconResource = switch(title) {
            case "Apps" -> R.drawable.sense_dock_apps;
            case "Phone" -> R.drawable.sense_dock_phone;
            case "Settings" -> R.drawable.sense_dock_settings;
            default -> 0;
        };
        if(iconResource != 0) {
            var icon=getDrawable(iconResource);
            icon.setBounds(0,0,dp(20),dp(20));
            control.setCompoundDrawablePadding(dp(5));
            control.setContentDescription(title);
            control.setTextSize("Phone".equals(title) ? 16 : 14);
            control.setPadding(dp(5),0,dp(5),0);
            if (!"Phone".equals(title)) {
                control.setText("");
                control.setCompoundDrawablePadding(0);
                icon.setBounds(0,0,dp(24),dp(24));
            }
            control.setCompoundDrawablesRelative(icon,null,null,null);
        }
        return control;
    }

    private LinearLayout.LayoutParams dockButtonParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), weight);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN && fullscreenWeather!=null)fullscreenWeather.stop();
        return super.dispatchTouchEvent(event);
    }
    private void applyNavigationBarVisibility(){if(stage==null)return;WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){if(WorkspaceSettings.hideNavigationBar(getSharedPreferences("workspace",0))){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.navigationBars());}else{controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);controller.show(WindowInsets.Type.navigationBars());}}if(root!=null)root.requestApplyInsets();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){applyNavigationBarVisibility();if(fullscreenWeather!=null)fullscreenWeather.focus();}}
    FullscreenWeather fullscreenWeather(){return fullscreenWeather;}
    @Override public boolean onKeyDown(int keyCode,android.view.KeyEvent event){
        if(keyCode==android.view.KeyEvent.KEYCODE_BACK && drawer){event.startTracking();return true;}
        return super.onKeyDown(keyCode,event);
    }
    @Override public boolean onKeyUp(int keyCode,android.view.KeyEvent event){
        if(keyCode==android.view.KeyEvent.KEYCODE_BACK && drawer){if(!event.isCanceled())closeDrawer();return true;}
        return super.onKeyUp(keyCode,event);
    }
    @Override public void onBackPressed(){if(drawerAppMenu.dismiss())return;if(drawer)closeDrawer();else showHome();}
    void closeDrawer(){
        if(!drawer || !(root instanceof SenseDrawerLayout closing))return;
        if(homeSurface==null){showHome();return;}
        drawer=false;drawerGeneration++;backDrawer=null;
        root=homeSurface;
        if(root.getParent()!=stage)stage.addView(root,0,new FrameLayout.LayoutParams(-1,-1));
        root.setAlpha(1);root.setTranslationX(0);root.requestApplyInsets();
        Runnable finished=()->{stage.removeView(closing);if(!drawer){resetDrawerScroll();restoreDockParent();warmDrawer();if(fullscreenWeather!=null)fullscreenWeather.enterHome();}};
        if(closing.getHeight()==0)finished.run();
        else closing.conceal(finished);
    }
    private void liftDockAboveDrawer(){
        if(foregroundDock==null && homeDock!=null && homeDock.getParent() instanceof FrameLayout slot
                && slot.getParent()==homeSurface){
            int index=homeSurface.indexOfChild(slot);
            dockPlaceholder=new View(this);
            int height=Math.max(slot.getHeight(),slot.getMeasuredHeight());
            homeSurface.removeView(slot);
            homeSurface.addView(dockPlaceholder,index,new LinearLayout.LayoutParams(-1,height));
            foregroundDock=slot;
            stage.addView(slot,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        }
        if(foregroundDock!=null){
            if(foregroundDock.getParent()==null)stage.addView(foregroundDock);
            foregroundDock.bringToFront();
        }
    }
    private void restoreDockParent(){
        if(foregroundDock==null)return;
        FrameLayout slot=foregroundDock;foregroundDock=null;
        stage.removeView(slot);
        int index=homeSurface.indexOfChild(dockPlaceholder);
        homeSurface.removeView(dockPlaceholder);dockPlaceholder=null;
        homeSurface.addView(slot,Math.max(0,index),new LinearLayout.LayoutParams(-1,-2));
    }
    private void showHome() { showHome(true); }

    private void showHome(boolean animate) {
        drawerAppMenu.dismiss();
        if(animate && drawer && homeSurface!=null){closeDrawer();return;}
        restoreDockParent();
        if(dockTransitions!=null)dockTransitions.destroy();
        if(discover!=null)discover.close();
        boolean closing=drawer && root instanceof SenseDrawerLayout;
        drawer = false;
        surface(animate && closing);
        workspace = new Workspace(this, widgetHost);
        root.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1));
        RoleManager roles = getSystemService(RoleManager.class);
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            root.addView(button("Set as home app", () -> startActivityForResult(
                    roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1)));
        }
        BentDockLayout dock = new BentDockLayout(this);
        homeDock = dock;
        dock.setGravity(Gravity.BOTTOM);
        dock.setPageIndicator(Math.max(0, workspace.page - 1), workspace.pages);
        dock.addView(dockButton("Apps", this::showApps), dockButtonParams(1));
        dock.addView(dockButton("Phone", this::openSensePhone), dockButtonParams(2));
        dock.addView(dockButton("Settings", () -> workspace.menu()), dockButtonParams(1));
        FrameLayout dockSlot=new FrameLayout(this);
        dockSlot.addView(dock,new FrameLayout.LayoutParams(-1,-2));
        homeRemoveTarget=new LinearLayout(this);
        homeRemoveTarget.setOrientation(LinearLayout.VERTICAL);homeRemoveTarget.setGravity(Gravity.CENTER);
        homeRemoveTarget.setVisibility(View.INVISIBLE);
        homeRemoveTarget.setContentDescription("Remove from home screen. Drop the icon or widget here.");
        removeVisual=new LinearLayout(this);removeVisual.setOrientation(LinearLayout.VERTICAL);removeVisual.setGravity(Gravity.CENTER);
        homeRemoveTarget.addView(removeVisual,new LinearLayout.LayoutParams(-1,-1));
        removeIcon=new ImageView(this);removeIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        removeVisual.addView(removeIcon,new LinearLayout.LayoutParams(dp(30),dp(27)));
        removeLabel=label("Remove",16);removeLabel.setIncludeFontPadding(false);
        LinearLayout.LayoutParams removeText=new LinearLayout.LayoutParams(-2,-2);removeText.topMargin=dp(3);
        removeVisual.addView(removeLabel,removeText);
        homeRemoveTarget.setOnDragListener((view,event)->{
            if(event.getAction()==android.view.DragEvent.ACTION_DRAG_STARTED)
                return dragWorkspace!=null && event.getClipDescription()!=null && event.getClipDescription().hasMimeType("text/vnd.sense.app");
            switch(event.getAction()){
                case android.view.DragEvent.ACTION_DRAG_ENTERED,android.view.DragEvent.ACTION_DRAG_LOCATION -> setRemoveHovered(true);
                case android.view.DragEvent.ACTION_DRAG_EXITED -> setRemoveHovered(false);
                case android.view.DragEvent.ACTION_DRAG_ENDED -> {if(dragWorkspace!=null)setHomeDragging(dragWorkspace,false);}
                case android.view.DragEvent.ACTION_DROP -> {
                    if(dragWorkspace!=null && event.getLocalState() instanceof org.json.JSONObject item)dragWorkspace.removeDraggedItem(item);
                    return true;
                }
            }
            return true;
        });
        setRemoveHovered(false);
        dockSlot.addView(homeRemoveTarget,new FrameLayout.LayoutParams(-1,-1));
        root.addView(dockSlot, new LinearLayout.LayoutParams(-1, -2));
        dockTransitions=new DockTransitions(dock,homeRemoveTarget,removeVisual,dockMotion);
        LinearLayout home=root;boolean automatic=pendingSettingsAction==null;
        home.post(()->{if(automatic && root==home && !drawer && fullscreenWeather!=null)fullscreenWeather.enterHome();});
    }

    void setHomeDragging(Workspace owner,boolean dragging){
        if(homeDock==null || homeRemoveTarget==null)return;
        if(dragging){
            dragWorkspace=owner;
            setRemoveHovered(false);dockTransitions.mode(true,true);
            homeRemoveTarget.announceForAccessibility("Drag to Remove at the bottom to remove from the home screen");
        }else if(dragWorkspace==owner){
            dragWorkspace=null;dockTransitions.mode(drawer || overviewVisible,false);
        }
    }
    private void setRemoveHovered(boolean hovered){
        if(homeRemoveTarget==null)return;
        homeRemoveTarget.setActivated(hovered);
        removeVisual.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{hovered?0xb0300905:0x50121414,hovered?0xffa52a22:0xf0181b1b}));
        removeIcon.setImageResource(hovered?R.drawable.sense_remove_over:R.drawable.sense_remove_out);
        removeLabel.setText("Remove");removeLabel.setTextColor(hovered?0xffffd4d0:Color.WHITE);
    }

    private void onCatalogChanged(){
        drawerViews.clear();
        if(drawer && !isDestroyed() && root instanceof SenseDrawerLayout panel && drawerContent!=null)
            requestDrawerContents(panel,drawerContent,getSharedPreferences("drawer",0).getBoolean("listMode",false),frequentApps);
        warmDrawer();
    }
    private void resetDrawerScroll(){
        for(DrawerViews views:drawerViews.values()){
            views.scroll().clearFocus();views.scroll().scrollTo(0,0);views.scroll().fling(0);
        }
    }
    void showApps() {
        drawerAppMenu.dismiss();
        if(discover!=null)discover.close();
        boolean opening=!drawer;
        if(opening)resetDrawerScroll();
        drawer=true;surface(opening);
        SenseDrawerLayout panel=(SenseDrawerLayout)root;
        panel.setOnPullDown(this::closeDrawer);
        var preferences=getSharedPreferences("drawer",0);
        boolean listMode=preferences.getBoolean("listMode",false);
        SenseHeaderBar header=new SenseHeaderBar(this);
        header.setTitle(frequentApps?"Frequent":"All apps");
        header.setIconAction(listMode?dev.sense.framework.R.drawable.sense_common_icon_glance_grid_rest:dev.sense.framework.R.drawable.sense_common_icon_glance_list_view_rest,listMode?dev.sense.framework.R.drawable.sense_common_icon_glance_grid_on:dev.sense.framework.R.drawable.sense_common_icon_glance_list_view_on,listMode?"Switch to grid view":"Switch to list view",()->{preferences.edit().putBoolean("listMode",!listMode).apply();showApps();});
        header.setNavigationAction("Close app drawer",this::closeDrawer);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        // The content slot has weight even before the initial scan finishes.
        FrameLayout content=new FrameLayout(this);drawerContent=content;
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        SenseTabBar tabs=new SenseTabBar(this);
        for(boolean frequent:new boolean[]{true,false})tabs.addIconTab(frequent?"Frequent":"All apps",frequent?dev.sense.framework.R.drawable.sense_common_icon_starred_rest:dev.sense.framework.R.drawable.sense_common_icon_my_apps_rest,frequent?dev.sense.framework.R.drawable.sense_common_icon_starred_on:dev.sense.framework.R.drawable.sense_common_icon_my_apps_on,frequentApps==frequent,()->{frequentApps=frequent;showApps();});
        root.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout placeholder=new LinearLayout(this);
        placeholder.addView(label("Loading apps…",18),new LinearLayout.LayoutParams(0,-1,1));
        placeholder.addView(new FrameLayout(this),new LinearLayout.LayoutParams(dp(DRAWER_INDEX_WIDTH_DP),-1));
        content.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        requestDrawerContents(panel,content,listMode,frequentApps);
    }

    private void requestDrawerContents(SenseDrawerLayout panel,FrameLayout content,boolean listMode,boolean frequent){
        if(!appCatalog.loaded())return;
        int generation=++drawerGeneration;
        java.util.List<AppCatalog.Entry> snapshot=appCatalog.entries();
        String key=drawerCacheKey(listMode,frequent);
        long requestedUsage=usageVersion;
        DrawerViews cached=drawerViews.get(key);
        if(cached!=null && cached.catalog()==snapshot && (!frequent || cached.usageVersion()==requestedUsage)){
            if(cached.row().getParent() instanceof android.view.ViewGroup parent)parent.removeView(cached.row());
            content.removeAllViews();content.addView(cached.row(),new FrameLayout.LayoutParams(-1,-1));
            panel.setScrollingContent(cached.scroll());return;
        }
        drawerWorker.execute(()->{
            if(generation!=drawerGeneration)return;
            java.util.List<AppCatalog.Entry> apps=frequent?AppRecency.ordered(this,snapshot):snapshot;
            runOnUiThread(()->{
                if(!isCurrentDrawer(panel,generation))return;
                panel.runWhenIdle(()->{if(isCurrentDrawer(panel,generation))bindDrawerContents(panel,content,listMode,frequent,apps,generation,snapshot,requestedUsage);});
            });
        });
    }
    private boolean isCurrentDrawer(SenseDrawerLayout panel,int generation){
        return !isDestroyed() && drawer && root==panel && drawerGeneration==generation;
    }
    private String drawerCacheKey(boolean listMode,boolean frequent){return (frequent?"frequent":"all")+(listMode?":list":":grid");}
    private void bindDrawerContents(SenseDrawerLayout panel,FrameLayout content,boolean listMode,boolean frequent,java.util.List<AppCatalog.Entry> apps,int generation,java.util.List<AppCatalog.Entry> snapshot,long requestedUsage){
        DrawerViews views=createDrawerViews(listMode,frequent,apps,snapshot,requestedUsage);
        drawerViews.put(drawerCacheKey(listMode,frequent),views);
        content.removeAllViews();content.addView(views.row(),new FrameLayout.LayoutParams(-1,-1));
        panel.setScrollingContent(views.scroll());
    }
    private DrawerViews createDrawerViews(boolean listMode,boolean frequent,java.util.List<AppCatalog.Entry> apps,java.util.List<AppCatalog.Entry> snapshot,long requestedUsage){
        LinearLayout row=new LinearLayout(this);
        ScrollView scroll;
        if(apps.isEmpty()){
            scroll=new ScrollView(this);scroll.setFillViewport(true);
            scroll.addView(label(frequent?"Launch apps from this launcher to build your Frequent list.":"No apps found.",16));
        }else scroll=new DrawerAppViewport(this,apps,listMode,frequent,
                ()->listMode?new SenseAppRow(this):new SenseAppIcon(this),this::bindDrawerIcon);
        row.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
        FrameLayout alphabetSlot=new FrameLayout(this);
        row.addView(alphabetSlot,new LinearLayout.LayoutParams(dp(DRAWER_INDEX_WIDTH_DP),-1));
        if(!frequent && scroll instanceof DrawerAppViewport viewport){
            java.util.LinkedHashMap<String,Integer> sections=new java.util.LinkedHashMap<>();
            for(int i=0;i<apps.size();i++)sections.putIfAbsent(apps.get(i).section(),i);
            java.util.List<Integer> targets=new java.util.ArrayList<>(sections.values());
            alphabetSlot.addView(new dev.sense.launcher.ui.AlphabetIndex(this,new java.util.ArrayList<>(sections.keySet()),index->viewport.jumpTo(targets.get(index))),new FrameLayout.LayoutParams(-1,-1));
        }
        return new DrawerViews(row,scroll,snapshot,requestedUsage);
    }
    /** Prepare the chosen layout while home is idle; never inflate during a reveal. */
    private void warmDrawer(){
        if(isDestroyed() || appCatalog==null || !appCatalog.loaded() || drawer || root==null)return;
        int warming=++warmGeneration;
        var snapshot=appCatalog.entries();long requestedUsage=usageVersion;
        boolean listMode=getSharedPreferences("drawer",0).getBoolean("listMode",false);
        drawerWorker.execute(()->{
            var frequent=AppRecency.ordered(this,snapshot);
            runOnUiThread(()->{
                if(isDestroyed() || drawer || warming!=warmGeneration || snapshot!=appCatalog.entries() || requestedUsage!=usageVersion)return;
                android.os.Looper.myQueue().addIdleHandler(()->{
                    if(isDestroyed() || drawer || warming!=warmGeneration || snapshot!=appCatalog.entries() || requestedUsage!=usageVersion)return false;
                    for(boolean isFrequent:new boolean[]{false,true}){
                        String key=drawerCacheKey(listMode,isFrequent);DrawerViews cached=drawerViews.get(key);
                        if(cached==null || cached.catalog()!=snapshot || (isFrequent && cached.usageVersion()!=requestedUsage))
                            drawerViews.put(key,createDrawerViews(listMode,isFrequent,isFrequent?frequent:snapshot,snapshot,requestedUsage));
                    }
                    return false;
                });
            });
        });
    }
    boolean drawerPrepared(){
        boolean listMode=getSharedPreferences("drawer",0).getBoolean("listMode",false);
        DrawerViews cached=drawerViews.get(drawerCacheKey(listMode,false));
        return cached!=null && cached.catalog()==appCatalog.entries();
    }
    private void bindDrawerIcon(SenseAppIcon icon,AppCatalog.Entry app){
        icon.setApp(app.label(),app.drawable(this));
        icon.setOnClickListener(v->open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)));
        drawerAppMenu.bind(icon,app,()->startDrawerDrag(app),()->{showHome(false);workspace.addShortcut(app.component());});
    }
    private void startDrawerDrag(AppCatalog.Entry app){
            showHome(false);
            workspace.post(()->workspace.startDragAndDrop(new android.content.ClipData("App",new String[]{"text/vnd.sense.app"},new android.content.ClipData.Item(app.component().flattenToString())),new View.DragShadowBuilder(workspace){
                @Override public void onProvideShadowMetrics(android.graphics.Point size,android.graphics.Point touch){size.set(dp(56),dp(56));touch.set(dp(28),dp(28));}
                @Override public void onDrawShadow(android.graphics.Canvas canvas){var image=app.drawable(MainActivity.this);image.setBounds(0,0,dp(56),dp(56));image.draw(canvas);}
            },null,0));
    }
    DrawerAppMenu drawerAppMenu(){return drawerAppMenu;}

    void runDesktopGesture(boolean up){
        switch(DesktopGestures.action(this,up)){
            case 1 -> showApps();
            case 2 -> {
                if(!DesktopGestureService.openNotifications())openLauncherSettings("gestures");
            }
        }
    }
    private void followDisplayRefreshRate(){
        var display=getDisplay();if(display==null)return;
        var mode=display.getMode();float rate=display.getRefreshRate();
        for(var candidate:display.getSupportedModes())if(candidate.getPhysicalWidth()==mode.getPhysicalWidth() && candidate.getPhysicalHeight()==mode.getPhysicalHeight())rate=Math.max(rate,candidate.getRefreshRate());
        var attributes=getWindow().getAttributes();attributes.preferredRefreshRate=rate;getWindow().setAttributes(attributes);
    }

    private void openSensePhone() {
        Intent phone = getPackageManager().getLaunchIntentForPackage("dev.sense.phone");
        open(phone != null ? phone : new Intent(Intent.ACTION_DIAL));
    }

    void open(Intent intent) {
        try {
            startActivity(intent);
            if (Intent.ACTION_MAIN.equals(intent.getAction())
                    && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intent.getComponent() != null) {
                AppRecency.record(this,intent.getComponent());
                usageVersion++;
            }
        }
        catch (android.content.ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, "App unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    HtcDockMotion dockMotion(){return dockMotion;}
    void setPageOverviewVisible(boolean visible){
        overviewVisible=visible;
        if(homeSurface!=null){
            homeSurface.setAlpha(1);
            for(int i=0;i<homeSurface.getChildCount();i++){
                View child=homeSurface.getChildAt(i);
                if(homeDock==null || child!=homeDock.getParent())child.setAlpha(visible?0:1);
            }
        }
        if(dockTransitions!=null)dockTransitions.mode(visible || drawer,false);
    }

    void updatePage(float page, int count) { if(homeDock != null) homeDock.setPageIndicator(Math.max(0, page - 1), count); }

    void openGoogle() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.android.googlequicksearchbox");
        if(intent == null) intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/"));
        open(intent);
    }
    void pickWidget() {
        cancelWidget();
        int columns=workspace==null?4:workspace.columns,rows=workspace==null?6:workspace.rows;
        int width=workspace==null||workspace.getWidth()==0?getResources().getDisplayMetrics().widthPixels:workspace.getWidth();
        int height=workspace==null||workspace.getHeight()==0?getResources().getDisplayMetrics().heightPixels:workspace.getHeight();
        startActivityForResult(new Intent(this,WidgetPickerActivity.class).putExtra("columns",columns).putExtra("rows",rows).putExtra("cellWidth",Math.max(1,width/columns)).putExtra("cellHeight",Math.max(1,height/rows)),PICK_WIDGET);
    }
    private void bindSelectedWidget(android.appwidget.AppWidgetProviderInfo info){
        cancelWidget();pendingWidget=widgetHost.allocateAppWidgetId();
        var manager=android.appwidget.AppWidgetManager.getInstance(this);
        try{
            if(manager.bindAppWidgetIdIfAllowed(pendingWidget,info.getProfile(),info.provider,null))configureWidget();
            else startActivityForResult(new Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidget)
                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider)
                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,info.getProfile()),BIND_WIDGET);
        }catch(RuntimeException error){cancelWidget();android.widget.Toast.makeText(this,"This widget is unavailable",android.widget.Toast.LENGTH_SHORT).show();}
    }

    private void readSettingsAction(Intent intent){
        pendingSettingsAction=intent.getStringExtra("settingsAction");pendingSettingsItem=intent.getStringExtra("settingsItem");
        intent.removeExtra("settingsAction");intent.removeExtra("settingsItem");
    }
    void openLauncherSettings(String section){
        if(workspace!=null)workspace.finishResize();
        if(discover!=null)discover.close();
        startActivity(new Intent(this,LauncherSettingsActivity.class).putExtra("section",section)
                .putExtra("discoverStatus",discover==null?null:discover.status()));
    }
    private void performSettingsAction(String action,String key){
        if(workspace==null)return;
        switch(action){
            case "weatherEffect"->{try{fullscreenWeather.preview(Integer.parseInt(key));}catch(NumberFormatException ignored){}}
            case "widgets"->pickWidget();
            case "pages"->workspace.showPageOverview();
            case "discover"->{if(discover!=null)discover.retry();}
            case "resize"->{
                for(int i=0;i<workspace.items.length();i++){
                    org.json.JSONObject item=workspace.items.optJSONObject(i);
                    if(item!=null && item.toString().equals(key)){
                        workspace.page=Math.max(1,item.optInt("page",1));workspace.render();workspace.post(()->workspace.resize(item));break;
                    }
                }
            }
        }
    }
    void addSearchWidget(){cancelWidget();if(workspace==null || drawer)showHome();workspace.addSearch();}
    void addMusicWidget(int styleRows){cancelWidget();if(workspace==null || drawer)showHome();workspace.addMusic(styleRows);}

    private void cancelWidget() { if(pendingWidget != -1) widgetHost.deleteAppWidgetId(pendingWidget); pendingWidget=-1; }
    private void configureWidget() {
        var manager=android.appwidget.AppWidgetManager.getInstance(this);
        var info=manager.getAppWidgetInfo(pendingWidget);
        if(info == null) { cancelWidget(); return; }
        if(info.configure != null) {
            try { widgetHost.startAppWidgetConfigureActivityForResult(this,pendingWidget,0,CONFIG_WIDGET,null); }
            catch(Exception e) { cancelWidget(); }
        } else finishWidget();
    }
    private void finishWidget() { int id=pendingWidget; pendingWidget=-1; if(workspace==null||drawer)showHome();workspace.addWidget(id); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK_WIDGET && request!=BIND_WIDGET && request!=CONFIG_WIDGET)return;
        if(result!=RESULT_OK) { cancelWidget(); return; }
        if(request==PICK_WIDGET){
            if(data==null)return;String kind=data.getStringExtra("widget_kind");
            if("search".equals(kind)){addSearchWidget();return;}
            if("weather".equals(kind)){String style=data.getStringExtra("weatherStyle");if(style!=null&&style.equals(SenseWeatherView.validStyle(style))){cancelWidget();if(workspace==null||drawer)showHome();workspace.addWeather(style);}return;}
            if("music".equals(kind)){int rows=data.getIntExtra("musicRows",2);if(rows==1||rows==2||rows==4)addMusicWidget(rows);return;}
            if("provider".equals(kind)){
                android.content.ComponentName component=data.getParcelableExtra("provider",android.content.ComponentName.class);
                android.os.UserHandle profile=data.getParcelableExtra("profile",android.os.UserHandle.class);
                for(var info:android.appwidget.AppWidgetManager.getInstance(this).getInstalledProviders())if(info.provider.equals(component)&&info.getProfile().equals(profile)){bindSelectedWidget(info);return;}
                android.widget.Toast.makeText(this,"This widget is no longer installed",android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if(request==CONFIG_WIDGET)finishWidget();else configureWidget();
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putInt("pendingWidget",pendingWidget); }
    @Override protected void onResume() {
        super.onResume();
        applyNavigationBarVisibility();
        discover.resume();
        MediaRepository.get(this).refreshAccess();
        followDisplayRefreshRate();
        if(appCatalog!=null)appCatalog.ensureLoaded();
        if(workspace!=null)workspace.reloadSavedSettings();
        if (drawer) showApps();else warmDrawer();
        if(fullscreenWeather!=null)fullscreenWeather.resume(pendingSettingsAction==null);
        if(pendingSettingsAction!=null){
            String action=pendingSettingsAction,item=pendingSettingsItem;pendingSettingsAction=pendingSettingsItem=null;
            root.post(()->performSettingsAction(action,item));
        }
    }
    @Override protected void onPause() { drawerAppMenu.dismiss();if(fullscreenWeather!=null)fullscreenWeather.pause();discover.pause();super.onPause(); }
    @Override protected void onStart() { super.onStart();widgetHost.startListening();discover.start(); }
    @Override protected void onStop() { if(drawer)resetDrawerScroll();discover.stop();widgetHost.stopListening();super.onStop(); }

    @Override protected void onDestroy() {
        drawerAppMenu.dismiss();
        if(root!=null)root.animate().cancel();
        if(fullscreenWeather!=null)fullscreenWeather.destroy();
        discover.destroy();
        if(dockTransitions!=null)dockTransitions.destroy();
        drawerGeneration++;warmGeneration++;drawerViews.clear();drawerWorker.shutdownNow();
        if(appCatalog!=null)appCatalog.removeListener(catalogChanged);
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);
        super.onDestroy();
    }
}
