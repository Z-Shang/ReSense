package dev.sense.phone;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.telecom.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import dev.sense.framework.SenseButton;

/** Incoming and ongoing Telecom calls, using stock Phone.apk artwork. */
public final class InCallActivity extends Activity {
    private LinearLayout root;
    private String token;
    private boolean keypad;
    private final Runnable changed=this::render;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);overrideActivityTransition(OVERRIDE_TRANSITION_OPEN,dev.sense.framework.R.anim.sense_screen_open_enter,dev.sense.framework.R.anim.sense_screen_hold);overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,dev.sense.framework.R.anim.sense_screen_hold,dev.sense.framework.R.anim.sense_screen_close_exit);setShowWhenLocked(true);setTurnScreenOn(true);if(state!=null){token=state.getString("token");keypad=state.getBoolean("keypad");}intent(getIntent());}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);intent(intent);render();}
    private void intent(Intent intent){String requested=intent.getStringExtra(SenseInCallService.TOKEN);if(requested!=null)token=requested;if(intent.getBooleanExtra("keypad",false))keypad=true;if(SenseInCallService.ANSWER.equals(intent.getAction())){SenseInCallService service=SenseInCallService.current();if(service!=null)service.answer(requested);intent.setAction(Intent.ACTION_VIEW);}}
    @Override protected void onStart(){super.onStart();SenseInCallService.listen(changed);render();}
    @Override protected void onStop(){SenseInCallService.unlisten(changed);super.onStop();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(Color.WHITE);view.setGravity(Gravity.CENTER);view.setPadding(dp(12),dp(5),dp(12),dp(5));return view;}
    private Button action(String value,Runnable action){SenseButton button=new SenseButton(this);button.setText(value);button.setOnClickListener(v->action.run());return button;}
    private Button colored(String value,int resource,Runnable action){Button button=action(value,action);button.setBackgroundTintList(null);button.setBackgroundResource(resource);button.setTextColor(Color.WHITE);return button;}
    private Button iconAction(String label,int rest,int on,boolean selected,Runnable callback){
        Button button=action(label,callback);
        android.graphics.drawable.StateListDrawable icon=new android.graphics.drawable.StateListDrawable();
        icon.addState(new int[]{android.R.attr.state_selected},getDrawable(on));
        icon.addState(new int[]{android.R.attr.state_pressed},getDrawable(on));
        icon.addState(new int[]{},getDrawable(rest));
        icon.setBounds(0,0,dp(28),dp(28));button.setCompoundDrawables(null,icon,null,null);
        button.setCompoundDrawablePadding(dp(3));button.setTextSize(13);
        android.graphics.drawable.StateListDrawable face=new android.graphics.drawable.StateListDrawable();
        face.addState(new int[]{android.R.attr.state_selected},getDrawable(dev.sense.framework.R.drawable.sense_common_button_selected));
        face.addState(new int[]{android.R.attr.state_pressed},getDrawable(dev.sense.framework.R.drawable.sense_common_button_selected));
        face.addState(new int[]{},button.getBackground());button.setBackgroundTintList(null);button.setBackground(face);
        button.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_selected},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{Color.BLACK,Color.BLACK,Color.WHITE}));button.setSelected(selected);button.setContentDescription(label);
        return button;
    }
    private void render(){
        SenseInCallService service=SenseInCallService.current();SenseInCallService.Entry entry=service==null?null:service.entry(token);
        if(entry==null&&service!=null){entry=service.primary();if(entry!=null)token=entry.token;}
        if(entry==null){finish();return;}final SenseInCallService.Entry current=entry;
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setBackgroundColor(0xff202723);
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setBackgroundColor(0xff202723);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root,new ScrollView.LayoutParams(-1,-2));outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        outer.setOnApplyWindowInsetsListener((view,insets)->{var safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;});
        setContentView(outer);root.addView(text(current.status(),20));root.addView(text(current.title(),28));if(!current.number().isBlank()&&!current.title().equals(current.number()))root.addView(text(current.number(),18));
        if(service.entries().size()>1){LinearLayout calls=new LinearLayout(this);for(var other:service.entries())calls.addView(action(other.title()+"\n"+other.status(),()->{token=other.token;render();}),new LinearLayout.LayoutParams(0,dp(60),1));root.addView(calls);}
        ImageView portrait=new ImageView(this);portrait.setImageResource(R.drawable.call_phone_default_incall);portrait.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(portrait,new LinearLayout.LayoutParams(-1,0,1));
        if(current.state()==Call.STATE_ACTIVE){Chronometer duration=new Chronometer(this);duration.setTextColor(Color.WHITE);duration.setTextSize(18);duration.setGravity(Gravity.CENTER);long connected=current.call.getDetails().getConnectTimeMillis();duration.setBase(SystemClock.elapsedRealtime()-Math.max(0,System.currentTimeMillis()-connected));if(connected>0){duration.start();root.addView(duration);}}
        if(current.state()==Call.STATE_SELECT_PHONE_ACCOUNT)accounts(current);
        if(current.ringing()){
            LinearLayout incoming=new LinearLayout(this);incoming.addView(colored("Decline",R.drawable.call_phone_btn_end_rest,()->service.end(current.token)),new LinearLayout.LayoutParams(0,dp(64),1));incoming.addView(colored("Answer",R.drawable.call_phone_btn_answer_rest,()->service.answer(current.token)),new LinearLayout.LayoutParams(0,dp(64),1));outer.addView(incoming);return;
        }
        if(keypad){
            String[] keys={"1","2","3","4","5","6","7","8","9","*","0","#"};
            for(int row=0;row<4;row++){LinearLayout line=new LinearLayout(this);for(int col=0;col<3;col++){char digit=keys[row*3+col].charAt(0);Button key=action(String.valueOf(digit),()->{current.call.playDtmfTone(digit);keyToneEnd(current);});key.setContentDescription("Send tone "+digit);line.addView(key,new LinearLayout.LayoutParams(0,dp(48),1));}root.addView(line);}
        }
        LinearLayout controls=new LinearLayout(this);
        Button mute=iconAction(service.muted()?"Unmute":"Mute",R.drawable.call_mute_rest,R.drawable.call_mute_on,service.muted(),()->service.setMuted(!service.muted()));mute.setSelected(service.muted());controls.addView(mute,new LinearLayout.LayoutParams(0,dp(80),1));
        boolean speaker=service.endpoint()!=null&&service.endpoint().getEndpointType()==CallEndpoint.TYPE_SPEAKER;
        boolean bluetooth=service.endpoint()!=null&&service.endpoint().getEndpointType()==CallEndpoint.TYPE_BLUETOOTH;
        controls.addView(iconAction(service.endpoint()==null?"Audio":service.endpoint().getEndpointName().toString(),bluetooth?R.drawable.call_phone_icon_bluetooth_headset_rest:R.drawable.call_speaker_on_rest,bluetooth?R.drawable.call_phone_icon_bluetooth_headset_on:R.drawable.call_speaker_on_on,speaker||bluetooth,()->audio(service)),new LinearLayout.LayoutParams(0,dp(80),1));
        controls.addView(iconAction(keypad?"Hide keypad":"Keypad",dev.sense.framework.R.drawable.sense_common_icon_glance_show_keypad_rest,dev.sense.framework.R.drawable.sense_common_icon_glance_hide_keypad_on,keypad,()->{keypad=!keypad;render();}),new LinearLayout.LayoutParams(0,dp(80),1));root.addView(controls);
        if(current.call.getDetails().can(Call.Details.CAPABILITY_HOLD)||current.state()==Call.STATE_HOLDING)root.addView(iconAction(current.state()==Call.STATE_HOLDING?"Resume":"Hold",R.drawable.call_hold_call_rest,R.drawable.call_hold_call_on,current.state()==Call.STATE_HOLDING,()->{if(current.state()==Call.STATE_HOLDING)current.call.unhold();else current.call.hold();}),new LinearLayout.LayoutParams(-1,dp(76)));
        List<Call> conferenceable=current.call.getConferenceableCalls();if(!conferenceable.isEmpty())root.addView(iconAction("Merge calls",R.drawable.call_merge_calls_rest,R.drawable.call_merge_calls_pressed,false,()->current.call.conference(conferenceable.get(0))),new LinearLayout.LayoutParams(-1,dp(76)));
        outer.addView(colored("End call",R.drawable.call_phone_btn_end_rest,()->service.end(current.token)),new LinearLayout.LayoutParams(-1,dp(60)));
    }
    private void keyToneEnd(SenseInCallService.Entry entry){new Handler(Looper.getMainLooper()).postDelayed(()->{SenseInCallService s=SenseInCallService.current();if(s!=null&&s.entry(entry.token)==entry)entry.call.stopDtmfTone();},150);}
    private void audio(SenseInCallService service){List<CallEndpoint> available=service.endpoints();if(available.isEmpty()){Toast.makeText(this,"No audio devices available",Toast.LENGTH_SHORT).show();return;}String[] labels=available.stream().map(e->e.getEndpointName().toString()).toArray(String[]::new);new AlertDialog.Builder(this).setTitle("Call audio").setItems(labels,(dialog,index)->service.route(available.get(index))).show();}
    private void accounts(SenseInCallService.Entry entry){
        ArrayList<PhoneAccountHandle> handles=new ArrayList<>();Bundle extras=entry.call.getDetails().getExtras();
        if(extras!=null){var suggestions=extras.getParcelableArrayList(Call.EXTRA_SUGGESTED_PHONE_ACCOUNTS,PhoneAccountSuggestion.class);if(suggestions!=null)for(var suggestion:suggestions)handles.add(suggestion.getPhoneAccountHandle());if(handles.isEmpty()){var legacy=extras.getParcelableArrayList(Call.AVAILABLE_PHONE_ACCOUNTS,PhoneAccountHandle.class);if(legacy!=null)handles.addAll(legacy);}}
        TelecomManager telecom=getSystemService(TelecomManager.class);if(handles.isEmpty())try{handles.addAll(telecom.getCallCapablePhoneAccounts());}catch(SecurityException ignored){}
        for(PhoneAccountHandle handle:handles){String label="Phone account";try{PhoneAccount account=telecom.getPhoneAccount(handle);if(account!=null)label=account.getLabel().toString();}catch(SecurityException ignored){}root.addView(action(label,()->entry.call.phoneAccountSelected(handle,false)),new LinearLayout.LayoutParams(-1,dp(52)));}
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("token",token);out.putBoolean("keypad",keypad);}
}
