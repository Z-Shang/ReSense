package dev.sense.launcher;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.view.WindowManager;
import android.view.inputmethod.*;
import android.widget.*;

/** Local search preferences and browser-only dispatch; no query requests from the launcher. */
final class BrowserSearch {
    static final String PREFERENCES="browser_search";
    enum Engine {
        GOOGLE("Google","https://www.google.com/search"),
        DUCKDUCKGO("DuckDuckGo","https://duckduckgo.com/"),
        BING("Bing","https://www.bing.com/search"),
        BRAVE("Brave","https://search.brave.com/search");
        final String label,base;
        Engine(String label,String base){this.label=label;this.base=base;}
    }
    static Engine engine(Context context){
        try{return Engine.valueOf(context.getSharedPreferences(PREFERENCES,0).getString("engine",Engine.GOOGLE.name()));}
        catch(IllegalArgumentException error){return Engine.GOOGLE;}
    }
    static void setEngine(Context context,Engine engine){context.getSharedPreferences(PREFERENCES,0).edit().putString("engine",engine.name()).apply();}
    static Uri uri(Engine engine,String query){
        String value=query==null?"":query.strip();if(value.isEmpty())throw new IllegalArgumentException("Empty search");
        return Uri.parse(engine.base).buildUpon().appendQueryParameter("q",value).build();
    }
    static Intent intent(Context context,Uri uri){
        PackageManager pm=context.getPackageManager();
        Intent view=new Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Resolve a neutral web origin, avoiding app-link handlers for a search engine's domain.
        Intent probe=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE);
        java.util.LinkedHashSet<String> packages=new java.util.LinkedHashSet<>();
        for(ResolveInfo candidate:pm.queryIntentActivities(probe,PackageManager.MATCH_DEFAULT_ONLY|PackageManager.GET_RESOLVED_FILTER)){
            if(candidate.activityInfo!=null && candidate.filter!=null && candidate.filter.countDataAuthorities()==0)
                packages.add(candidate.activityInfo.packageName);
        }
        Intent selector=Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_BROWSER);
        for(ResolveInfo candidate:pm.queryIntentActivities(selector,PackageManager.MATCH_DEFAULT_ONLY)){
            if(candidate.activityInfo!=null && !"android".equals(candidate.activityInfo.packageName)
                    && pm.resolveActivity(new Intent(view).setPackage(candidate.activityInfo.packageName),PackageManager.MATCH_DEFAULT_ONLY)!=null)
                packages.add(candidate.activityInfo.packageName);
        }
        ResolveInfo preferred=pm.resolveActivity(probe,PackageManager.MATCH_DEFAULT_ONLY);
        if(preferred!=null && preferred.activityInfo!=null && packages.contains(preferred.activityInfo.packageName))
            return view.setPackage(preferred.activityInfo.packageName);
        preferred=pm.resolveActivity(selector,PackageManager.MATCH_DEFAULT_ONLY);
        if(preferred!=null && preferred.activityInfo!=null && packages.contains(preferred.activityInfo.packageName))
            return view.setPackage(preferred.activityInfo.packageName);
        if(packages.isEmpty())throw new ActivityNotFoundException("No browser installed");
        java.util.ArrayList<Intent> choices=new java.util.ArrayList<>();
        for(String name:packages)choices.add(new Intent(view).setPackage(name));
        Intent primary=choices.remove(0);
        if(choices.isEmpty())return primary;
        return Intent.createChooser(primary,"Search in browser").putExtra(Intent.EXTRA_INITIAL_INTENTS,choices.toArray(new Intent[0])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    static boolean launch(Context context,String query){
        if(query==null || query.isBlank())return false;
        try{context.startActivity(intent(context,uri(engine(context),query)));return true;}
        catch(ActivityNotFoundException error){Toast.makeText(context,"No browser available",Toast.LENGTH_LONG).show();return false;}
    }
    static void showSettings(Context context){LauncherSettingsActivity.open(context,"search");}
    static AlertDialog showQuery(Context context){
        EditText query=new EditText(context);query.setSingleLine(true);query.setTextSize(18);
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setHint("Search with "+engine(context).label);
        FrameLayout box=new FrameLayout(context);int padding=Math.round(20*context.getResources().getDisplayMetrics().density);box.setPadding(padding,padding/2,padding,padding/2);box.addView(query,new FrameLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(context).setTitle("Search with "+engine(context).label).setView(box).setPositiveButton("Search",null).setNegativeButton("Cancel",null).create();
        Runnable submit=()->{if(launch(context,query.getText().toString()))dialog.dismiss();};
        query.setOnEditorActionListener((view,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH || (event!=null && event.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER && event.getAction()==android.view.KeyEvent.ACTION_UP)){submit.run();return true;}return false;});
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        Button search=dialog.getButton(AlertDialog.BUTTON_POSITIVE);search.setEnabled(false);search.setOnClickListener(v->submit.run());
            query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence text,int start,int count,int after){}public void onTextChanged(CharSequence text,int start,int before,int count){search.setEnabled(!text.toString().isBlank());}public void afterTextChanged(android.text.Editable text){}});
        query.requestFocus();return dialog;
    }
}
