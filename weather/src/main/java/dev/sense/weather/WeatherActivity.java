package dev.sense.weather;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;

import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** HTC Weather screen structure rebuilt with native views and live Open-Meteo data. */
public final class WeatherActivity extends Activity {
    private WeatherRepository weather;
    private LinearLayout details;
    private LinearLayout tabs;
    private WeatherSceneView scene;
    private int tab;
    private boolean fullscreenAnimation;
    private Dialog cityDialog;
    private ListPopupWindow cityDropdown;
    private BaseAdapter cityMenuAdapter;
    private java.util.ArrayList<WeatherRepository.City> cityMenuCities;
    private CityRow currentLocationRow;
    private boolean cityGesture,cityGestureClaimed;
    private float cityDownX,cityDownY;
    private int searchGeneration;
    private static final int LOCATION_PERMISSION=41;
    private final java.util.List<android.os.CancellationSignal> locationRequests=new java.util.ArrayList<>();
    private final android.os.Handler locationHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private int locationGeneration;
    private final Runnable locationTimeout=()->{cancelLocation();Toast.makeText(this,"Location unavailable. Try again outdoors or choose a saved city.",Toast.LENGTH_LONG).show();};
    private final Runnable repaint=this::render;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener changed=(prefs,key)->{
        View root=findViewById(R.id.weather_root);root.removeCallbacks(repaint);root.post(repaint);
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);getWindow().setNavigationBarContrastEnforced(false);
        setContentView(R.layout.weather_main);
        fullscreenAnimation=saved!=null && saved.getBoolean("fullscreenAnimation",false);
        replaceHeaderIcon(R.id.refresh,dev.sense.framework.R.drawable.sense_common_icon_glance_updates_rest,dev.sense.framework.R.drawable.sense_common_icon_glance_updates_on,"Refresh weather");
        replaceHeaderIcon(R.id.fullscreen,R.drawable.wx_common_icon_titlebar_full_screen_rest,R.drawable.wx_common_icon_titlebar_full_screen_rest,"Full screen weather animation");
        findViewById(R.id.fullscreen).setOnClickListener(v->{fullscreenAnimation=!fullscreenAnimation;render();});
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{if(fullscreenAnimation){fullscreenAnimation=false;render();}else finish();});
        View root=findViewById(R.id.weather_root);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            var safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            int bottom=Math.max(safe.bottom,insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom);
            view.setPadding(safe.left,safe.top,safe.right,bottom);return insets;
        });root.requestApplyInsets();
        weather=WeatherRepository.get(this);tab=saved==null?0:saved.getInt("tab",0);
        details=findViewById(R.id.details);scene=findViewById(R.id.scene);
        tabs=findViewById(R.id.tabs);
        var tabFrame=new android.graphics.drawable.GradientDrawable();tabFrame.setColor(0xff202629);tabFrame.setCornerRadius(dp(5));
        tabs.setBackground(tabFrame);tabs.setClipToOutline(true);
        findViewById(R.id.refresh).setOnClickListener(v->{if(weather.isCurrentLocation())useCurrentLocation(true);else weather.refresh(true);});
        findViewById(R.id.city).setOnClickListener(v->showCityDropdown());
        TextView title=text(R.id.city);title.setFocusable(true);
        var arrow=getDrawable(R.drawable.wx_common_icon_titlebar_down_rest);arrow.setBounds(0,0,dp(20),dp(20));
        title.setCompoundDrawablesRelative(null,null,arrow,null);title.setCompoundDrawablePadding(dp(8));
        findViewById(R.id.attribution).setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://open-meteo.com/")));}catch(ActivityNotFoundException ignored){}});
        acceptLocation(getIntent());render();
    }
    private void replaceHeaderIcon(int id,int resting,int active,String description){
        View previous=findViewById(id);ViewGroup parent=(ViewGroup)previous.getParent();int index=parent.indexOfChild(previous);var params=previous.getLayoutParams();parent.removeView(previous);
        ImageButton icon=new dev.sense.framework.SenseHeaderIconButton(this,resting,active,description);icon.setId(id);parent.addView(icon,index,params);
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);acceptLocation(intent);render();}
    private void acceptLocation(Intent intent){
        if(!intent.hasExtra("latitude") || !intent.hasExtra("longitude"))return;
        try{
            if(intent.getBooleanExtra("sharedWeather",false) && weather.sharingWeather())return;
            double lat=intent.getDoubleExtra("latitude",Double.NaN),lon=intent.getDoubleExtra("longitude",Double.NaN);
            String name=intent.getStringExtra("city");if(name==null || name.isBlank() || name.length()>200)return;
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return;
            if(name.equals(weather.city()) && Double.toString(lat).equals(weather.prefs.getString("latitude","-33.8688")) && Double.toString(lon).equals(weather.prefs.getString("longitude","151.2093")))return;
            weather.select(new JSONObject().put("name",name).put("latitude",lat).put("longitude",lon));
        }catch(JSONException ignored){}finally{
            intent.removeExtra("latitude");intent.removeExtra("longitude");intent.removeExtra("city");
        }
    }
    @Override protected void onStart(){super.onStart();scene.setActive(true);weather.connectSharedWeather();weather.prefs.registerOnSharedPreferenceChangeListener(changed);render();weather.refresh(false);if(weather.isCurrentLocation())useCurrentLocation(false);}
    @Override protected void onStop(){scene.setActive(false);cancelLocation();if(cityDropdown!=null)cityDropdown.dismiss();weather.prefs.unregisterOnSharedPreferenceChangeListener(changed);findViewById(R.id.weather_root).removeCallbacks(repaint);super.onStop();}
    @Override protected void onDestroy(){searchGeneration++;if(cityDialog!=null)cityDialog.dismiss();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("tab",tab);out.putBoolean("fullscreenAnimation",fullscreenAnimation);}
    private TextView text(int id){return findViewById(id);}
    private void render(){
        if(details==null)return;
        text(R.id.city).setText(weather.city());
        text(R.id.city).setContentDescription(weather.city()+". Open saved cities dropdown. Swipe up or down to change city.");
        findViewById(R.id.now_panel).setVisibility(tab!=1?View.VISIBLE:View.GONE);
        findViewById(R.id.weather_scroll).setVisibility(View.GONE);
        findViewById(R.id.hourly_chart).setVisibility(tab==1?View.VISIBLE:View.GONE);
        View provider=findViewById(R.id.provider_bar);ViewGroup providerParent=tab!=1?findViewById(R.id.now_panel):findViewById(R.id.weather_root);
        if(provider.getParent()!=providerParent){((ViewGroup)provider.getParent()).removeView(provider);providerParent.addView(provider,tab!=1?1:providerParent.indexOfChild(tabs),new LinearLayout.LayoutParams(-1,-2));}
        provider.setBackgroundColor(0xffe7eff2);
        text(R.id.attribution).setTextColor(0xff344b57);text(R.id.status).setTextColor(0xff637b86);
        provider.setVisibility(fullscreenAnimation?View.GONE:View.VISIBLE);
        findViewById(R.id.now_periods).setVisibility(fullscreenAnimation?View.GONE:View.VISIBLE);
        findViewById(R.id.condition_overlay).setVisibility(fullscreenAnimation?View.GONE:View.VISIBLE);
        tabs.setVisibility(fullscreenAnimation?View.GONE:View.VISIBLE);
        ImageButton full=findViewById(R.id.fullscreen);full.setVisibility(tab==1?View.GONE:View.VISIBLE);
        full.setImageResource(fullscreenAnimation?R.drawable.wx_common_icon_titlebar_fit_screen_rest:R.drawable.wx_common_icon_titlebar_full_screen_rest);
        full.setContentDescription(fullscreenAnimation?"Exit full screen weather animation":"Full screen weather animation");
        text(R.id.forecast_title).setVisibility(tab==0?View.GONE:View.VISIBLE);
        text(R.id.forecast_title).setText(tab==1?"Hourly forecast":"Five-day forecast");
        WeatherRepository.Snapshot snapshot=weather.snapshot();JSONObject data=weather.forecast();
        text(R.id.temperature).setText(snapshot==null?"—":degree(snapshot.temperature));
        boolean noCity=weather.cities().isEmpty() && !weather.isCurrentLocation();
        text(R.id.condition).setText(snapshot==null?(noCity?"Add a city":"Waiting for weather"):WeatherCodes.description(snapshot.code));
        text(R.id.range).setText(snapshot==null?(noCity?"Open the city list to add a city":"Choose a city or refresh"):"H: "+degree(snapshot.high)+"    L: "+degree(snapshot.low));
        ZoneId zone;try{zone=ZoneId.of(data==null?"UTC":data.optString("timezone","UTC"));}catch(DateTimeException e){zone=ZoneOffset.UTC;}
        String status=snapshot==null?weather.status():DateTimeFormatter.ofPattern("HH:mm",Locale.getDefault()).withZone(zone).format(Instant.ofEpochMilli(snapshot.fetched));
        if(snapshot!=null && weather.status().contains("unavailable"))status="Offline · "+status;
        text(R.id.status).setText(status);text(R.id.status).setContentDescription(weather.status()+". Last refreshed "+status);
        var update=getDrawable(R.drawable.wx_icon_update);update.setBounds(0,0,dp(12),dp(12));text(R.id.status).setCompoundDrawablesRelative(update,null,null,null);text(R.id.status).setCompoundDrawablePadding(dp(4));
        scene.condition(snapshot==null?-1:snapshot.code,snapshot==null||snapshot.day);
        scene.setContentDescription(snapshot==null?"Weather animation":WeatherCodes.description(snapshot.code)+". Tap to replay animation.");
        tabs.removeAllViews();String[] labels={"Now","Hourly","5-day"};
        for(int i=0;i<labels.length;i++){
            final int selected=i;
            TextView button=dev.sense.framework.SenseStyle.tab(this,labels[i],i);
            button.setTextSize(17);button.setTypeface(null,Typeface.BOLD);button.setGravity(Gravity.CENTER);
            button.setSelected(i==tab);button.setContentDescription(labels[i]+" forecast");
            button.setOnClickListener(v->selectForecast(selected));tabs.addView(button,new LinearLayout.LayoutParams(0,-1,1));
        }
        if(tab==2)renderDaily(data,zone);else renderPeriods(data,zone);
        details.removeAllViews();
        if(data==null && tab==1)((HourlyForecastView)findViewById(R.id.hourly_chart)).forecast(null,zone);
        if(tab==1)((HourlyForecastView)findViewById(R.id.hourly_chart)).forecast(data,zone);
        refreshCityDropdown();
    }
    private void renderDaily(JSONObject data,ZoneId zone){
        LinearLayout strip=findViewById(R.id.now_periods);strip.removeAllViews();
        for(var day:WeatherPeriods.upcoming(data,zone,LocalDate.now(zone))){
            if(strip.getChildCount()>0){View divider=new View(this);divider.setBackgroundColor(0xffbdcbd1);strip.addView(divider,new LinearLayout.LayoutParams(dp(1),-1));}
            LinearLayout card=new LinearLayout(this);card.setTag("daily-card");card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(2),dp(5),dp(2),dp(5));
            TextView label=cell(day.date().format(DateTimeFormatter.ofPattern("EEE",Locale.getDefault())),14,0xff344b57);label.setGravity(Gravity.CENTER);label.setTypeface(null,Typeface.BOLD);card.addView(label);
            TextView date=cell(day.date().format(DateTimeFormatter.ofPattern("MMM d",Locale.getDefault())),10,0xff637b86);date.setGravity(Gravity.CENTER);card.addView(date);
            ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);int id=conditionIcon(day.code(),true);if(id!=0)icon.setImageResource(id);card.addView(icon,new LinearLayout.LayoutParams(dp(48),0,1));
            TextView range=cell(degree(day.high())+"/"+degree(day.low()),14,0xff243c48);range.setGravity(Gravity.CENTER);range.setSingleLine();card.addView(range);
            card.setContentDescription(day.date()+", "+WeatherCodes.description(day.code())+", high "+degree(day.high())+", low "+degree(day.low()));
            strip.addView(card,new LinearLayout.LayoutParams(0,-1,1));
        }
    }
    private void renderPeriods(JSONObject data,ZoneId zone){
        LinearLayout strip=findViewById(R.id.now_periods);strip.removeAllViews();
        var periods=WeatherPeriods.build(data,zone,LocalDate.now(zone));
        for(int i=0;i<periods.size();i++){
            var period=periods.get(i);
            if(i>0){View divider=new View(this);divider.setBackgroundColor(0xffbdcbd1);strip.addView(divider,new LinearLayout.LayoutParams(dp(1),-1));}
            LinearLayout card=new LinearLayout(this);card.setTag("now-period");card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(4),dp(5),dp(4),dp(5));
            TextView label=cell(period.label(),16,0xff344b57);label.setGravity(Gravity.CENTER);label.setTypeface(null,Typeface.BOLD);card.addView(label);
            TextView date=cell(period.date().format(DateTimeFormatter.ofPattern("MMM d, yyyy",Locale.getDefault())),11,0xff637b86);date.setGravity(Gravity.CENTER);card.addView(date);
            ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int htc=WeatherCodes.htcScene(period.code(),period.day());
            int id=htc==0?0:getResources().getIdentifier(String.format(Locale.ROOT,"wx_condition_%02d",htc),"drawable",getPackageName());
            if(id!=0)icon.setImageResource(id);card.addView(icon,new LinearLayout.LayoutParams(dp(68),0,1));
            TextView temperature=cell(degree(period.temperature()),30,0xff243c48);temperature.setGravity(Gravity.CENTER);card.addView(temperature);
            card.setContentDescription(period.label()+", "+date.getText()+", "+WeatherCodes.description(period.code())+", "+temperature.getText());
            strip.addView(card,new LinearLayout.LayoutParams(0,-1,1));
        }
    }
    private void selectForecast(int selected){
        tab=selected;fullscreenAnimation=false;render();findViewById(R.id.weather_scroll).scrollTo(0,0);((HourlyForecastView)findViewById(R.id.hourly_chart)).scrollTo(0,0);
        if(selected==0)return;
        // Older launcher caches may contain only today's high/low. A tab needs
        // the actual hourly/daily series, not a repetition of current conditions.
        JSONObject saved=weather.forecast();
        weather.refresh(needsForecast(saved,selected));
    }
    static boolean needsForecast(JSONObject saved,int selected){
        JSONObject series=saved==null?null:saved.optJSONObject(selected==1?"hourly":"daily");
        JSONArray times=series==null?null:series.optJSONArray("time");
        return times==null || times.length()<(selected==1?24:6);
    }
    private TextView cell(String label,int size,int color){
        TextView view=new TextView(this);view.setText(label);view.setTextSize(size);view.setTextColor(color);view.setGravity(Gravity.CENTER_VERTICAL);return view;
    }
    private void forecastColumnHeader(){
        LinearLayout header=new LinearLayout(this);header.setPadding(dp(12),dp(4),dp(12),dp(8));
        header.addView(cell("Time",12,0xffaabbc2),new LinearLayout.LayoutParams(dp(54),-2));
        header.addView(cell("Conditions",12,0xffaabbc2),new LinearLayout.LayoutParams(0,-2,1));
        TextView rain=cell("Temp / Rain",12,0xffaabbc2);rain.setGravity(Gravity.END);header.addView(rain,new LinearLayout.LayoutParams(dp(78),-2));
        details.addView(header,new LinearLayout.LayoutParams(-1,-2));
    }
    private void dayHeader(java.time.LocalDate date,ZoneId zone){
        var today=java.time.LocalDate.now(zone);
        String label=date.equals(today)?"Today":date.equals(today.plusDays(1))?"Tomorrow":date.format(DateTimeFormatter.ofPattern("EEEE",Locale.getDefault()));
        TextView view=cell(label+" · "+date.format(DateTimeFormatter.ofPattern("d MMM",Locale.getDefault())),14,0xffc0e78e);
        view.setPadding(dp(12),dp(12),dp(12),dp(8));view.setTypeface(null,Typeface.BOLD);view.setAccessibilityHeading(true);
        details.addView(view,new LinearLayout.LayoutParams(-1,-2));
    }
    private void hourlyRow(String time,int condition,boolean day,double temperature,double rain){
        LinearLayout row=new LinearLayout(this);row.setTag("hourly-row");row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(72));row.setPadding(dp(12),dp(10),dp(12),dp(10));
        TextView hour=cell(time,16,Color.WHITE);row.addView(hour,new LinearLayout.LayoutParams(dp(54),-2));
        ImageView image=new ImageView(this);int resource=conditionIcon(condition,day);if(resource!=0)image.setImageResource(resource);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(dp(40),dp(40));icon.setMarginEnd(dp(10));row.addView(image,icon);
        TextView description=cell(WeatherCodes.description(condition),14,0xffc5d0d4);row.addView(description,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout values=new LinearLayout(this);values.setOrientation(LinearLayout.VERTICAL);values.setGravity(Gravity.END);
        TextView degrees=cell(degree(temperature),23,Color.WHITE);degrees.setTypeface(null,Typeface.BOLD);values.addView(degrees);
        TextView chance=cell(Double.isFinite(rain)?Math.round(rain)+"% rain":"— rain",12,0xffb7d7e6);values.addView(chance);
        row.addView(values,new LinearLayout.LayoutParams(dp(78),-2));
        details.addView(row,new LinearLayout.LayoutParams(-1,-2));divider();
    }
    private int conditionIcon(int condition,boolean day){return getResources().getIdentifier(String.format(Locale.ROOT,"wx_condition_%02d",WeatherCodes.htcScene(condition,day)),"drawable",getPackageName());}
    private void divider(){View line=new View(this);line.setBackgroundResource(R.drawable.wx_common_weather_divider);details.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    private static int condition(JSONObject object,int index){double value=value(object,"weather_code",index);return Double.isFinite(value)?(int)value:-1;}
    private static double value(JSONObject object,String key,int index){JSONArray array=object.optJSONArray(key);return array==null?Double.NaN:array.optDouble(index,Double.NaN);}
    private static String degree(double number){return Math.round(number)+"°";}
    private void metric(String label,JSONObject object,String key,String unit,int icon){
        double value=object==null?Double.NaN:object.optDouble(key,Double.NaN);
        TextView item=message(label+"    "+(Double.isFinite(value)?Math.round(value)+unit:"—"));
        if(icon!=0){var image=getDrawable(icon);image.setBounds(0,0,dp(28),dp(28));item.setCompoundDrawablesRelative(image,null,null,null);item.setCompoundDrawablePadding(dp(14));}
    }
    private TextView message(String value){TextView view=new TextView(this);view.setText(value);view.setTextColor(Color.WHITE);view.setTextSize(16);view.setPadding(dp(12),dp(12),dp(12),dp(12));details.addView(view,new LinearLayout.LayoutParams(-1,-2));return view;}
    private void row(String title,String subtitle,int condition,boolean day,String temperatures){
        LinearLayout item=new LinearLayout(this);item.setTag("daily-row");item.setOrientation(LinearLayout.VERTICAL);item.setPadding(dp(12),dp(12),dp(12),dp(12));
        TextView heading=new TextView(this);heading.setText(title+"   "+temperatures);heading.setTextSize(16);heading.setTextColor(Color.WHITE);heading.setTypeface(null,Typeface.BOLD);item.addView(heading);
        int sceneCode=WeatherCodes.htcScene(condition,day);
        int icon=getResources().getIdentifier(String.format(Locale.ROOT,"wx_condition_%02d",sceneCode),"drawable",getPackageName());
        if(icon!=0){var image=getDrawable(icon);image.setBounds(0,0,dp(40),dp(40));heading.setCompoundDrawablesRelative(image,null,null,null);heading.setCompoundDrawablePadding(dp(12));}
        TextView sub=new TextView(this);sub.setText(subtitle);sub.setTextColor(0xffc5d0d4);sub.setTextSize(14);item.addView(sub);
        details.addView(item,new LinearLayout.LayoutParams(-1,-2));
        View divider=new View(this);divider.setBackgroundResource(R.drawable.wx_common_weather_divider);details.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    ListPopupWindow cityMenu(){return cityDropdown;}
    private void showCityDropdown(){
        if(cityDropdown!=null && cityDropdown.isShowing())return;
        var cities=new java.util.ArrayList<>(weather.cities());cityMenuCities=cities;
        int selected=-1;
        for(int i=0;i<cities.size();i++)if(!weather.isCurrentLocation() && cities.get(i).key().equals(weather.selectedCityKey()))selected=i;
        ListPopupWindow popup=new ListPopupWindow(this);cityDropdown=popup;
        popup.setAnchorView(findViewById(R.id.city));popup.setModal(true);
        popup.setWidth(Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),Math.max(dp(280),findViewById(R.id.city).getWidth())));
        popup.setBackgroundDrawable(getDrawable(dev.sense.framework.R.drawable.sense_common_popupmenu));
        popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        // A prompt sits outside the scrolling city list, so this action stays pinned.
        CityRow current=(CityRow)getLayoutInflater().inflate(R.layout.weather_city_row,null,false);currentLocationRow=current;
        current.setTag("current-location");current.setChecked(weather.isCurrentLocation());
        CheckedTextView label=current.findViewById(R.id.city_label);label.setText("Current location");
        var gps=getDrawable(R.drawable.wx_icon_my_location_white);gps.setBounds(0,0,dp(24),dp(24));
        label.setCompoundDrawablesRelative(gps,null,null,null);label.setCompoundDrawablePadding(dp(12));
        View unusedRemove=current.findViewById(R.id.remove_city);unusedRemove.setVisibility(View.INVISIBLE);unusedRemove.setEnabled(false);
        unusedRemove.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);current.setFocusable(true);
        current.setContentDescription("Current location. Use GPS location for weather");
        current.setOnClickListener(v->{popup.dismiss();useCurrentLocation(true);});
        popup.setPromptPosition(ListPopupWindow.POSITION_PROMPT_ABOVE);popup.setPromptView(current);
        cityMenuAdapter=new BaseAdapter(){
            @Override public int getCount(){return cities.size()+1;}
            @Override public WeatherRepository.City getItem(int position){return position==cities.size()?null:cities.get(position);}
            @Override public long getItemId(int position){return position;}
            @Override public int getViewTypeCount(){return 2;}
            @Override public int getItemViewType(int position){return position==cities.size()?1:0;}
            @Override public View getView(int position,View recycled,ViewGroup parent){
                if(position==cities.size()){
                    TextView add=recycled instanceof TextView?(TextView)recycled:new TextView(WeatherActivity.this);
                    add.setTag("add-city");add.setText("Add city");add.setContentDescription("Add city");
                    add.setTextColor(getColorStateList(R.color.city_text));add.setTextSize(18);add.setGravity(Gravity.CENTER_VERTICAL);
                    add.setMinHeight(dp(56));add.setPadding(dp(16),dp(10),dp(16),dp(10));
                    var icon=getDrawable(dev.sense.framework.R.drawable.sense_common_icon_glance_add_rest);icon.setBounds(0,0,dp(24),dp(24));
                    add.setCompoundDrawablesRelative(icon,null,null,null);add.setCompoundDrawablePadding(dp(12));return add;
                }
                View row=recycled==null?getLayoutInflater().inflate(R.layout.weather_city_row,parent,false):recycled;
                var city=getItem(position);((CheckedTextView)row.findViewById(R.id.city_label)).setText(city.label());
                ((CityRow)row).setChecked(!weather.isCurrentLocation() && city.key().equals(weather.selectedCityKey()));
                View remove=row.findViewById(R.id.remove_city);remove.setContentDescription("Remove "+city.label());
                remove.setOnClickListener(v->{
                    weather.removeCity(city.key());render();
                });return row;
            }
        };popup.setAdapter(cityMenuAdapter);
        popup.setOnItemClickListener((parent,view,index,id)->{
            popup.dismiss();
            if(index==cities.size()){chooseCity();return;}
            try{selectCity(cities.get(index),0);}catch(JSONException error){Toast.makeText(this,"That city is unavailable",Toast.LENGTH_SHORT).show();}
        });
        popup.setOnDismissListener(()->{if(cityDropdown==popup){cityDropdown=null;cityMenuAdapter=null;cityMenuCities=null;currentLocationRow=null;}});popup.show();
        dev.sense.framework.SenseStyle.list(popup.getListView());
        popup.getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);if(selected>=0){popup.getListView().setItemChecked(selected,true);popup.setSelection(selected);}
    }
    private void refreshCityDropdown(){
        ListPopupWindow popup=cityDropdown;
        if(popup==null || !popup.isShowing() || cityMenuAdapter==null || cityMenuCities==null)return;
        ListView list=popup.getListView();if(list==null)return;
        var updated=weather.cities();boolean changed=!cityMenuCities.equals(updated);
        int first=list.getFirstVisiblePosition();View firstView=list.getChildAt(0);int top=firstView==null?0:firstView.getTop();
        if(changed){
            cityMenuCities.clear();cityMenuCities.addAll(updated);cityMenuAdapter.notifyDataSetChanged();
        }
        int selected=-1;
        for(int i=0;i<cityMenuCities.size();i++)if(!weather.isCurrentLocation() && cityMenuCities.get(i).key().equals(weather.selectedCityKey()))selected=i;
        if(changed || list.getCheckedItemPosition()!=selected){list.clearChoices();if(selected>=0)list.setItemChecked(selected,true);}
        currentLocationRow.setChecked(weather.isCurrentLocation());
        if(changed){
            int position=Math.min(first,cityMenuCities.size());
            list.post(()->{if(cityDropdown==popup && popup.isShowing())list.setSelectionFromTop(position,top);});
        }
    }
    private void selectCity(WeatherRepository.City city,int direction)throws JSONException{
        cancelLocation();
        if(weather.isCurrentLocation() || !city.key().equals(weather.selectedCityKey()))weather.select(city);
        render();findViewById(R.id.weather_scroll).scrollTo(0,0);
        text(R.id.city).announceForAccessibility(weather.city());
        if(direction!=0 && android.animation.ValueAnimator.areAnimatorsEnabled()){
            View content=findViewById(R.id.weather_scroll);content.animate().cancel();content.setTranslationY(dp(24)*direction);content.setAlpha(.65f);
            content.animate().translationY(0).alpha(1).setDuration(200).start();
        }
    }
    private void cancelLocation(){
        locationGeneration++;locationHandler.removeCallbacks(locationTimeout);
        for(var signal:locationRequests)signal.cancel();locationRequests.clear();
    }
    private void useCurrentLocation(boolean askPermission){
        cancelLocation();
        boolean fine=checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean coarse=checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED;
        if(!fine && !coarse){
            if(askPermission)requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_PERMISSION);
            return;
        }
        var manager=getSystemService(android.location.LocationManager.class);
        if(manager==null || !manager.isLocationEnabled()){
            if(askPermission)new AlertDialog.Builder(this).setTitle("Turn on location")
                    .setMessage("Enable Android location services to show weather where you are.")
                    .setPositiveButton("Location settings",(dialog,which)->startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Cancel",null).show();
            return;
        }
        int generation=locationGeneration;
        java.util.List<String> providers=new java.util.ArrayList<>();
        for(String provider:new String[]{android.location.LocationManager.FUSED_PROVIDER,android.location.LocationManager.NETWORK_PROVIDER,android.location.LocationManager.GPS_PROVIDER}){
            if(provider.equals(android.location.LocationManager.GPS_PROVIDER) && !fine)continue;
            if(manager.hasProvider(provider) && manager.isProviderEnabled(provider))providers.add(provider);
        }
        if(providers.isEmpty()){Toast.makeText(this,"No location provider available. Choose a city or enable precise location.",Toast.LENGTH_LONG).show();return;}
        Toast.makeText(this,"Finding current location…",Toast.LENGTH_SHORT).show();
        locationHandler.postDelayed(locationTimeout,30_000);
        final int[] remaining={providers.size()};
        for(String provider:providers){
            if(generation!=locationGeneration)break;
            var signal=new android.os.CancellationSignal();locationRequests.add(signal);
            try{manager.getCurrentLocation(provider,signal,getMainExecutor(),location->{
                if(generation!=locationGeneration)return;
                if(location==null){if(--remaining[0]==0)locationTimeout.run();return;}
                cancelLocation();
                try{weather.selectCurrentLocation(location.getLatitude(),location.getLongitude());if(askPermission)weather.refresh(true);render();findViewById(R.id.weather_scroll).scrollTo(0,0);}
                catch(JSONException error){Toast.makeText(this,"That location is unavailable",Toast.LENGTH_SHORT).show();}
            });}catch(SecurityException|IllegalArgumentException error){if(--remaining[0]==0)locationTimeout.run();}
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode!=LOCATION_PERMISSION)return;
        if(checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED || checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED)useCurrentLocation(true);
        else Toast.makeText(this,"Location permission was not granted. Saved cities are still available.",Toast.LENGTH_LONG).show();
    }
    private void nextCity(int direction){
        var cities=weather.cities();if(cities.size()<2)return;int selected=0;
        for(int i=0;i<cities.size();i++)if(cities.get(i).key().equals(weather.selectedCityKey())){selected=i;break;}
        int next=Math.floorMod(selected+direction,cities.size());
        try{selectCity(cities.get(next),direction);}catch(JSONException ignored){}
    }
    private boolean inView(int id,float x,float y){android.graphics.Rect bounds=new android.graphics.Rect();View view=findViewById(id);return view.isShown() && view.getGlobalVisibleRect(bounds) && bounds.contains((int)x,(int)y);}
    private boolean citySwipeArea(MotionEvent event){
        if(weather.cities().size()<2)return false;
        for(int id:new int[]{R.id.city,R.id.current_scene,R.id.condition,R.id.range,R.id.status,R.id.forecast_title})
            if(inView(id,event.getRawX(),event.getRawY()))return true;
        return false;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN -> {cityGesture=citySwipeArea(event);cityGestureClaimed=false;cityDownX=event.getRawX();cityDownY=event.getRawY();}
            case MotionEvent.ACTION_POINTER_DOWN -> {cityGesture=false;}
            case MotionEvent.ACTION_MOVE -> {
                if(cityGestureClaimed)return true;
                if(cityGesture){
                    float dx=event.getRawX()-cityDownX,dy=event.getRawY()-cityDownY;int slop=ViewConfiguration.get(this).getScaledTouchSlop();
                    if(Math.abs(dx)>slop && Math.abs(dx)>Math.abs(dy)){cityGesture=false;break;}
                    if(Math.abs(dy)>Math.max(dp(48),slop*3) && Math.abs(dy)>Math.abs(dx)*1.5f){
                        cityGesture=false;cityGestureClaimed=true;
                        MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();
                        nextCity(dy<0?1:-1);return true;
                    }
                }
            }
            case MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {boolean claimed=cityGestureClaimed;cityGesture=cityGestureClaimed=false;if(claimed)return true;}
        }
        return super.dispatchTouchEvent(event);
    }
    private void chooseCity(){
        if(cityDialog!=null && cityDialog.isShowing())return;
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(8),dp(16),dp(8));
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("City name");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);box.addView(input,new LinearLayout.LayoutParams(-1,-2));
        Button search=new Button(this);search.setText("Search cities");box.addView(search);
        TextView hint=new TextView(this);hint.setText("Search by city name; no location permission needed.");box.addView(hint);
        LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);ScrollView scroll=new ScrollView(this);scroll.addView(results);box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(240)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Choose city").setView(box).setNegativeButton("Cancel",null).create();cityDialog=dialog;
        Runnable submit=()->{
            String query=input.getText().toString().trim();if(query.length()<2){hint.setText("Enter at least two characters.");return;}
            int request=++searchGeneration;results.removeAllViews();hint.setText("Searching…");search.setEnabled(false);
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(),0);
            weather.search(query,(locations,error)->{
                if(isDestroyed() || !dialog.isShowing() || request!=searchGeneration)return;
                search.setEnabled(true);hint.setText(error!=null?error:locations.length()==0?"No cities found.":"Choose a result:");
                for(int i=0;i<locations.length();i++){
                    JSONObject location=locations.optJSONObject(i);if(location==null)continue;
                    Button choice=new Button(this);choice.setAllCaps(false);choice.setText(location.optString("name")+", "+location.optString("admin1")+", "+location.optString("country"));
                    choice.setOnClickListener(v->{try{selectCity(WeatherRepository.City.from(location),0);dialog.dismiss();}catch(JSONException e){hint.setText("That location is unavailable.");}});results.addView(choice,new LinearLayout.LayoutParams(-1,-2));
                }
            });
        };
        search.setOnClickListener(v->submit.run());input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){submit.run();return true;}return false;});
        dialog.setOnDismissListener(ignored->{searchGeneration++;cityDialog=null;});dialog.show();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
