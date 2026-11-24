package dev.sense.launcher;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import dev.sense.framework.*;
import java.util.*;
import java.util.concurrent.*;

/** Personalize catalogue followed by Rosie's explicit, horizontal style chooser. */
public final class WidgetPickerActivity extends Activity {
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private List<WidgetPicker.Family> families=List.of();
    private SenseScreenHost host;
    private WidgetPicker.Family family;
    private String query="",restoreKey;
    private int index,columns,rows,cellWidth,cellHeight,catalogueY;
    private LinearLayout catalogue;
    private ScrollView catalogueScroll;
    private TextView counter;
    private Filmstrip filmstrip;
    private View previous,next;
    private boolean destroyed;
    private final Set<String> collapsedProviders=new HashSet<>(),searchCollapsedProviders=new HashSet<>();
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        columns=Math.max(1,getIntent().getIntExtra("columns",4));rows=Math.max(1,getIntent().getIntExtra("rows",6));
        cellWidth=Math.max(1,getIntent().getIntExtra("cellWidth",dp(90)));cellHeight=Math.max(1,getIntent().getIntExtra("cellHeight",dp(100)));
        if(state!=null){query=state.getString("query","");restoreKey=state.getString("family");index=state.getInt("index");catalogueY=state.getInt("scroll");}
        if(state!=null && state.getStringArrayList("collapsedProviders")!=null)collapsedProviders.addAll(state.getStringArrayList("collapsedProviders"));
        host=new SenseScreenHost(this);host.setBackgroundColor(Color.BLACK);setContentView(host);
        host.setOnApplyWindowInsetsListener((v,insets)->{var bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        getWindow().setDecorFitsSystemWindows(false);
        showCatalogue(false);
        loader.execute(()->{
            List<WidgetPicker.Family> result;
            try{result=WidgetPicker.families(this,AppWidgetManager.getInstance(this).getInstalledProviders());}
            catch(RuntimeException failure){result=WidgetPicker.families(this,List.of());}
            final var loaded=result;
            runOnUiThread(()->{if(destroyed)return;families=loaded;populate();if(restoreKey!=null){for(var candidate:families)if(candidate.key().equals(restoreKey)){family=candidate;showStyles(false);break;}restoreKey=null;}});
        });
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
    }
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private LinearLayout screen(String title){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.BLACK);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff646964,0xff242a25}));
        var back=SenseHeaderIconButton.back(this,"Back");back.setOnClickListener(v->back());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(56)));
        TextView heading=text(title,22,Color.WHITE);heading.setGravity(Gravity.CENTER);heading.setAccessibilityHeading(true);header.addView(heading,new LinearLayout.LayoutParams(0,dp(56),1));header.addView(new View(this),new LinearLayout.LayoutParams(dp(48),1));
        root.addView(header);return root;
    }
    private void showCatalogue(boolean animate){
        family=null;LinearLayout root=screen("Widgets");
        SenseSearchBox search=new SenseSearchBox(this,"Search widgets","Clear search");search.getTextField().setText(query);root.addView(search,new LinearLayout.LayoutParams(-1,dp(56)));
        catalogueScroll=new ScrollView(this);catalogue=new LinearLayout(this);catalogue.setOrientation(LinearLayout.VERTICAL);catalogueScroll.addView(catalogue);root.addView(catalogueScroll,new LinearLayout.LayoutParams(-1,0,1));
        search.getTextField().addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){query=s.toString();searchCollapsedProviders.clear();catalogueY=0;populate();}public void afterTextChanged(Editable e){}});
        populate();host.show(root,animate,false);catalogueScroll.post(()->catalogueScroll.scrollTo(0,catalogueY));
    }
    private void populate(){
        if(catalogue==null)return;catalogue.removeAllViews();String filter=query.toLowerCase(Locale.getDefault());
        for(var provider:WidgetPicker.providerGroups(this,families)){
            var matches=new ArrayList<WidgetPicker.Family>();
            for(var item:provider.families())if((provider.name()+" "+item.name()+" "+item.application()).toLowerCase(Locale.getDefault()).contains(filter))matches.add(item);
            if(matches.isEmpty())continue;
            Set<String> collapsed=filter.isBlank()?collapsedProviders:searchCollapsedProviders;boolean expanded=!collapsed.contains(provider.key());
            TextView heading=SenseStyle.section(this,provider.name()+"  ("+matches.size()+")");heading.setTag("widget-provider:"+provider.key());heading.setMinimumHeight(dp(48));heading.setGravity(Gravity.CENTER_VERTICAL);heading.setFocusable(true);heading.setAccessibilityHeading(true);
            var arrow=getDrawable(dev.sense.framework.R.drawable.sense_common_icon_glance_next_rest).mutate();arrow.setBounds(0,0,dp(20),dp(20));
            heading.setCompoundDrawablesRelative(null,null,arrow,null);heading.setContentDescription(provider.name()+", "+matches.size()+" widgets, "+(expanded?"expanded":"collapsed"));
            if(expanded){var down=getDrawable(dev.sense.framework.R.drawable.sense_common_icon_glance_down_rest);down.setBounds(0,0,dp(20),dp(20));heading.setCompoundDrawablesRelative(null,null,down,null);}
            heading.setOnClickListener(v->{int scroll=catalogueScroll.getScrollY();if(!collapsed.add(provider.key()))collapsed.remove(provider.key());populate();catalogueScroll.post(()->catalogueScroll.scrollTo(0,scroll));});catalogue.addView(heading);
            if(!expanded)continue;
        for(var item:matches){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(10),dp(8));android.graphics.drawable.StateListDrawable rowBackground=new android.graphics.drawable.StateListDrawable();rowBackground.addState(new int[]{android.R.attr.state_pressed},getDrawable(dev.sense.framework.R.drawable.sense_list_selector_background_pressed));rowBackground.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));row.setBackground(rowBackground);row.setMinimumHeight(dp(72));row.setFocusable(true);row.setClickable(true);
            ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
            if(item.key().equals("sense:music"))icon.setImageResource(R.drawable.sense_music_preview_small);
            else if(item.key().equals("sense:search"))icon.setImageResource(R.drawable.sense_search_icon);
            else if(item.key().equals("sense:weather"))icon.setImageResource(WeatherWidgetAssets.preview(this,"2x2"));
            else loader.execute(()->{android.graphics.drawable.Drawable artwork=null;try{artwork=item.styles().get(0).info().loadIcon(this,getResources().getDisplayMetrics().densityDpi);}catch(RuntimeException ignored){}final var result=artwork;runOnUiThread(()->{if(!destroyed)icon.setImageDrawable(result);});});
            LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(14),0,dp(8),0);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            TextView title=text(item.name(),20,Color.WHITE);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);labels.addView(title);
            TextView subtitle=text(item.styles().size()+ (item.styles().size()==1?" style":" styles"),14,0xffc4c4c4);subtitle.setSingleLine(true);subtitle.setEllipsize(TextUtils.TruncateAt.END);labels.addView(subtitle);
            ImageView chevron=new ImageView(this);chevron.setImageResource(dev.sense.framework.R.drawable.sense_home_widget_picker_arrow_right);chevron.setScaleType(ImageView.ScaleType.CENTER);row.addView(chevron,new LinearLayout.LayoutParams(dp(24),dp(32)));
            row.setOnClickListener(v->{catalogueY=catalogueScroll.getScrollY();family=item;index=0;((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(row.getWindowToken(),0);showStyles(true);});catalogue.addView(row);
            View divider=new View(this);divider.setBackgroundResource(dev.sense.framework.R.drawable.sense_common_list_divider);catalogue.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        }
        if(catalogue.getChildCount()==0){TextView empty=text(families.isEmpty()?"Loading widgets…":"No matching widgets",18,0xffc4c4c4);empty.setPadding(dp(20),dp(24),dp(20),0);catalogue.addView(empty);}
    }
    private int[] size(WidgetPicker.Style style){
        int w=style.info()==null?4:(int)Math.ceil(style.info().minWidth/(double)cellWidth);
        int h=style.info()==null?(style.music()?style.musicRows():1):(int)Math.ceil(style.info().minHeight/(double)cellHeight);
        if(style.weather()){w=Integer.parseInt(style.weatherStyle().substring(0,1));h=Integer.parseInt(style.weatherStyle().substring(2,3));}
        return new int[]{Math.min(columns,Math.max(1,w)),Math.min(rows,Math.max(1,h))};
    }
    private void showStyles(boolean animate){
        index=Math.min(Math.max(0,index),family.styles().size()-1);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.BLACK);
        TextView name=text(family.name(),16,Color.WHITE);name.setGravity(Gravity.CENTER);root.addView(name,new LinearLayout.LayoutParams(-1,dp(36)));
        LinearLayout navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER);
        previous=styleArrow(true,"Previous style");previous.setOnClickListener(v->filmstrip.select(index-1,true));navigation.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));
        counter=text("",12,0xffc4c4c4);counter.setGravity(Gravity.CENTER);navigation.addView(counter,new LinearLayout.LayoutParams(dp(56),dp(48)));
        next=styleArrow(false,"Next style");next.setOnClickListener(v->filmstrip.select(index+1,true));navigation.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(navigation);
        filmstrip=new Filmstrip();root.addView(filmstrip,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(this);footer.setPadding(dp(8),dp(6),dp(8),dp(6));android.graphics.drawable.BitmapDrawable texture=(android.graphics.drawable.BitmapDrawable)getDrawable(dev.sense.framework.R.drawable.sense_common_app_bkg_src);texture.setTileModeXY(android.graphics.Shader.TileMode.REPEAT,android.graphics.Shader.TileMode.REPEAT);footer.setBackground(texture);
        Button details=new SensePreferences.ButtonControl(this);details.setText("Details");details.setOnClickListener(v->details());footer.addView(details,new LinearLayout.LayoutParams(0,dp(48),1));
        Button select=new SensePreferences.ButtonControl(this);select.setText("Select");select.setOnClickListener(v->choose());LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1);params.leftMargin=dp(8);footer.addView(select,params);root.addView(footer);
        host.show(root,animate,true);updateCounter();
    }
    private ImageButton styleArrow(boolean left,String description){
        ImageButton button=new ImageButton(this);button.setImageResource(left?dev.sense.framework.R.drawable.sense_home_widget_picker_arrow_left:dev.sense.framework.R.drawable.sense_home_widget_picker_arrow_right);button.setScaleType(ImageView.ScaleType.CENTER);button.setBackgroundColor(Color.TRANSPARENT);button.setContentDescription(description);button.setTooltipText(description);return button;
    }
    private void updateCounter(){int[] size=size(family.styles().get(index));counter.setText((index+1)+" / "+family.styles().size());counter.setContentDescription("Style "+(index+1)+" of "+family.styles().size()+", "+size[0]+" by "+size[1]);previous.setEnabled(index>0);next.setEnabled(index+1<family.styles().size());previous.setAlpha(index>0?1:.3f);next.setAlpha(index+1<family.styles().size()?1:.3f);}
    private void details(){var style=family.styles().get(index);int[] size=size(style);String description=style.music()?"Control the currently playing music.":style.search()?"Search the web using your chosen search engine.":style.weather()?"Original HTC weather artwork with shared city and forecast data.":"";if(style.info()!=null){CharSequence value=style.info().loadDescription(this);if(value!=null)description=value.toString();}new AlertDialog.Builder(this).setTitle(family.name()).setMessage(family.application()+"\n"+size[0]+" × "+size[1]+"\n\n"+description).setPositiveButton("OK",null).show();}
    private void choose(){var style=family.styles().get(index);Intent result=new Intent();if(style.music())result.putExtra("widget_kind","music").putExtra("musicRows",style.musicRows());else if(style.search())result.putExtra("widget_kind","search");else if(style.weather())result.putExtra("widget_kind","weather").putExtra("weatherStyle",style.weatherStyle());else result.putExtra("widget_kind","provider").putExtra("provider",style.info().provider).putExtra("profile",style.info().getProfile());setResult(RESULT_OK,result);finish();}
    private void back(){if(family!=null)showCatalogue(true);else{setResult(RESULT_CANCELED);finish();}}
    @Override public void onBackPressed(){back();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("query",query);state.putString("family",family==null?null:family.key());state.putInt("index",index);state.putInt("scroll",family==null?catalogueScroll.getScrollY():catalogueY);state.putStringArrayList("collapsedProviders",new ArrayList<>(collapsedProviders));}
    @Override protected void onDestroy(){destroyed=true;loader.shutdownNow();super.onDestroy();}
    private final class Filmstrip extends HorizontalScrollView {
        final LinearLayout strip;int pageWidth;boolean flung;
        Filmstrip(){super(WidgetPickerActivity.this);setHorizontalScrollBarEnabled(false);setClipChildren(true);setClipToPadding(true);setFillViewport(true);strip=new LinearLayout(WidgetPickerActivity.this);addView(strip,new LayoutParams(-2,-1));for(var style:family.styles())strip.addView(preview(style),new LinearLayout.LayoutParams(1,-1));}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            super.onMeasure(widthSpec,heightSpec);
            int width=getMeasuredWidth(),height=getMeasuredHeight();
            for(int i=0;i<strip.getChildCount();i++){View child=strip.getChildAt(i);var params=child.getLayoutParams();params.width=width;params.height=height;}
            strip.measure(MeasureSpec.makeMeasureSpec(width*strip.getChildCount(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);pageWidth=w;for(int i=0;i<strip.getChildCount();i++)strip.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(w,-1));post(()->select(index,false));}
        void select(int selected,boolean animate){if(family==null||filmstrip!=this)return;index=Math.max(0,Math.min(family.styles().size()-1,selected));if(animate)smoothScrollTo(index*pageWidth,0);else scrollTo(index*pageWidth,0);updateCounter();}
        @Override public void fling(int velocity){flung=true;select(Math.round(getScrollX()/(float)Math.max(1,pageWidth))+(Math.abs(velocity)>400?(velocity>0?1:-1):0),true);}
        @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)flung=false;boolean handled=super.onTouchEvent(event);if(!flung&&(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL))select(Math.round(getScrollX()/(float)Math.max(1,pageWidth)),true);return handled;}
    }
    private View preview(WidgetPicker.Style style){
        FrameLayout frame=new FrameLayout(this){@Override public boolean onInterceptTouchEvent(MotionEvent event){return true;}};frame.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);frame.setPadding(dp(20),dp(16),dp(20),dp(16));
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);frame.addView(image,params);
        if(style.music())image.setImageResource(style.musicRows()==1?R.drawable.sense_music_preview_small:style.musicRows()==4?R.drawable.sense_music_preview_large:R.drawable.sense_music_preview);
        else if(style.weather())image.setImageResource(WeatherWidgetAssets.preview(this,style.weatherStyle()));
        else if(style.search()){frame.addView(new SenseSearchView(this),new FrameLayout.LayoutParams(-1,dp(76),Gravity.CENTER));}
        else loader.execute(()->{android.graphics.drawable.Drawable artwork=null;try{artwork=style.info().loadPreviewImage(WidgetPickerActivity.this,getResources().getDisplayMetrics().densityDpi);if(artwork==null)artwork=style.info().loadIcon(WidgetPickerActivity.this,getResources().getDisplayMetrics().densityDpi);}catch(RuntimeException ignored){}final var drawable=artwork;runOnUiThread(()->{if(!destroyed)image.setImageDrawable(drawable);});});
        return frame;
    }
}
