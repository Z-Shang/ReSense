package dev.sense.launcher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Native adaptations of the recovered compact, medium and full-page Sense music scenes. */
final class SenseMusicView extends FrameLayout {
    final int styleRows;
    private final int referenceHeight,noCoverResource;
    private final MediaRepository media;
    private MediaRepository.State state;
    private final Map<View,float[]> positions=new LinkedHashMap<>();
    private final ImageView cover,reflection;
    private final TextView title,artist,elapsed,remaining;
    private final ImageButton previous,play,next;
    private final SeekBar seek;
    private boolean seeking,pendingHold,claimed;
    private float downX,downY;
    private long downTime;
    private final Consumer<MediaRepository.State> listener=this::update;
    private final Runnable tick=new Runnable(){public void run(){updatePosition();if(isAttachedToWindow() && getWindowVisibility()==VISIBLE && state!=null && state.playing())postDelayed(this,1000);}};
    private final Runnable hold=()->{
        if(!pendingHold || !isAttachedToWindow() || !isShown())return;
        pendingHold=false;
        if(performLongClick()){
            claimed=true;
            MotionEvent cancel=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,downX,downY,0);
            super.dispatchTouchEvent(cancel);cancel.recycle();
        }
    };
    SenseMusicView(Context context){this(context,2);}
    SenseMusicView(Context context,int rows){
        super(context);
        styleRows=rows==1 || rows==4?rows:2;
        referenceHeight=styleRows==1?142:styleRows==4?748:344;
        noCoverResource=styleRows==4?R.drawable.sense_music_large_no_cover:R.drawable.sense_music_no_cover;
        media=MediaRepository.get(context);state=media.state();
        setClipChildren(false);setClipToPadding(false);
        View panel=new View(context);panel.setBackground(new Panel(context));place(panel,10,9,510,332);
        ImageView shadow=image(R.drawable.sense_music_shadow);shadow.setAlpha(.55f);place(shadow,0,180,275,31);
        reflection=image(noCoverResource);reflection.setScaleY(-1);reflection.setAlpha(.17f);place(reflection,55,195,191,60);
        ImageView glaze=image(R.drawable.sense_music_reflection);glaze.setAlpha(.5f);place(glaze,39,194,214,92);
        cover=image(noCoverResource);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);place(cover,55,5,191,191);
        ImageView frame=image(R.drawable.sense_music_album_frame);place(frame,39,0,214,202);
        // The Mode10 dynamic cover has z=1; the opaque album plate stays behind it.
        cover.bringToFront();
        title=text(28);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);place(title,274,36,231,65);
        artist=text(23);artist.setSingleLine(true);artist.setEllipsize(android.text.TextUtils.TruncateAt.END);place(artist,274,105,231,55);
        View.OnClickListener open=v->{if(!media.hasAccess())LauncherSettingsActivity.open(getContext(),"integrations");else media.openPlayer(getContext());};
        cover.setContentDescription("Open current media app");
        cover.setOnClickListener(open);frame.setOnClickListener(open);title.setOnClickListener(open);artist.setOnClickListener(open);
        seek=new SeekBar(context);seek.setMax(1000);seek.setPadding(0,0,0,0);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffefefef));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(0xffdddddd));
        place(seek,70,211,390,28);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user){}
            public void onStartTrackingTouch(SeekBar bar){seeking=true;}
            public void onStopTrackingTouch(SeekBar bar){seeking=false;if(!claimed)media.seek(state.duration()*bar.getProgress()/1000);}
        });
        elapsed=text(16);remaining=text(16);remaining.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(elapsed,70,232,120,25);place(remaining,340,232,120,25);
        ImageView controls=image(R.drawable.sense_music_controls);place(controls,106,248,318,73);
        previous=control(R.drawable.sense_music_previous,"Previous track",media::previous);place(previous,106,250,104,70);
        play=control(R.drawable.sense_music_play,"Play",media::playPause);place(play,210,250,110,70);
        next=control(R.drawable.sense_music_next,"Next track",media::next);place(next,320,250,104,70);
        if(styleRows==1){
            move(panel,10,12,510,124);move(shadow,5,120,200,12);
            move(frame,20,0,137,129);move(cover,30,3,122,122);
            move(reflection,30,130,122,12);move(glaze,20,129,137,13);
            move(title,184,12,326,44);title.setMaxLines(1);
            artist.setVisibility(GONE);seek.setVisibility(GONE);elapsed.setVisibility(GONE);remaining.setVisibility(GONE);
            move(controls,186,58,318,73);move(previous,186,60,104,70);move(play,290,60,110,70);move(next,400,60,104,70);
        }else if(styleRows==4){
            panel.setBackgroundResource(R.drawable.sense_music_large_panel);move(panel,9,70,510,678);
            frame.setImageResource(R.drawable.sense_music_large_frame);move(frame,78,0,348,331);move(cover,104,9,313,313);
            move(shadow,20,305,441,49);move(reflection,104,331,313,145);
            glaze.setImageResource(R.drawable.sense_music_large_reflection);glaze.setScaleY(-1);move(glaze,78,331,348,157);
            move(title,70,401,390,48);title.setGravity(Gravity.CENTER);title.setMaxLines(1);
            move(artist,70,490,390,42);artist.setGravity(Gravity.CENTER);
            move(seek,84,440,360,28);move(elapsed,78,460,120,25);move(remaining,330,460,120,25);
            move(controls,106,635,318,73);move(previous,106,637,104,70);move(play,210,637,110,70);move(next,320,637,104,70);
            ImageView shuffle=image(R.drawable.sense_music_shuffle);shuffle.setAlpha(.4f);place(shuffle,24,87,28,28);
            ImageView repeat=image(R.drawable.sense_music_repeat);repeat.setAlpha(.4f);place(repeat,478,87,28,28);
            place(image(R.drawable.sense_music_divider),11,591,506,2);
        }
        setContentDescription("Sense music widget, 4 by "+styleRows);update(state);
    }
    private ImageView image(int resource){ImageView image=new ImageView(getContext());image.setImageResource(resource);image.setScaleType(ImageView.ScaleType.FIT_XY);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);return image;}
    private TextView text(int size){TextView text=new TextView(getContext());text.setTextColor(Color.WHITE);text.setIncludeFontPadding(false);text.setGravity(Gravity.CENTER_VERTICAL);text.setTag(size);return text;}
    private ImageButton control(int icon,String description,Runnable action){
        ImageButton button=new ImageButton(getContext());button.setImageResource(icon);button.setScaleType(ImageView.ScaleType.FIT_CENTER);button.setContentDescription(description);
        button.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x44ffffff),null,null));button.setOnClickListener(v->action.run());return button;
    }
    private void move(View child,float x,float y,float width,float height){positions.put(child,new float[]{x,y,width,height});}
    private void place(View child,float x,float y,float width,float height){positions.put(child,new float[]{x,y,width,height});addView(child,new LayoutParams(1,1));}
    @Override protected void onMeasure(int widthSpec,int heightSpec){setMeasuredDimension(MeasureSpec.getSize(widthSpec),MeasureSpec.getSize(heightSpec));}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        float scale=Math.min(getWidth()/530f,getHeight()/(float)referenceHeight),ox=(getWidth()-530*scale)/2,oy=(getHeight()-referenceHeight*scale)/2;
        for(var entry:positions.entrySet()){
            View child=entry.getKey();float[] rect=entry.getValue();int x=Math.round(ox+rect[0]*scale),y=Math.round(oy+rect[1]*scale),w=Math.max(1,Math.round(rect[2]*scale)),h=Math.max(1,Math.round(rect[3]*scale));
            if(child instanceof TextView label && child.getTag() instanceof Integer size)label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*scale);
            if(child instanceof ImageButton)child.setPadding(Math.round((rect[2]-44)*scale/2),Math.round(13*scale),Math.round((rect[2]-44)*scale/2),Math.round(13*scale));
            child.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));child.layout(x,y,x+w,y+h);
        }
    }
    private void update(MediaRepository.State nextState){
        state=nextState;
        if(!state.access()){title.setText(styleRows==1?"Enable media access":"Music");artist.setText("Enable media access");}
        else if(state.controller()==null){title.setText("No music playing");artist.setText("Start playback in a music app");}
        else{
            MediaMetadata metadata=state.metadata();CharSequence name=metadata==null?null:metadata.getDescription().getTitle();
            CharSequence performer=metadata==null?null:metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
            if(performer==null && metadata!=null)performer=metadata.getDescription().getSubtitle();
            title.setText(name==null?"Now playing":name);artist.setText(performer==null?"":performer);
        }
        if(state.artwork()!=null){cover.setImageBitmap(state.artwork());reflection.setImageBitmap(state.artwork());}
        else{cover.setImageResource(noCoverResource);reflection.setImageResource(noCoverResource);}
        play.setImageResource(state.playing()?R.drawable.sense_music_pause:R.drawable.sense_music_play);play.setContentDescription(state.playing()?"Pause":"Play");
        enable(previous,state.supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS));enable(next,state.supports(PlaybackState.ACTION_SKIP_TO_NEXT));
        enable(play,state.supports(PlaybackState.ACTION_PLAY_PAUSE|(state.playing()?PlaybackState.ACTION_PAUSE:PlaybackState.ACTION_PLAY)));
        seek.setEnabled(state.duration()>0 && state.supports(PlaybackState.ACTION_SEEK_TO));
        updatePosition();removeCallbacks(tick);if(isAttachedToWindow() && getWindowVisibility()==VISIBLE && state.playing())postDelayed(tick,1000);
    }
    private void enable(View view,boolean enabled){view.setEnabled(enabled);view.setAlpha(enabled?1:.4f);}
    private void updatePosition(){
        if(state==null)return;long position=state.position(),duration=state.duration();
        if(!seeking)seek.setProgress(duration>0?(int)(position*1000/duration):0);
        elapsed.setText(time(position));remaining.setText("−"+time(Math.max(0,duration-position)));
    }
    private static String time(long milliseconds){long seconds=milliseconds/1000;return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();media.subscribe(listener);}
    @Override protected void onDetachedFromWindow(){clearHold();removeCallbacks(tick);media.unsubscribe(listener);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(media!=null){removeCallbacks(tick);if(visibility==VISIBLE){media.refreshAccess();if(state!=null && state.playing())postDelayed(tick,1000);}}}
    private void clearHold(){pendingHold=false;removeCallbacks(hold);}
    @Override public void cancelLongPress(){super.cancelLongPress();clearHold();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:clearHold();claimed=false;downX=event.getX();downY=event.getY();downTime=event.getDownTime();pendingHold=true;postDelayed(hold,ViewConfiguration.getLongPressTimeout());break;
            case MotionEvent.ACTION_MOVE:if(Math.abs(event.getX()-downX)>ViewConfiguration.get(getContext()).getScaledTouchSlop() || Math.abs(event.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())clearHold();break;
            case MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN:clearHold();break;
        }
        return claimed || super.dispatchTouchEvent(event);
    }
    /** Stretch only the center of the recovered common_panel.9.png. */
    private static final class Panel extends Drawable {
        final Bitmap bitmap;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Panel(Context context){bitmap=BitmapFactory.decodeResource(context.getResources(),R.drawable.sense_music_panel);}
        public void draw(Canvas canvas){Rect b=getBounds();int w=bitmap.getWidth(),h=bitmap.getHeight();int[] sx={0,w/2-1,w/2+1,w},sy={0,h/2-1,h/2+1,h};float edge=Math.min(b.width(),b.height())*.04f;float[] dx={b.left,b.left+edge,b.right-edge,b.right},dy={b.top,b.top+edge,b.bottom-edge,b.bottom};for(int y=0;y<3;y++)for(int x=0;x<3;x++)canvas.drawBitmap(bitmap,new Rect(sx[x],sy[y],sx[x+1],sy[y+1]),new RectF(dx[x],dy[y],dx[x+1],dy[y+1]),paint);}
        public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
}
