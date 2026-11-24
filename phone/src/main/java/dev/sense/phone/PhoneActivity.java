package dev.sense.phone;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.app.NotificationManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import java.util.ArrayList;

/** Native Pyramid keypad and user-approved default Phone setup. */
public final class PhoneActivity extends Activity {
    private TextView number;
    private LinearLayout root, body;
    private ImageButton options;
    private dev.sense.framework.SenseScreenHost screenHost;
    private String displayedScreen;
    private String setupLabel;
    private Runnable setupAction;
    private boolean keypadVisible=true;
    private long groupId=-1;
    private String groupTitle="Group";
    static boolean visible;
    private String pendingNumber;
    private final ArrayList<TextView> tabViews = new ArrayList<>();
    private String digits = "", tab = "Keypad";
    private int generation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN,dev.sense.framework.R.anim.sense_screen_open_enter,dev.sense.framework.R.anim.sense_screen_hold);
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,dev.sense.framework.R.anim.sense_screen_hold,dev.sense.framework.R.anim.sense_screen_close_exit);
        if (state != null) { digits = state.getString("digits", ""); tab = state.getString("tab", "Keypad"); pendingNumber=state.getString("pendingNumber");keypadVisible=state.getBoolean("keypadVisible",true);groupId=state.getLong("groupId",-1);groupTitle=state.getString("groupTitle","Group"); }
        if (getIntent().getData() != null && "tel".equals(getIntent().getData().getScheme())) digits = getIntent().getData().getSchemeSpecificPart();
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom); return insets;
        });
        setContentView(root);
        FrameLayout workspace=new FrameLayout(this);
        screenHost=new dev.sense.framework.SenseScreenHost(this);workspace.addView(screenHost,new FrameLayout.LayoutParams(-1,-1));
        options=dev.sense.framework.SenseHeaderIconButton.overflow(this,"Phone options");options.setOnClickListener(this::showOptions);
        FrameLayout.LayoutParams menuParams=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);workspace.addView(options,menuParams);
        root.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
        dev.sense.framework.SenseCarouselTabs tabs=new dev.sense.framework.SenseCarouselTabs(this);
        String[] names={"Phone","People","Groups","Call History"};String[] keys={"Keypad","Contacts","Groups","Call History"};
        int[] icons={dev.sense.framework.R.drawable.sense_common_icon_phone_rest,dev.sense.framework.R.drawable.sense_common_icon_all_people_rest,dev.sense.framework.R.drawable.sense_common_icon_group_rest,dev.sense.framework.R.drawable.sense_common_icon_call_history_rest};
        for(int i=0;i<names.length;i++){
            int[] activeIcons={dev.sense.framework.R.drawable.sense_common_icon_phone_on,dev.sense.framework.R.drawable.sense_common_icon_all_people_on,dev.sense.framework.R.drawable.sense_common_icon_group_on,dev.sense.framework.R.drawable.sense_common_icon_call_history_on};
            String key=keys[i];TextView button=tabs.addTab(names[i],icons[i],activeIcons[i],tab.equals(key),()->{tab=key;groupId=-1;render();});button.setTag(key);if(key.equals("Keypad")){button.setTooltipText("Phone · hold for options");button.setOnLongClickListener(v->{showOptions(v);return true;});}tabViews.add(button);
        }
        root.addView(tabs,new LinearLayout.LayoutParams(-1,dp(64)));updateSetup();render();
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{if(tab.equals("Groups")&&groupId>=0){groupId=-1;render();}else if(!tab.equals("Keypad")){tab="Keypad";render();}else finish();});
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private StateListDrawable controlBackground(int rest, int selected, int pressed) {
        return dev.sense.framework.SenseStyle.background(this,rest,selected,pressed);
    }
    private void render() {
        for (TextView button : tabViews) button.setSelected(tab.equals(button.getTag()));
        String screen=tab+":"+groupId;boolean changed=displayedScreen!=null&&!displayedScreen.equals(screen);
        boolean forward=!tab.equals("Keypad")&&!(tab.equals("Groups")&&groupId<0&&displayedScreen!=null&&displayedScreen.startsWith("Groups:"));
        generation++;body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setBackgroundColor(tab.equals("Keypad")?Color.BLACK:0xfffafafa);options.setVisibility(tab.equals("Keypad")?View.VISIBLE:View.GONE);
        if (tab.equals("Keypad")) keypad(); else if(tab.equals("Contacts")) showContacts(); else if(tab.equals("Groups"))showGroups();else showCallHistory();
        screenHost.show(body,changed,forward);displayedScreen=screen;
    }
    private void keypad() {
        body.setBackgroundColor(Color.BLACK);
        LinearLayout keypadBody=new LinearLayout(this);keypadBody.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.addView(keypadBody,new ScrollView.LayoutParams(-1,-2));body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        keypadBody.addView(new Space(this),new LinearLayout.LayoutParams(-1,0,1));
        scroll.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->options.setVisibility(tab.equals("Keypad")&&b-t>dp(56+256+48)?View.VISIBLE:View.GONE));
        FrameLayout display = new FrameLayout(this);display.setBackgroundResource(R.drawable.dial_phone_accumulator_bg);display.setPadding(0,0,0,0);
        LinearLayout input=new LinearLayout(this);input.setOrientation(LinearLayout.VERTICAL);input.setGravity(Gravity.CENTER);
        number=label(digits,28,Color.WHITE);number.setHint("Enter name or number");number.setHintTextColor(Color.WHITE);number.setGravity(Gravity.CENTER);number.setSingleLine();number.setHorizontallyScrolling(true);number.setIncludeFontPadding(false);number.setTextSize(digits.isEmpty()?18:28);
        number.setOnClickListener(v->{tab="Contacts";render();});input.addView(number,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=label("Tap for dialing tips",16,0xffbcbcbc);hint.setGravity(Gravity.CENTER);hint.setIncludeFontPadding(false);hint.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Dialing tips").setMessage("Use the keypad to enter a number. Hold 0 for +. Tap the number field or People to search contacts. Hold Delete to clear the number. Voice input fills the number for you to review before calling.").setPositiveButton("OK",null).show());input.addView(hint,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout.LayoutParams inputParams=new FrameLayout.LayoutParams(-1,dp(60),Gravity.CENTER);inputParams.setMargins(dp(56),0,dp(56),0);display.addView(input,inputParams);
        ImageButton erase=iconButton(R.drawable.dial_phone_icon_backspace_rest,"Delete digit");erase.setOnClickListener(v->{if(!digits.isEmpty())setDigits(digits.substring(0,digits.length()-1));});erase.setOnLongClickListener(v->{setDigits("");return true;});
        FrameLayout.LayoutParams eraseParams=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.CENTER_VERTICAL|Gravity.END);eraseParams.setMargins(dp(6),dp(6),dp(6),dp(6));display.addView(erase,eraseParams);keypadBody.addView(display);
        LinearLayout pad=new LinearLayout(this);pad.setOrientation(LinearLayout.VERTICAL);pad.setVisibility(keypadVisible?View.VISIBLE:View.GONE);
        String[] letters={"","ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ","","+",""};
        String[] keys={"1","2","3","4","5","6","7","8","9","*","0","#"};
        for(int row=0; row<4; row++) {
            LinearLayout line=new LinearLayout(this);line.setPadding(dp(4),0,dp(4),0);
            for(int col=0;col<3;col++) {
                int index=row*3+col; String key=keys[index];
                LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setBackground(controlBackground(R.drawable.dial_htc_pin_asset_rest,R.drawable.dial_htc_pin_asset_rest,R.drawable.dial_htc_pin_asset_pressed));tile.setPadding(0,0,0,0);
                TextView digit = label(key,31,Color.BLACK); digit.setGravity(Gravity.CENTER);digit.setIncludeFontPadding(false);digit.setShadowLayer(1,0,1,Color.WHITE);
                TextView alphabet = label(letters[index],14,0xff797979); alphabet.setGravity(Gravity.CENTER);alphabet.setIncludeFontPadding(false);
                if(key.equals("0")){
                    tile.setOrientation(LinearLayout.HORIZONTAL);tile.addView(digit,new LinearLayout.LayoutParams(-2,-2));alphabet.setTextSize(24);tile.addView(alphabet,new LinearLayout.LayoutParams(-2,-2));
                }else{
                    tile.addView(digit,new LinearLayout.LayoutParams(-1,-2));
                    if(key.equals("1")){ImageView voicemail=new ImageView(this);voicemail.setImageResource(dev.sense.framework.R.drawable.sense_common_icon_voicemail_rest);voicemail.setScaleType(ImageView.ScaleType.CENTER_INSIDE);tile.addView(voicemail,new LinearLayout.LayoutParams(dp(28),dp(16)));}
                    else if(!letters[index].isEmpty())tile.addView(alphabet,new LinearLayout.LayoutParams(-1,-2));
                } tile.setContentDescription(key+" "+letters[index]);tile.setFocusable(true);tile.setClickable(true);
                tile.setOnClickListener(v->{ v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); setDigits(digits+key); });
                if(key.equals("0")) tile.setOnLongClickListener(v->{setDigits(digits+"+");return true;});
                LinearLayout.LayoutParams keyParams=new LinearLayout.LayoutParams(0,dp(60),1);keyParams.setMargins(dp(3),dp(2),dp(3),dp(2));line.addView(tile,keyParams);
            }
            pad.addView(line);
        }
        keypadBody.addView(pad);
        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setBackgroundResource(R.drawable.dial_phone_bg_taskbar);
        actions.setPadding(dp(4), dp(4), dp(4), dp(4));
        ImageButton toggle=iconButton(R.drawable.dial_phone_icon_keypad_rest,"Hide keypad");toggle.setOnClickListener(v->{keypadVisible=!keypadVisible;pad.setVisibility(keypadVisible?View.VISIBLE:View.GONE);toggle.setContentDescription(keypadVisible?"Hide keypad":"Show keypad");});toggle.setContentDescription(keypadVisible?"Hide keypad":"Show keypad");
        LinearLayout.LayoutParams toggleParams=new LinearLayout.LayoutParams(dp(52),dp(56));toggleParams.setMarginEnd(dp(6));actions.addView(toggle,toggleParams);
        Button call=new Button(this);call.setText("Call");call.setTextSize(26);call.setShadowLayer(1,0,1,Color.BLACK);call.setTextColor(Color.WHITE);call.setAllCaps(false);
        StateListDrawable background=new StateListDrawable();background.addState(new int[]{android.R.attr.state_pressed},getDrawable(R.drawable.dial_phone_btn_call_pressed));background.addState(new int[]{},getDrawable(R.drawable.dial_phone_btn_call_rest));call.setBackground(background);
        call.setOnClickListener(v->handoff());actions.addView(call,new LinearLayout.LayoutParams(0,dp(56),1));
        ImageButton voice=iconButton(R.drawable.dial_phone_icon_voice_rest,"Voice dial");voice.setOnClickListener(v->{try{startActivityForResult(new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,"Say a phone number"),11);}catch(android.content.ActivityNotFoundException error){Toast.makeText(this,"No voice input app available",Toast.LENGTH_SHORT).show();}});
        LinearLayout.LayoutParams voiceParams=new LinearLayout.LayoutParams(dp(52),dp(56));voiceParams.setMarginStart(dp(6));actions.addView(voice,voiceParams);body.addView(actions);
    }
    private void setDigits(String value) { digits=value; if(number!=null){number.setText(value);number.setTextSize(value.isEmpty()?18:28);} }
    private ImageButton iconButton(int resource,String description){ImageButton button=new ImageButton(this);button.setImageResource(resource);button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);button.setPadding(dp(8),dp(8),dp(8),dp(8));button.setBackground(controlBackground(R.drawable.dial_phone_btn_backspace_rest,R.drawable.dial_phone_btn_backspace_selected,R.drawable.dial_phone_btn_backspace_pressed));button.setContentDescription(description);button.setTooltipText(description);return button;}
    private void showOptions(View anchor){
        updateSetup();PopupMenu popup=new PopupMenu(this,anchor,Gravity.END);
        if(setupAction!=null)popup.getMenu().add(setupLabel).setOnMenuItemClickListener(item->{setupAction.run();return true;});
        popup.getMenu().add("Add contact").setOnMenuItemClickListener(item->{launch(new Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE).putExtra(ContactsContract.Intents.Insert.PHONE,digits));return true;});
        popup.getMenu().add("Default Phone settings").setOnMenuItemClickListener(item->{launch(new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));return true;});popup.show();
    }
    private boolean defaultPhone(){RoleManager roles=getSystemService(RoleManager.class);return roles.isRoleAvailable(RoleManager.ROLE_DIALER)&&roles.isRoleHeld(RoleManager.ROLE_DIALER);}
    private void updateSetup(){
        setupLabel=null;setupAction=null;RoleManager roles=getSystemService(RoleManager.class);
        String title;Runnable action;
        if(roles.isRoleAvailable(RoleManager.ROLE_DIALER)&&!defaultPhone()){
            title="Make default Phone";action=()->startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER),10);
        }else if(defaultPhone()&&!getSystemService(NotificationManager.class).areNotificationsEnabled()){
            title="Enable call notifications";action=()->{if(shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)||!getPreferences(0).getBoolean("notificationsRequested",false)){getPreferences(0).edit().putBoolean("notificationsRequested",true).apply();requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},4);}else launch(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));};
        }else if(defaultPhone()&&!getSystemService(NotificationManager.class).canUseFullScreenIntent()){
            title="Enable incoming call screen";action=()->launch(new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.fromParts("package",getPackageName(),null)));
        }else return;
        setupLabel=title;setupAction=action;
    }
    @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);if(request==11&&result==RESULT_OK&&data!=null){var results=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(results!=null&&!results.isEmpty()){String value=results.get(0).replaceAll("[^0-9+*#]","");if(!value.isBlank()){tab="Keypad";setDigits(value);render();}else Toast.makeText(this,"Say a phone number",Toast.LENGTH_SHORT).show();}}if(request==10){updateSetup();if(defaultPhone()){
        ArrayList<String> permissions=new ArrayList<>();for(String permission:new String[]{android.Manifest.permission.CALL_PHONE,android.Manifest.permission.READ_PHONE_STATE,android.Manifest.permission.POST_NOTIFICATIONS})if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)permissions.add(permission);
        if(!permissions.isEmpty()){getPreferences(0).edit().putBoolean("notificationsRequested",true).apply();requestPermissions(permissions.toArray(new String[0]),4);}
    }}}
    private void handoff(){
        if(digits.isBlank())return;
        if(!defaultPhone()){systemDialer(digits);return;}
        if(checkSelfPermission(android.Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){
            if(getPreferences(0).getBoolean("callPermissionRequested",false)&&!shouldShowRequestPermissionRationale(android.Manifest.permission.CALL_PHONE)){
                Toast.makeText(this,"Allow Phone permission to place calls",Toast.LENGTH_LONG).show();launch(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)));return;
            }
            pendingNumber=digits;getPreferences(0).edit().putBoolean("callPermissionRequested",true).apply();requestPermissions(new String[]{android.Manifest.permission.CALL_PHONE},3);return;
        }
        place(digits);
    }
    private void place(String number){
        if(!defaultPhone()){systemDialer(number);return;}
        try{getSystemService(TelecomManager.class).placeCall(Uri.fromParts("tel",number,null),new Bundle());}
        catch(SecurityException|IllegalArgumentException error){Toast.makeText(this,"Call could not be started. Check Phone permission and your SIM.",Toast.LENGTH_LONG).show();}
    }
    private void systemDialer(String value){
        TelecomManager telecom=getSystemService(TelecomManager.class);String system=telecom.getDefaultDialerPackage();if(system==null||system.equals(getPackageName()))system=telecom.getSystemDialerPackage();
        if(system==null||system.equals(getPackageName())){Toast.makeText(this,"Choose a default Phone app to make calls",Toast.LENGTH_LONG).show();return;}
        launch(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",value,null)).setPackage(system));
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(Intent.ACTION_DIAL.equals(intent.getAction())||(intent.getData()!=null&&"tel".equals(intent.getData().getScheme()))){if(intent.getData()!=null&&"tel".equals(intent.getData().getScheme()))digits=intent.getData().getSchemeSpecificPart();tab="Keypad";keypadVisible=true;render();}}
    private void launch(Intent intent) { try{startActivity(intent);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"No compatible app installed",Toast.LENGTH_SHORT).show();} }
    private void showContacts() {
        body.setBackgroundColor(0xfffafafa);
        body.addView(new ContactsView(this, () -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 1), value -> {
            digits = value; tab = "Keypad"; render();
        }), new LinearLayout.LayoutParams(-1, -1));
    }
    private void showGroups(){
        if(groupId<0){body.addView(new GroupsView(this,()->requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},1),group->{groupId=group.id();groupTitle=group.title();render();}),new LinearLayout.LayoutParams(-1,-1));return;}
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackgroundColor(0xff353b36);
        ImageButton back=dev.sense.framework.SenseHeaderIconButton.back(this,"Back to groups");back.setOnClickListener(v->{groupId=-1;render();});header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));ImageView badge=new ImageView(this);badge.setImageResource(dev.sense.framework.R.drawable.sense_common_icon_default_group);badge.setScaleType(ImageView.ScaleType.CENTER_INSIDE);header.addView(badge,new LinearLayout.LayoutParams(dp(40),dp(40)));TextView title=label(groupTitle,22,Color.WHITE);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(dp(8),0,dp(12),0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));body.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        body.addView(new ContactsView(this,()->requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},1),value->{digits=value;tab="Keypad";render();},groupId),new LinearLayout.LayoutParams(-1,0,1));
    }
    private void showCallHistory() {
        body.setBackgroundColor(0xfffafafa);
        CallHistoryView history = new CallHistoryView(this, () -> requestPermissions(
                new String[]{Manifest.permission.READ_CALL_LOG}, 2), value -> {
            digits = value; tab = "Keypad"; render();
        });
        body.addView(history, new LinearLayout.LayoutParams(-1, -1));
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==1 || request==2)render();if(request==3){String number=pendingNumber;pendingNumber=null;if(number!=null&&checkSelfPermission(android.Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED)place(number);}updateSetup();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("digits",digits);out.putString("tab",tab);out.putString("pendingNumber",pendingNumber);out.putBoolean("keypadVisible",keypadVisible);out.putLong("groupId",groupId);out.putString("groupTitle",groupTitle);}
    @Override protected void onResume(){super.onResume();visible=true;updateSetup();if(body!=null && (tab.equals("Contacts") || tab.equals("Groups") || tab.equals("Call History")))render();}
    @Override protected void onPause(){visible=false;super.onPause();}
    @Override protected void onDestroy(){generation++;super.onDestroy();}
}
