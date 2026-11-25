package dev.sense.messages;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.database.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.telephony.*;
import android.text.*;
import android.text.format.DateUtils;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import dev.sense.framework.*;
import java.util.*;

/** Native Android 15 SMS client, using original Mms.apk bubbles and shared Sense chrome. */
public final class MessagesActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private SenseScreenHost screenHost;private int navigationAnimation;
    private LinearLayout root,content;private ListView list;private TextView info,counter,titleView,subtitleView;private LinearLayout recipientBar;private EditText recipient,body,search;private ImageButton send,attachment;
    private final ArrayList<MmsFiles.Attachment> attachments=new ArrayList<>();private LinearLayout attachmentStrip;private ScrollView attachmentScroll;private String pickerAddress="";
    private long thread=-1;private String address="",draft="",filter="";private boolean composing,busy,preparing;private volatile boolean started;private volatile int generation;private int limit=200;private Rows rows;private boolean firstLoad;
    private final Runnable refresh=this::load;
    private final SharedPreferences.OnSharedPreferenceChangeListener draftChanged=(prefs,key)->{if(started&&key!=null&&key.startsWith("attachments:")&&recipient!=null&&key.equals("attachments:"+SendCoordinator.key(recipient.getText().toString())))main.post(()->{if(prefs.contains("mms-submitted:"+SendCoordinator.key(address)))return;attachments.clear();attachments.addAll(MmsFiles.draft(this,address));updateAttachments();updateCounter();});if(started&&key!=null&&(key.startsWith("sending:")||key.startsWith("submitted:")||key.startsWith("error:")))main.post(this::syncSending);};
    private void syncSending(){
        if(body==null||recipient==null)return;String key=SendCoordinator.key(recipient.getText().toString());var prefs=getSharedPreferences("drafts",MODE_PRIVATE);
        String sending=prefs.getString("sending:"+key,null),submitted=prefs.getString("submitted:"+key,null),error=prefs.getString("error:"+key,null);
        if(sending!=null&&!SendCoordinator.ACTIVE.contains(key)){prefs.edit().remove("sending:"+key).apply();sending=null;}
        busy=sending!=null;body.setEnabled(!busy);recipient.setEnabled(!busy);
        if(submitted!=null){String sentAttachments=prefs.getString("mms-submitted:"+key,null);boolean matches=submitted.equals(body.getText().toString())&&(sentAttachments==null||sentAttachments.equals(MmsFiles.encode(attachments)));if(matches){if(sentAttachments!=null){attachments.clear();updateAttachments();}draft="";body.setText("");}prefs.edit().remove("submitted:"+key).remove("mms-submitted:"+key).apply();}

        if(error!=null){toast(error);prefs.edit().remove("error:"+key).apply();}
        updateCounter();load();
    }
    private final ContentObserver observer=new ContentObserver(main){@Override public void onChange(boolean self){main.removeCallbacks(refresh);main.postDelayed(refresh,120);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setDecorFitsSystemWindows(false);
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN,dev.sense.framework.R.anim.sense_screen_open_enter,dev.sense.framework.R.anim.sense_screen_hold);
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,dev.sense.framework.R.anim.sense_screen_hold,dev.sense.framework.R.anim.sense_screen_close_exit);
        if(state!=null){thread=state.getLong("thread",-1);address=state.getString("address","");draft=state.getString("body","");composing=state.getBoolean("composing");filter=state.getString("filter","");pickerAddress=state.getString("pickerAddress",address);}else readIntent(getIntent());
        if(composing)attachments.addAll(state!=null&&state.containsKey("attachments")?MmsFiles.decode(state.getString("attachments","[]")):MmsFiles.draft(this,address));
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,()->{if(composing)closeConversation();else finish();});render();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);saveDraft();setIntent(intent);readIntent(intent);attachments.clear();attachments.addAll(MmsFiles.draft(this,address));navigationAnimation=1;render();}
    private void readIntent(Intent intent){
        Uri data=intent.getData();String action=intent.getAction();thread=intent.getLongExtra("thread",-1);address=intent.getStringExtra("address");if(address==null)address="";draft="";composing=thread>0;
        if(data!=null&&Set.of("sms","smsto","mms","mmsto").contains(data.getScheme())){String raw=data.getSchemeSpecificPart();int query=raw.indexOf('?');address=query<0?raw:raw.substring(0,query);composing=true;}
        if(Intent.ACTION_SEND.equals(action))composing=true;
        String supplied=intent.getStringExtra("sms_body");if(supplied==null){CharSequence shared=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);if(shared!=null)supplied=shared.toString();}
        if("dev.sense.messages.QUICK_DRAFT".equals(action)){var prefs=getSharedPreferences("drafts",MODE_PRIVATE);address=prefs.getString("quick-address","");supplied=prefs.getString("quick-body","");composing=true;}
        if(supplied!=null){draft=supplied;composing=true;}else if(composing)draft=getSharedPreferences("drafts",MODE_PRIVATE).getString("body:"+SendCoordinator.key(address),"");
    }
    @Override protected void onStart(){super.onStart();started=true;getSharedPreferences("drafts",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(draftChanged);syncSending();MessageStore.WORK.execute(()->{try(SendLedger ledger=new SendLedger(this);MmsTransfers mms=new MmsTransfers(this)){ledger.reconcile(this);mms.reconcile(this);}catch(RuntimeException ignored){}});observe();load();}
    private void observe(){
        MessagesWidgetProvider.observe(this);MessagesWidgetProvider.refresh(this);
        getContentResolver().unregisterContentObserver(observer);
        try{if(MessageStore.permission(this,Manifest.permission.READ_SMS)){getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI,true,observer);getContentResolver().registerContentObserver(Telephony.Mms.CONTENT_URI,true,observer);}}catch(SecurityException ignored){}
        try{if(MessageStore.permission(this,Manifest.permission.READ_CONTACTS))getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,true,observer);}catch(SecurityException ignored){}
    }
    @Override protected void onResume(){super.onResume();if(info!=null){updateInfo();load();}}
    @Override protected void onPause(){saveDraft();super.onPause();}
    @Override protected void onStop(){started=false;getSharedPreferences("drafts",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(draftChanged);generation++;main.removeCallbacks(refresh);getContentResolver().unregisterContentObserver(observer);super.onStop();}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    private Button button(String label,Runnable action){SenseButton b=new SenseButton(this);b.setText(label);b.setOnClickListener(v->action.run());return b;}
    private EditText input(String hint){EditText v=new EditText(this);v.setHint(hint);v.setTextColor(0xff222222);v.setHintTextColor(0xff777777);v.setTextSize(18);v.setBackgroundResource(dev.sense.framework.R.drawable.sense_common_inputfield_rest);v.setPadding(dp(10),dp(9),dp(10),dp(9));return v;}
    private Drawable selector(int rest,int active){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_pressed},getDrawable(active));d.addState(new int[]{},getDrawable(rest));return d;}
    private ImageButton icon(int image,String description,Runnable action){
        ImageButton b=new ImageButton(this);b.setImageResource(image);b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x44ffffff),null,new ColorDrawable(Color.WHITE)));
        b.setPadding(dp(8),dp(8),dp(8),dp(8));b.setContentDescription(description);b.setOnClickListener(v->action.run());return b;
    }
    private void render(){
        generation++;firstLoad=true;limit=200;root=vertical();root.setBackgroundColor(0xff202521);
        root.setOnApplyWindowInsetsListener((v,insets)->{var safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());var ime=insets.getInsets(WindowInsets.Type.ime());v.setPadding(safe.left,safe.top,safe.right,Math.max(safe.bottom,ime.bottom));if(body!=null){int minimum=dp(ime.bottom>0?149:165);if(body.getMinimumHeight()!=minimum){body.setMinHeight(minimum);body.setMaxLines(ime.bottom>0?3:6);}}return insets;});
        if(screenHost==null){screenHost=new SenseScreenHost(this);setContentView(screenHost);}getWindow().getInsetsController().setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff646964,0xff353b36,0xff242a25}));
        if(composing){
            ImageButton back=SenseHeaderIconButton.back(this,"Back to conversations");back.setOnClickListener(v->closeConversation());header.addView(back,new LinearLayout.LayoutParams(dp(48),-1));
            ImageView avatar=new ImageView(this);avatar.setImageResource(R.drawable.msg_message_icon_contact);avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);header.addView(avatar,new LinearLayout.LayoutParams(dp(40),dp(40)));
        }
        LinearLayout headings=vertical();headings.setGravity(Gravity.CENTER_VERTICAL);headings.setPadding(dp(12),0,dp(6),0);
        titleView=text(composing?(address.isBlank()?"New message":address):"Messages",22,Color.WHITE);titleView.setSingleLine();titleView.setEllipsize(TextUtils.TruncateAt.END);headings.addView(titleView);
        subtitleView=text("",13,0xffdddddd);subtitleView.setSingleLine();subtitleView.setVisibility(View.GONE);headings.addView(subtitleView);header.addView(headings,new LinearLayout.LayoutParams(0,-1,1));
        if(!composing)header.addView(icon(R.drawable.msg_message_icon_edit_rest,"New message",()->open(-1,"",getSharedPreferences("drafts",MODE_PRIVATE).getString("body:",""))),new LinearLayout.LayoutParams(dp(48),dp(48)));
        ImageButton overflow=SenseHeaderIconButton.overflow(this,"Message options");overflow.setOnClickListener(v->options(v));header.addView(overflow,new LinearLayout.LayoutParams(dp(48),-1));root.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        info=text("",14,Color.WHITE);info.setPadding(dp(12),dp(7),dp(12),dp(7));info.setBackgroundColor(0xff424a3c);info.setOnClickListener(v->setup());root.addView(info,new LinearLayout.LayoutParams(-1,-2));
        content=vertical();content.setBackgroundColor(0xfff2f2f2);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        recipient=null;recipientBar=null;body=null;search=null;send=null;attachment=null;counter=null;
        if(composing){
            recipientBar=new LinearLayout(this);recipientBar.setGravity(Gravity.CENTER_VERTICAL);recipientBar.setPadding(dp(9),dp(8),dp(9),dp(8));recipientBar.setBackgroundColor(Color.WHITE);
            TextView label=text("To:",17,0xff444444);label.setPadding(0,0,dp(8),0);recipientBar.addView(label);recipient=input("Phone number");recipient.setInputType(android.text.InputType.TYPE_CLASS_PHONE);recipient.setSingleLine();recipient.setContentDescription("Recipient");recipient.setText(address);recipientBar.addView(recipient,new LinearLayout.LayoutParams(0,dp(44),1));
            recipientBar.addView(icon(R.drawable.msg_icon_contact_picker_rest,"Pick contact",this::pickContact),new LinearLayout.LayoutParams(dp(48),dp(48)));recipientBar.setVisibility(thread>0?View.GONE:View.VISIBLE);content.addView(recipientBar);
            View divider=new View(this);divider.setBackgroundColor(0xff1e1e1e);content.addView(divider,new LinearLayout.LayoutParams(-1,Math.max(1,dp(1))));
            recipient.addTextChangedListener(watcher(()->{address=recipient.getText().toString();thread=-1;limit=200;main.removeCallbacks(refresh);main.postDelayed(refresh,350);}));
        }else{
            search=input("Search conversations");search.setSingleLine();search.setText(filter);search.setContentDescription("Search conversations");content.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));search.addTextChangedListener(watcher(()->{filter=search.getText().toString();main.removeCallbacks(refresh);main.postDelayed(refresh,200);}));
        }
        FrameLayout frame=new FrameLayout(this);list=new ListView(this);list.setDivider(composing?null:getDrawable(dev.sense.framework.R.drawable.sense_common_list_divider));list.setDividerHeight(composing?0:dp(1));list.setCacheColorHint(Color.TRANSPARENT);list.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);list.setStackFromBottom(composing);frame.addView(list,new FrameLayout.LayoutParams(-1,-1));
        TextView empty=text(composing?"No messages yet":"No conversations",18,0xff6a6a65);empty.setGravity(Gravity.CENTER);frame.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);content.addView(frame,new LinearLayout.LayoutParams(-1,0,1));rows=new Rows();list.setAdapter(rows);
        list.setOnItemClickListener((parent,view,index,id)->{if(composing){int messageIndex=index-(rows.earlier?1:0);if(messageIndex>=0&&messageIndex<rows.messages.size()&&rows.messages.get(messageIndex).download()){downloadMms(rows.messages.get(messageIndex));return;}if(index==0&&rows.earlier){limit+=200;load();}return;}MessageStore.ThreadRow row=rows.threads.get(index);open(row.id(),row.address(),getSharedPreferences("drafts",MODE_PRIVATE).getString("body:"+SendCoordinator.key(row.address()),""));});
        list.setOnItemLongClickListener((parent,view,index,id)->{if(composing){int pos=index-(rows.earlier?1:0);if(pos>=0&&pos<rows.messages.size()){messageMenu(rows.messages.get(pos));return true;}}return false;});
        if(composing){
            FrameLayout compose=new FrameLayout(this);compose.setAddStatesFromChildren(true);compose.setBackground(selector(R.drawable.msg_message_btn_input_rest,R.drawable.msg_message_btn_input_pressed));
            body=input("Add text");body.setBackground(null);body.setTextSize(17.3f);body.setGravity(Gravity.TOP|Gravity.START);body.setPadding(dp(8),dp(8),dp(8),dp(11));body.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);body.setMinHeight(dp(165));body.setMaxLines(6);body.setContentDescription("Message text");body.setText(draft);FrameLayout.LayoutParams editorParams=new FrameLayout.LayoutParams(-1,-2);editorParams.rightMargin=dp(80);compose.addView(body,editorParams);
            send=new ImageButton(this);send.setImageDrawable(selector(R.drawable.msg_msg_icon_send_rest,R.drawable.msg_msg_icon_send_on));send.setScaleType(ImageView.ScaleType.CENTER_INSIDE);send.setContentDescription("Send SMS");send.setOnClickListener(v->chooseSend());send.setBackground(selector(R.drawable.msg_message_btn_rest,R.drawable.msg_message_btn_pressed));send.setPadding(dp(15),dp(8),dp(15),dp(8));
            FrameLayout.LayoutParams sendParams=new FrameLayout.LayoutParams(dp(67),dp(48),Gravity.BOTTOM|Gravity.RIGHT);sendParams.setMargins(0,0,dp(9),dp(65));compose.addView(send,sendParams);
            attachment=new ImageButton(this);attachment.setImageDrawable(selector(R.drawable.msg_msg_icon_attachment_expend_rest,R.drawable.msg_msg_icon_attachment_expend_on));attachment.setScaleType(ImageView.ScaleType.CENTER_INSIDE);attachment.setContentDescription("Add attachment");attachment.setTooltipText("Add attachment");attachment.setBackground(selector(R.drawable.msg_message_btn_rest,R.drawable.msg_message_btn_pressed));attachment.setPadding(dp(15),dp(8),dp(15),dp(8));attachment.setOnClickListener(v->attachmentMenu());
            FrameLayout.LayoutParams attachmentParams=new FrameLayout.LayoutParams(dp(67),dp(48),Gravity.BOTTOM|Gravity.RIGHT);attachmentParams.setMargins(0,0,dp(9),dp(9));compose.addView(attachment,attachmentParams);
            counter=text("",13,0xff666666);counter.setGravity(Gravity.TOP|Gravity.END);counter.setTypeface(null,Typeface.BOLD);counter.setPadding(0,0,dp(3),0);
            FrameLayout.LayoutParams counterParams=new FrameLayout.LayoutParams(dp(67),-2,Gravity.TOP|Gravity.RIGHT);counterParams.setMargins(0,dp(8),dp(9),0);compose.addView(counter,counterParams);
            attachmentStrip=vertical();attachmentStrip.setBackgroundColor(0xfff2f2f2);attachmentScroll=new ScrollView(this);attachmentScroll.addView(attachmentStrip);root.addView(attachmentScroll,new LinearLayout.LayoutParams(-1,-2));updateAttachments();root.addView(compose,new LinearLayout.LayoutParams(-1,-2));body.addTextChangedListener(watcher(this::updateCounter));syncSending();updateCounter();
        }
        screenHost.show(root,navigationAnimation!=0,navigationAnimation>0);navigationAnimation=0;root.requestApplyInsets();
        updateInfo();if(started)load();
    }
    private TextWatcher watcher(Runnable action){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){action.run();}public void afterTextChanged(Editable e){}};}
    private void updateCounter(){if(body==null)return;int[] length=SmsMessage.calculateLength(body.getText(),false);counter.setText(attachments.isEmpty()?length[2]+"\n"+length[0]:"MMS");counter.setContentDescription(attachments.isEmpty()?length[2]+" characters remaining, "+length[0]+" SMS parts":"MMS with "+attachments.size()+" attachments");if(attachment!=null)attachment.setEnabled(!busy&&!preparing);if(send!=null)send.setEnabled(!busy&&!preparing&&(!body.getText().toString().isBlank()||!attachments.isEmpty()));if(send!=null)send.setContentDescription(attachments.isEmpty()?"Send SMS":"Send MMS");}
    private void updateInfo(){
        String message="";if(!MessageStore.isDefault(this))message="Tap to make Sense Messages your default SMS app";
        else if(!MessageStore.permission(this,Manifest.permission.READ_SMS)||!MessageStore.permission(this,Manifest.permission.SEND_SMS)||!MessageStore.permission(this,Manifest.permission.RECEIVE_SMS))message="Tap to allow SMS access";
        else if(!getSystemService(NotificationManager.class).areNotificationsEnabled())message="Message notifications are off · tap to enable";
        info.setText(message);info.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);
    }
    private void setup(){
        RoleManager roles=getSystemService(RoleManager.class);
        if(!MessageStore.isDefault(this)){if(roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_SMS))startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_SMS),1);else toast("SMS is not available on this device.");return;}
        ArrayList<String> needed=new ArrayList<>();for(String p:new String[]{Manifest.permission.READ_SMS,Manifest.permission.SEND_SMS,Manifest.permission.RECEIVE_SMS,Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.READ_PHONE_STATE})if(!MessageStore.permission(this,p))needed.add(p);
        if(!needed.isEmpty()){requestPermissions(needed.toArray(String[]::new),2);return;}
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(started)observe();updateInfo();load();if(request==2&&!MessageStore.permission(this,Manifest.permission.READ_SMS))new AlertDialog.Builder(this).setMessage("SMS access is needed to display your messages. You can allow it in Android app settings.").setPositiveButton("App settings",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("Cancel",null).show();}
    private void options(View anchor){PopupMenu menu=new PopupMenu(this,anchor,Gravity.END);menu.getMenu().add("Default SMS and permissions").setOnMenuItemClickListener(i->{setup();return true;});menu.getMenu().add("Show contact names").setOnMenuItemClickListener(i->{requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},3);return true;});menu.getMenu().add("About SMS and MMS").setOnMenuItemClickListener(i->{about();return true;});menu.getMenu().add("Android default apps").setOnMenuItemClickListener(i->{startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));return true;});menu.show();}
    private void about(){new AlertDialog.Builder(this).setTitle("Sense Messages").setMessage("SMS and carrier MMS are supported. Add a photo, video, audio file or contact card using the attachment button. Photos are resized to your carrier's message limit; other files must already fit. Sending uses the selected SIM and carrier charges may apply.\n\nTap an incoming MMS notification in the conversation to download it. RCS and group MMS composition are not supported.").setPositiveButton("OK",null).show();}
    private void attachmentMenu(){if(busy||preparing)return;new AlertDialog.Builder(this).setTitle("Attach").setItems(new String[]{"Picture","Video","Audio","Contact card"},(dialog,which)->{
        pickerAddress=recipient.getText().toString();Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(new String[]{"image/*","video/*","audio/*","text/*"}[which]).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);if(which==3)pick.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/vcard","text/x-vcard"});
        try{startActivityForResult(pick,5);}catch(ActivityNotFoundException e){toast("No document picker is installed.");}
    }).setNegativeButton("Cancel",null).show();}
    private void updateAttachments(){if(attachmentStrip==null)return;attachmentStrip.removeAllViews();attachmentStrip.setVisibility(attachments.isEmpty()?View.GONE:View.VISIBLE);if(attachmentScroll!=null){attachmentScroll.setVisibility(attachments.isEmpty()?View.GONE:View.VISIBLE);attachmentScroll.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(48*Math.min(3,attachments.size()))));}
        for(var file:attachments){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),0,dp(8),0);if(file.mime().startsWith("image/")||file.mime().startsWith("video/")){MmsPreview preview=new MmsPreview(this,Uri.fromFile(MmsFiles.file(this,file)),file.mime(),null);LinearLayout.LayoutParams previewSize=new LinearLayout.LayoutParams(dp(48),dp(40));previewSize.setMarginEnd(dp(8));row.addView(preview,previewSize);}TextView label=text(file.name()+" · "+android.text.format.Formatter.formatShortFileSize(this,file.bytes()),14,0xff333333);label.setSingleLine();label.setEllipsize(TextUtils.TruncateAt.END);row.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));Button remove=button("Remove",()->{if(busy||preparing)return;attachments.remove(file);MmsFiles.save(this,address,attachments);updateAttachments();updateCounter();});remove.setContentDescription("Remove attachment "+file.name());row.addView(remove,new LinearLayout.LayoutParams(dp(88),dp(48)));attachmentStrip.addView(row);}
    }
    private void importAttachments(Intent data){String number=pickerAddress;ArrayList<Uri> selected=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<Math.min(11,data.getClipData().getItemCount());i++)selected.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)selected.add(data.getData());if(selected.isEmpty())return;
        toast("Adding attachment…");Context app=getApplicationContext();MessageStore.EVENTS.execute(()->{try{List<MmsFiles.Attachment> saved=new ArrayList<>(MmsFiles.draft(app,number));if(saved.size()+selected.size()>10)throw new java.io.IOException("Maximum ten attachments per MMS.");for(Uri uri:selected){var file=MmsFiles.copy(app,uri);saved.add(file);MmsFiles.save(app,number,saved);}main.post(()->{if(isDestroyed()||!composing||recipient==null||!SendCoordinator.key(recipient.getText().toString()).equals(SendCoordinator.key(number)))return;attachments.clear();attachments.addAll(saved);updateAttachments();updateCounter();});}catch(Exception failure){main.post(()->{if(!isDestroyed())toast(failure.getMessage()==null?"Cannot add attachment":failure.getMessage());});}});
    }
    private void downloadMms(MessageStore.Message message){MessageStore.EVENTS.execute(()->{try{MmsTransport.download(getApplicationContext(),message.id(),MmsTransport.CARRIER);main.post(()->toast("Downloading MMS…"));}catch(Exception failure){main.post(()->toast(failure.getMessage()==null?"Cannot download MMS":failure.getMessage()));}});}
    private void openMmsPart(MmsStore.Part part){
        try{
            Uri uri=MmsAttachmentProvider.uri(this,Long.parseLong(part.uri().getLastPathSegment()));
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,part.mime()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        }catch(ActivityNotFoundException|SecurityException error){toast("No app can open this attachment.");}
    }
    private void mmsParts(LinearLayout bubble,MessageStore.Message message,int maxWidth){
        Context app=getApplicationContext();int request=generation;
        MessageStore.WORK.execute(()->{
            try{
                var parts=MmsStore.parts(app,message.id());
                main.post(()->{
                    if(isDestroyed()||!composing||request!=generation)return;
                    int width=Math.min(dp(240),Math.max(1,maxWidth));
                    for(var part:parts){
                        if(part.mime().startsWith("image/")||part.mime().startsWith("video/")){
                            MmsPreview preview=new MmsPreview(this,part.uri(),part.mime(),()->openMmsPart(part));
                            preview.setOnLongClickListener(v->{messageMenu(message);return true;});
                            bubble.addView(preview,new LinearLayout.LayoutParams(width,dp(160)));
                        }
                        Button view=button(part.name().isBlank()?"Open "+part.mime():part.name(),()->openMmsPart(part));
                        view.setSingleLine();view.setEllipsize(TextUtils.TruncateAt.END);
                        bubble.addView(view,new LinearLayout.LayoutParams(width,dp(48)));
                    }
                });
            }catch(RuntimeException unavailable){main.post(()->{
                if(!isDestroyed()&&request==generation){TextView error=text("Attachments unavailable",14,0xff666666);bubble.addView(error);}
            });}
        });
    }


    private void closeConversation(){saveDraft();getSystemService(InputMethodManager.class).hideSoftInputFromWindow(root.getWindowToken(),0);composing=false;thread=-1;filter="";navigationAnimation=-1;render();}
    private void open(long id,String number,String text){saveDraft();thread=id;address=number;draft=text;attachments.clear();attachments.addAll(MmsFiles.draft(this,number));composing=true;navigationAnimation=1;render();}
    private void saveDraft(){if(body!=null&&recipient!=null){address=recipient.getText().toString();draft=body.getText().toString();MmsFiles.save(this,address,attachments);getSharedPreferences("drafts",MODE_PRIVATE).edit().putString("body:"+SendCoordinator.key(address),draft).apply();}}
    private void pickContact(){try{startActivityForResult(new Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI),4);}catch(ActivityNotFoundException e){toast("No contact picker is installed. Enter a phone number.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==5&&result==RESULT_OK&&data!=null)importAttachments(data);if(request==1&&result==RESULT_OK)setup();if(request==4&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();MessageStore.WORK.execute(()->{try(Cursor c=getContentResolver().query(uri,new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null)){if(c!=null&&c.moveToFirst()){String number=c.getString(0);main.post(()->{if(recipient!=null)recipient.setText(number);});}}catch(RuntimeException e){main.post(()->toast("Could not read the selected contact."));}});}}
    private void chooseSend(){
        if(busy||preparing)return;if(!MessageStore.isDefault(this)||!MessageStore.permission(this,Manifest.permission.SEND_SMS)||!MessageStore.permission(this,Manifest.permission.READ_SMS)){setup();return;}
        String number;try{number=SmsSender.address(recipient.getText().toString());}catch(IllegalArgumentException e){recipient.setError(e.getMessage());return;}
        String text=body.getText().toString();if(text.isBlank()&&attachments.isEmpty())return;preparing=true;updateCounter();
        MessageStore.WORK.execute(()->{try{List<SubscriptionInfo> sims=SmsSender.subscriptions(this);int preferred=SubscriptionManager.getDefaultSmsSubscriptionId();main.post(()->{
            if(isFinishing()||isDestroyed())return;
            if(!composing||recipient==null||!number.equals(android.telephony.PhoneNumberUtils.stripSeparators(recipient.getText().toString()))||!text.equals(body.getText().toString())){preparing=false;updateCounter();return;}
            if(sims.size()>1&&!SubscriptionManager.isValidSubscriptionId(preferred)){String[] labels=sims.stream().map(s->s.getDisplayName()+" · SIM "+(s.getSimSlotIndex()+1)).toArray(String[]::new);new AlertDialog.Builder(this).setTitle("Send SMS using").setItems(labels,(d,index)->dispatch(number,text,sims.get(index).getSubscriptionId())).setNegativeButton("Cancel",(d,w)->{preparing=false;updateCounter();}).setOnCancelListener(d->{preparing=false;updateCounter();}).show();}
            else{int sub=SubscriptionManager.isValidSubscriptionId(preferred)?preferred:sims.size()==1?sims.get(0).getSubscriptionId():-1;if(sub<0){preparing=false;updateCounter();toast("No active SMS SIM. Insert or enable a SIM in Android settings.");}else dispatch(number,text,sub);}
        });}catch(RuntimeException e){main.post(()->{preparing=false;updateCounter();toast("SIM information unavailable. Allow phone access in Message options.");});}});
    }
    private void dispatch(String number,String text,int subscription){
        preparing=false;saveDraft();if(attachments.isEmpty())SendCoordinator.send(this,number,text,subscription);else SendCoordinator.sendMms(this,number,text,List.copyOf(attachments),subscription);syncSending();
    }
    private void load(){
        if(!started||list==null)return;int serial=++generation;boolean detail=composing;long id=thread;String number=address,query=filter;int count=limit;
        if(!MessageStore.permission(this,Manifest.permission.READ_SMS)){rows.threads=List.of();rows.messages=List.of();rows.notifyDataSetChanged();updateInfo();return;}
        MessageStore.WORK.execute(()->{try{
            if(!started||serial!=generation)return;List<MessageStore.ThreadRow> threads=detail?List.of():MessageStore.threads(this,query,()->!started||serial!=generation);List<MessageStore.Message> messages=detail?MessageStore.conversation(this,id,number,count+1):List.of();
            boolean earlier=messages.size()>count;if(earlier)messages=new ArrayList<>(messages.subList(1,messages.size()));
            long resolved=id;if(detail&&resolved<0&&!messages.isEmpty())resolved=messages.get(0).thread();String name=detail?MessageStore.contactName(this,number):"";
            final long loadedThread=resolved;final List<MessageStore.Message> loaded=messages;
            main.post(()->{if(!started||serial!=generation)return;int first=list.getFirstVisiblePosition();View firstView=list.getChildAt(0);int top=firstView==null?0:firstView.getTop();boolean bottom=firstLoad||list.getLastVisiblePosition()>=rows.getCount()-2;int previous=rows.getCount();rows.threads=threads;rows.messages=loaded;rows.earlier=earlier;rows.notifyDataSetChanged();
                if(detail){thread=loadedThread;titleView.setText(number.isBlank()?"New message":name);subtitleView.setText(number);subtitleView.setVisibility(!number.isBlank()&&!name.equals(number)?View.VISIBLE:View.GONE);if(loadedThread>0&&!recipient.hasFocus())recipientBar.setVisibility(View.GONE);if(bottom)list.setSelection(rows.getCount()-1);else list.setSelectionFromTop(first+Math.max(0,rows.getCount()-previous),top);}
                firstLoad=false;
                if(detail&&loadedThread>0&&hasWindowFocus()&&loaded.stream().anyMatch(m->!m.read()&&m.type()==1))MessageStore.WORK.execute(()->{try{MessageStore.markRead(this,loadedThread);}catch(RuntimeException ignored){}});
            });
        }catch(RuntimeException e){main.post(()->{if(serial==generation){toast("Messages unavailable. Check SMS access in Message options.");updateInfo();}});}});
    }
    private void messageMenu(MessageStore.Message message){ArrayList<String> actions=new ArrayList<>(List.of("Copy text"));if(message.type()==5)actions.add(message.error()==10001?"Review uncertain send":"Edit and resend");if(MessageStore.isDefault(this))actions.add("Delete message");new AlertDialog.Builder(this).setItems(actions.toArray(String[]::new),(dialog,index)->{String action=actions.get(index);if(action.equals("Copy text")){getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Message",message.body()));}else if((action.equals("Edit and resend")||action.equals("Review uncertain send"))){if(!message.mms())body.setText(message.body());else restoreMmsDraft(message);}else new AlertDialog.Builder(this).setMessage("Delete this message?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->MessageStore.WORK.execute(()->{try{if(MessageStore.isDefault(this))getContentResolver().delete(ContentUris.withAppendedId(message.mms()?Telephony.Mms.CONTENT_URI:Telephony.Sms.CONTENT_URI,message.id()),null,null);}catch(RuntimeException e){main.post(()->toast("Could not delete this message."));}})).show();}).show();}
    private void restoreMmsDraft(MessageStore.Message message){
        String number=address;MessageStore.EVENTS.execute(()->{try{List<MmsFiles.Attachment> recovered=new ArrayList<>();for(var part:MmsStore.parts(this,message.id()))recovered.add(MmsFiles.copy(this,part.uri()));MmsFiles.save(this,number,recovered);main.post(()->{if(isDestroyed()||body==null||!composing||!number.equals(address))return;attachments.clear();attachments.addAll(recovered);body.setText(message.body().equals("MMS attachment")?"":message.body());updateAttachments();updateCounter();});}catch(Exception failure){main.post(()->toast("Could not restore MMS attachments. The original message is still saved."));}});
    }
    private void toast(String text){Toast.makeText(this,text==null?"SMS unavailable":text,Toast.LENGTH_LONG).show();}
    @Override protected void onSaveInstanceState(Bundle state){saveDraft();state.putLong("thread",thread);state.putString("address",address);state.putString("body",draft);state.putBoolean("composing",composing);state.putString("filter",filter);state.putString("pickerAddress",pickerAddress);state.putString("attachments",MmsFiles.encode(attachments));super.onSaveInstanceState(state);}
    private final class Rows extends BaseAdapter {
        List<MessageStore.ThreadRow> threads=List.of();List<MessageStore.Message> messages=List.of();boolean earlier;
        public int getCount(){return composing?messages.size()+(earlier?1:0):threads.size();}public Object getItem(int p){return composing?messages.get(Math.max(0,p-(earlier?1:0))):threads.get(p);}public long getItemId(int p){return composing?(earlier&&p==0?-1:(messages.get(p-(earlier?1:0)).mms()?-messages.get(p-(earlier?1:0)).id()-1:messages.get(p-(earlier?1:0)).id())):threads.get(p).id();}public boolean hasStableIds(){return true;}
        @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
            if(composing){if(earlier&&position==0){TextView more=text("Load earlier messages",16,0xff38662a);more.setGravity(Gravity.CENTER);more.setPadding(0,dp(15),0,dp(15));return more;}
                MessageStore.Message message=messages.get(position-(earlier?1:0));boolean outgoing=message.type()!=1;LinearLayout line=new LinearLayout(MessagesActivity.this);line.setGravity(outgoing?Gravity.END:Gravity.START);
                int leading=outgoing?73:4,trailing=outgoing?4:65;line.setPadding(dp(leading),dp(8),dp(trailing),0);
                LinearLayout bubble=vertical();bubble.setBackgroundResource(outgoing?R.drawable.msg_message_conversation_me:R.drawable.msg_message_conversation_others);
                int left=outgoing?8:23,right=outgoing?23:8;int available=parent.getWidth()>0?parent.getWidth():getResources().getDisplayMetrics().widthPixels;
                int width=Math.max(dp(60),available-dp(leading+trailing));bubble.setPadding(dp(left),dp(8),dp(right),dp(11));
                TextView text=text(message.body(),17,0xff202020);text.setOnLongClickListener(v->{messageMenu(message);return true;});bubble.setOnLongClickListener(v->{messageMenu(message);return true;});text.setMaxWidth(Math.max(1,width-dp(left+right)));bubble.addView(text);if(message.mms()){if(message.download()){Button download=button("Download MMS",()->downloadMms(message));bubble.addView(download,new LinearLayout.LayoutParams(-1,dp(48)));}else{LinearLayout media=vertical();bubble.addView(media);mmsParts(media,message,width-dp(left+right));}}
                String status=message.type()==5?(message.error()==10001?"Send status unknown":"Not sent"):message.type()==4||message.type()==6?"Sending…":outgoing?(message.status()==0?"Delivered":message.status()>=64?"Delivery failed":"Sent"):"";
                TextView stamp=text(DateUtils.formatDateTime(MessagesActivity.this,message.date(),DateUtils.FORMAT_SHOW_TIME|DateUtils.FORMAT_SHOW_DATE)+(status.isEmpty()?"":" · "+status),13,message.type()==5?0xffa52c22:0xff66665f);stamp.setPadding(0,dp(4),0,0);stamp.setMaxWidth(Math.max(1,width-dp(left+right)));bubble.addView(stamp);line.addView(bubble,new LinearLayout.LayoutParams(-2,-2));return line;
            }
            MessageStore.ThreadRow row=threads.get(position);LinearLayout line=new LinearLayout(MessagesActivity.this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(8),dp(8),dp(8),dp(8));line.setMinimumHeight(dp(73));android.graphics.drawable.StateListDrawable face=new android.graphics.drawable.StateListDrawable();face.addState(new int[]{android.R.attr.state_pressed},getDrawable(dev.sense.framework.R.drawable.sense_list_selector_background_pressed));face.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));line.setBackground(face);
            ImageView photo=new ImageView(MessagesActivity.this);photo.setImageResource(R.drawable.msg_message_icon_contact);photo.setScaleType(ImageView.ScaleType.FIT_CENTER);line.addView(photo,new LinearLayout.LayoutParams(dp(48),dp(48)));
            LinearLayout labels=vertical();labels.setPadding(dp(10),0,dp(8),0);TextView name=text(row.name()+" ("+row.count()+")",18,0xff222222);name.setSingleLine();name.setEllipsize(TextUtils.TruncateAt.END);name.setTypeface(null,row.unread()>0?Typeface.BOLD:Typeface.NORMAL);labels.addView(name);TextView preview=text((row.failed()?"Not sent · ":"")+row.body(),15,0xff666666);preview.setSingleLine();preview.setEllipsize(TextUtils.TruncateAt.END);labels.addView(preview);line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView date=text(DateUtils.getRelativeTimeSpanString(row.date(),System.currentTimeMillis(),DateUtils.MINUTE_IN_MILLIS).toString()+(row.unread()>0?"\n"+row.unread()+" unread":""),11,0xff666666);date.setGravity(Gravity.END);date.setMaxWidth(dp(92));line.addView(date);return line;
        }
    }
}
