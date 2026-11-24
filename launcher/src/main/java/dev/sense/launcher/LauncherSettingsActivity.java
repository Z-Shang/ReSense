package dev.sense.launcher;

import dev.sense.framework.SensePreferences;
import dev.sense.framework.SenseHeaderBar;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import dev.sense.launcher.ui.*;
import java.util.*;
import org.json.*;

/** Single full-screen home for launcher preferences and desktop-management actions. */
public final class LauncherSettingsActivity extends Activity {
    private LinearLayout content,itemRows,cityResults;
    private ScrollView scroll;
    private SharedPreferences workspace;
    private WeatherRepository weather;
    private TextView gridSummary,weatherStatus,accessStatus,homeStatus;
    private NumberPicker columns,rows;
    private Switch clockToggle;
    private boolean updatingClock;
    private final Map<String,View> sections=new HashMap<>();
    private int weatherSearchGeneration;
    private final SharedPreferences.OnSharedPreferenceChangeListener weatherChanged=(prefs,key)->updateWeather();
    static void open(Context context,String section){
        if(context instanceof MainActivity main){main.openLauncherSettings(section);return;}
        Intent intent=new Intent(context,LauncherSettingsActivity.class).putExtra("section",section);
        if(!(context instanceof Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        workspace=getSharedPreferences("workspace",0);weather=WeatherRepository.get(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff1c211e);
        SenseHeaderBar header=new SenseHeaderBar(this);header.setTitle("Sense settings");header.setAction("Done","Return to home screen",this::finish);root.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,0,0,dp(24));content.setBackgroundColor(Color.WHITE);content.setDividerDrawable(getDrawable(dev.sense.framework.R.drawable.sense_common_list_divider));content.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);content.setFocusableInTouchMode(true);content.requestFocus();scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        root.setOnApplyWindowInsetsListener((view,insets)->{var bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());var gestures=insets.getInsets(WindowInsets.Type.mandatorySystemGestures());var keyboard=insets.getInsets(WindowInsets.Type.ime());view.setPadding(bars.left,bars.top,bars.right,Math.max(keyboard.bottom,Math.max(bars.bottom,gestures.bottom)));return insets;});
        setContentView(root);root.requestApplyInsets();
        buildDesktop();buildDrawer();buildGestures();buildSearch();buildWeather();buildIntegrations();buildItems();buildSystem();
        int position=state==null?-1:state.getInt("scroll",0);
        scroll.post(()->{if(position>=0)scroll.scrollTo(0,position);else scrollToSection(getIntent().getStringExtra("section"));});
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void buildDesktop(){
        section("desktop","Desktop");
        action("Add widget",()->desktopAction("widgets",null));action("Manage pages",()->desktopAction("pages",null));
        gridSummary=label("",15);content.addView(gridSummary);updateGrid();
        LinearLayout grid=new LinearLayout(this);grid.setGravity(Gravity.CENTER);grid.setPadding(dp(16),0,dp(16),dp(8));
        columns=picker(3,8,workspace.getInt("columns",4),"Desktop columns");rows=picker(4,10,workspace.getInt("rows",6),"Desktop rows");
        LinearLayout columnBox=new LinearLayout(this);columnBox.setOrientation(LinearLayout.VERTICAL);columnBox.setGravity(Gravity.CENTER);columnBox.addView(label("Columns",15));columnBox.addView(columns);
        LinearLayout rowBox=new LinearLayout(this);rowBox.setOrientation(LinearLayout.VERTICAL);rowBox.setGravity(Gravity.CENTER);rowBox.addView(label("Rows",15));rowBox.addView(rows);
        grid.addView(columnBox,new LinearLayout.LayoutParams(0,-2,1));grid.addView(rowBox,new LinearLayout.LayoutParams(0,-2,1));content.addView(grid);
        action("Apply grid",()->{WorkspaceSettings.grid(workspace,columns.getValue(),rows.getValue());updateGrid();refreshItems();Toast.makeText(this,"Grid updated",Toast.LENGTH_SHORT).show();});
        Switch clock=new SensePreferences.Toggle(this);clockToggle=clock;clock.setText("Show Sense clock and weather");clock.setChecked(WorkspaceSettings.hasClock(workspace));clock.setOnCheckedChangeListener((button,checked)->{if(!updatingClock){WorkspaceSettings.clock(workspace,checked);refreshItems();}});content.addView(clock);
        Switch navigation=new SensePreferences.Toggle(this);navigation.setText("Hide system navigation bar");navigation.setChecked(WorkspaceSettings.hideNavigationBar(workspace));navigation.setOnCheckedChangeListener((button,checked)->workspace.edit().putBoolean(WorkspaceSettings.HIDE_NAVIGATION_BAR,checked).apply());content.addView(navigation);
        content.addView(label("Applies to the home screen and app drawer. An edge swipe temporarily reveals hidden navigation controls.",14));
        action("Choose wallpaper",()->launch(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER),"Choose wallpaper")));
    }
    private void buildDrawer(){
        section("drawer","App drawer");
        RadioGroup layout=choices(new String[]{"Grid","List"},getSharedPreferences("drawer",0).getBoolean("listMode",false)?1:0,index->getSharedPreferences("drawer",0).edit().putBoolean("listMode",index==1).apply());content.addView(layout);
        LinearLayout timing=new LinearLayout(this);timing.setGravity(Gravity.CENTER_VERTICAL);
        timing.addView(label("Alphabet hold delay",16),new LinearLayout.LayoutParams(0,-2,1));
        NumberPicker hold=picker(0,18,Math.round((AlphabetIndex.holdDelayMillis(this)-100)/50f),"Alphabet hold delay");
        String[] delays=new String[19];for(int i=0;i<delays.length;i++)delays[i]=(100+i*50)+" ms";hold.setDisplayedValues(delays);
        hold.setOnValueChangedListener((view,oldValue,newValue)->AlphabetIndex.setHoldDelayMillis(this,100+newValue*50));
        timing.addView(hold,new LinearLayout.LayoutParams(dp(120),dp(144)));content.addView(timing);
        content.addView(label("Hold the alphabet rail before sliding to a letter. Default: 300 ms.",14));
    }
    private void buildGestures(){
        section("gestures","Desktop gestures");
        for(boolean up:new boolean[]{true,false}){
            content.addView(label(up?"Swipe up":"Swipe down",17));
            int selected=getSharedPreferences("gestures",0).getInt(up?"up":"down",up?1:2);
            content.addView(choices(DesktopGestures.ACTIONS,Math.max(0,Math.min(2,selected)),index->getSharedPreferences("gestures",0).edit().putInt(up?"up":"down",index).apply()));
        }
        content.addView(label("Opening notifications uses Sense desktop gestures in Android Accessibility settings.",14));
        action("Notification gesture access",()->launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }
    private void buildSearch(){
        section("search","Search");content.addView(label("Searches open in your default browser.",14));
        var engines=BrowserSearch.Engine.values();String[] names=new String[engines.length];for(int i=0;i<names.length;i++)names[i]=engines[i].label;
        content.addView(choices(names,BrowserSearch.engine(this).ordinal(),index->BrowserSearch.setEngine(this,engines[index])));
    }
    private void buildWeather(){
        section("weather","Weather");weatherStatus=label("",15);content.addView(weatherStatus);updateWeather();
        EditText city=new EditText(this);city.setSingleLine(true);city.setHint("City, country or region");city.setContentDescription("Weather city search");SensePreferences.input(city);LinearLayout.LayoutParams cityParams=new LinearLayout.LayoutParams(-1,-2);cityParams.setMargins(dp(16),dp(8),dp(16),dp(8));content.addView(city,cityParams);
        Button search=action("Find city",()->{});cityResults=new LinearLayout(this);cityResults.setOrientation(LinearLayout.VERTICAL);
        search.setOnClickListener(v->{
            String query=city.getText().toString().strip();if(query.length()<2){city.setError("Enter at least two characters");return;}
            int generation=++weatherSearchGeneration;search.setEnabled(false);cityResults.removeAllViews();
            weather.search(query,(results,error)->{
                if(isFinishing() || isDestroyed() || generation!=weatherSearchGeneration)return;
                search.setEnabled(true);
                if(error!=null){city.setError(error);return;}
                if(results.length()==0){city.setError("No cities found");return;}
                for(int i=0;i<results.length();i++){
                    JSONObject location=results.optJSONObject(i);String name=location.optString("name")+", "+location.optString("admin1",location.optString("country"))+" ("+location.optString("country_code")+")";
                    cityResults.addView(rowButton(name,()->{try{weather.select(location);cityResults.removeAllViews();updateWeather();}catch(JSONException failure){city.setError("Invalid location result");}}),new LinearLayout.LayoutParams(-1,-2));
                }
            });
        });content.addView(cityResults);
        action("Refresh weather",()->weather.refresh(true));action("Preview weather animations",()->WeatherPreview.show(this));
        for(String reason:new String[]{"unlock","home"}){
            Switch effect=new SensePreferences.Toggle(this);effect.setText(reason.equals("unlock")?"Full-screen weather after unlocking":"Full-screen weather when entering home");
            var preferences=getSharedPreferences(FullscreenWeather.PREFERENCES,0);effect.setChecked(preferences.getBoolean(reason,true));
            effect.setOnCheckedChangeListener((button,checked)->preferences.edit().putBoolean(reason,checked).apply());content.addView(effect);
        }
        action("Preview full-screen weather",()->new AlertDialog.Builder(this).setTitle("Full-screen weather")
                .setItems(new String[]{"Sunlight and light clouds","Clouds","Rain and wiper","Ice and snow","Sunlight","Thunderstorm","Wind"},(dialog,index)->desktopAction("weatherEffect",Integer.toString(index+1))).show());
        content.addView(label("Weather: Open-Meteo (CC BY 4.0). Location search: GeoNames via Open-Meteo.",13));
        action("Open-Meteo",()->launch(new Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/"))));
    }
    private void buildIntegrations(){
        section("integrations","Integrations");
        accessStatus=label("",15);content.addView(accessStatus);action("Media control access",()->MediaRepository.get(this).requestAccess(this));
        String discover=getIntent().getStringExtra("discoverStatus");content.addView(label("Google Discover · "+(discover==null?"Google app required for the leftmost screen":discover),14));
        action("Reconnect Google Discover",()->desktopAction("discover",null));
    }
    private void buildItems(){section("items","Placed items");itemRows=new LinearLayout(this);itemRows.setOrientation(LinearLayout.VERTICAL);content.addView(itemRows);refreshItems();}
    private void refreshItems(){
        if(itemRows==null)return;itemRows.removeAllViews();JSONArray items=WorkspaceSettings.items(workspace);
        if(items.length()==0){itemRows.addView(label("No placed items",15));return;}
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;String key=item.toString();
            itemRows.addView(label("Page "+item.optInt("page")+" · "+itemLabel(item),16));
            LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(16),dp(4),dp(16),dp(12));
            LinearLayout.LayoutParams resizeParams=new LinearLayout.LayoutParams(0,-2,1);resizeParams.setMarginEnd(dp(4));
            LinearLayout.LayoutParams removeParams=new LinearLayout.LayoutParams(0,-2,1);removeParams.setMarginStart(dp(4));
            actions.addView(button("Resize",()->desktopAction("resize",key)),resizeParams);
            actions.addView(button("Remove",()->removeItem(key)),removeParams);itemRows.addView(actions);
        }
    }
    private String itemLabel(JSONObject item){
        return switch(item.optString("type")){
            case "clock"->"Sense clock and weather";
            case "music"->"Sense Music · 4 × "+item.optInt("musicRows",2);
            case "search"->"Sense Search";
            case "widget"->{var info=android.appwidget.AppWidgetManager.getInstance(this).getAppWidgetInfo(item.optInt("id"));yield info==null?"Unavailable widget":info.loadLabel(getPackageManager());}
            default->{String name="App shortcut";var component=ComponentName.unflattenFromString(item.optString("component"));if(component!=null)try{name=getPackageManager().getActivityInfo(component,0).loadLabel(getPackageManager()).toString();}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}yield name;}
        };
    }
    private void removeItem(String key){
        JSONArray items=WorkspaceSettings.items(workspace),remaining=new JSONArray();
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            if(item.toString().equals(key)){if("widget".equals(item.optString("type")))new SenseWidgetHost(this,1024).deleteAppWidgetId(item.optInt("id"));}
            else remaining.put(item);
        }
        workspace.edit().putString("items",remaining.toString()).apply();
        updatingClock=true;clockToggle.setChecked(WorkspaceSettings.hasClock(workspace));updatingClock=false;refreshItems();
    }
    private void buildSystem(){
        section("system","System");homeStatus=label("",15);content.addView(homeStatus);
        action("Default home app",()->{RoleManager role=getSystemService(RoleManager.class);if(role.isRoleAvailable(RoleManager.ROLE_HOME) && !role.isRoleHeld(RoleManager.ROLE_HOME))launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME));else launch(new Intent(Settings.ACTION_HOME_SETTINGS));});
        action("HTC lock screen",()->launch(new Intent().setComponent(new android.content.ComponentName("dev.sense.lockscreen","dev.sense.lockscreen.LockScreenSettingsActivity"))));
        action("Android settings",()->launch(new Intent(Settings.ACTION_SETTINGS)));
    }
    private void updateGrid(){if(gridSummary!=null)gridSummary.setText("Home grid · "+workspace.getInt("columns",4)+" columns × "+workspace.getInt("rows",6)+" rows");}
    private void updateWeather(){
        if(weatherStatus==null)return;var snapshot=weather.snapshot();String details=weather.city()+"\n"+weather.status();
        if(snapshot!=null)details=weather.city()+" · "+WeatherCodes.description(snapshot.code)+", "+Math.round(snapshot.temperature)+"°C\nUpdated "+android.text.format.DateFormat.format("EEE HH:mm",snapshot.fetched)+" · "+weather.status();weatherStatus.setText(details);
    }
    private void desktopAction(String action,String item){startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("settingsAction",action).putExtra("settingsItem",item));finish();}
    private void launch(Intent intent){try{startActivity(intent);}catch(ActivityNotFoundException error){Toast.makeText(this,"No app available for this action",Toast.LENGTH_SHORT).show();}}
    private void section(String key,String title){TextView heading=label(title,14);SensePreferences.section(heading);heading.setAccessibilityHeading(true);sections.put(key,heading);content.addView(heading);}
    private TextView label(String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextSize(size);SensePreferences.text(view);return view;}
    private Button button(String text,Runnable action){Button button=new SensePreferences.ButtonControl(this);button.setText(text);button.setOnClickListener(v->action.run());return button;}
    private Button rowButton(String text,Runnable action){Button button=new SensePreferences.Row(this);button.setText(text);button.setOnClickListener(v->action.run());return button;}
    private Button action(String text,Runnable action){
        boolean control=Set.of("Apply grid","Find city","Refresh weather").contains(text);
        Button button=control?button(text,action):rowButton(text,action);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);if(control)params.setMargins(dp(16),dp(8),dp(16),dp(8));content.addView(button,params);return button;
    }
    private NumberPicker picker(int min,int max,int current,String description){NumberPicker picker=new NumberPicker(this);picker.setMinValue(min);picker.setMaxValue(max);picker.setValue(Math.max(min,Math.min(max,current)));picker.setWrapSelectorWheel(false);picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);picker.setTextColor(0xff222222);picker.setTextSize(dp(18));picker.setSelectionDividerHeight(dp(1));picker.setContentDescription(description);return picker;}
    private RadioGroup choices(String[] labels,int selected,java.util.function.IntConsumer changed){RadioGroup group=new RadioGroup(this);group.setDividerDrawable(getDrawable(dev.sense.framework.R.drawable.sense_common_list_divider));group.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);for(int i=0;i<labels.length;i++){RadioButton option=new SensePreferences.Choice(this);option.setId(View.generateViewId());option.setTag(i);option.setText(labels[i]);group.addView(option,new RadioGroup.LayoutParams(-1,-2));if(i==selected)group.check(option.getId());}group.setOnCheckedChangeListener((view,id)->{View option=group.findViewById(id);if(option!=null)changed.accept((Integer)option.getTag());});return group;}
    private void scrollToSection(String section){View target=sections.get(section);if(target!=null)scroll.scrollTo(0,target.getTop());}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);scroll.post(()->scrollToSection(intent.getStringExtra("section")));}
    @Override protected void onStart(){super.onStart();weather.prefs.registerOnSharedPreferenceChangeListener(weatherChanged);}
    @Override protected void onStop(){weather.prefs.unregisterOnSharedPreferenceChangeListener(weatherChanged);super.onStop();}
    @Override protected void onResume(){super.onResume();if(accessStatus!=null)accessStatus.setText(MediaRepository.get(this).hasAccess()?"Music · Media control access enabled":"Music · Enable media control access to show and control other apps' playback");if(homeStatus!=null)homeStatus.setText(getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_HOME)?"Sense Launcher is your default home app":"Sense Launcher is not your default home app");updateWeather();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("scroll",scroll.getScrollY());}
    @Override protected void onDestroy(){weatherSearchGeneration++;super.onDestroy();}
}
