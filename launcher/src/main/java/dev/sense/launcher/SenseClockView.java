package dev.sense.launcher;

import android.content.Context;
import android.graphics.*;
import android.text.format.DateFormat;
import android.view.View;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Native clock with flap rotations sampled from the local ROM Mode10 scene. */
public final class SenseClockView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Bitmap[][] clockDigits = new Bitmap[10][2];
    private final Paint digitPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private HtcWeatherScene weatherScene;
    private int weatherSceneCode;
    private final RectF weatherArtBounds=new RectF();
    private final WeatherWidgetGeometry weatherGeometry;
    private final WeatherRepository weather;
    private WeatherRepository.Snapshot weatherSnapshot;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener weatherListener=(preferences,key)->updateWeather();
    private float weatherFrame;
    private final RectF weatherClipBounds=new RectF();
    private android.animation.ValueAnimator weatherAnimator;
    private final Camera flapCamera = new Camera();
    private final Matrix flapMatrix = new Matrix();
    private final float[] topAngles = new float[11], bottomAngles = new float[11];
    private String hour, minute, oldHour, oldMinute;
    private boolean clockTap;
    private float flipProgress = 1f;
    private android.animation.ValueAnimator flipAnimator;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            weather.refresh(false);
            updateTime(true);
            updateDescription();
            invalidate();
            postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000);
        }
    };

    public SenseClockView(Context context) {
        super(context);
        for (int digit=0; digit<10; digit++) {
            for (int half=0; half<2; half++) {
                String name="sense_clock_digit_"+digit+(half==0 ? "_top" : "_bottom");
                int id=getResources().getIdentifier(name,"drawable",context.getPackageName());
                clockDigits[digit][half]=BitmapFactory.decodeResource(getResources(),id);
            }
        }
        weatherGeometry=new WeatherWidgetGeometry(context);
        weatherGeometry.fit(374,272,weatherArtBounds);
        weather=WeatherRepository.get(context);
        weatherSnapshot=weather.snapshot();
        setOnClickListener(v->{boolean clock=clockTap;clockTap=false;if(clock)ClockActions.open(getContext());else if(getContext() instanceof android.app.Activity activity)WeatherSettings.openWeather(activity);});
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        loadFlapSamples();
        updateTime(false);
        updateDescription();
    }

    private boolean clockPoint(float x,float y){
        float fit=Math.min(getWidth()/374f,getHeight()/272f);if(fit<=0)return false;
        x=(x-(getWidth()-374*fit)/2)/fit;y=(y-(getHeight()-272*fit)/2)/fit;
        return x>=23&&x<=350&&y>=10&&y<=169;
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)clockTap=clockPoint(event.getX(),event.getY());
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)clockTap=false;
        return super.onTouchEvent(event);
    }
    @Override public boolean performLongClick(){clockTap=false;return super.performLongClick();}
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(R.id.open_clock,"Open clock"));
        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(R.id.open_weather,"Open weather"));
    }
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle arguments){
        if(action==R.id.open_clock){ClockActions.open(getContext());return true;}
        if(action==R.id.open_weather){if(getContext() instanceof android.app.Activity activity)WeatherSettings.openWeather(activity);return true;}
        return super.performAccessibilityAction(action,arguments);
    }

    private void loadFlapSamples() {
        try (java.io.InputStream input=getResources().openRawResource(R.raw.sense_clock_flip)) {
            org.json.JSONObject data=new org.json.JSONObject(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            for(int i=0;i<11;i++) {
                topAngles[i]=(float)data.getJSONArray("top").getJSONObject(i).getDouble("degrees");
                bottomAngles[i]=(float)data.getJSONArray("bottom").getJSONObject(i).getDouble("degrees");
            }
        } catch(java.io.IOException | org.json.JSONException exception) {
            throw new IllegalStateException("Invalid generated clock animation", exception);
        }
    }

    private void updateTime(boolean animate) {
        ZonedDateTime now=ZonedDateTime.now();
        String nextHour=now.format(DateTimeFormatter.ofPattern(DateFormat.is24HourFormat(getContext()) ? "H" : "h",Locale.ROOT));
        String nextMinute=now.format(DateTimeFormatter.ofPattern("mm",Locale.ROOT));
        if(nextHour.equals(hour) && nextMinute.equals(minute))return;
        if(flipAnimator!=null)flipAnimator.cancel();
        oldHour=hour;oldMinute=minute;hour=nextHour;minute=nextMinute;
        if(!animate || oldHour==null || !android.animation.ValueAnimator.areAnimatorsEnabled()) {flipProgress=1f;return;}
        flipProgress=0f;
        flipAnimator=android.animation.ValueAnimator.ofFloat(0f,1f);
        // Mode10 supplies the angular samples; its runtime FPS has not been established.
        flipAnimator.setDuration(1000);
        flipAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        flipAnimator.addUpdateListener(a->{flipProgress=(float)a.getAnimatedValue();invalidate();});
        flipAnimator.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator a){flipProgress=1f;flipAnimator=null;invalidate();}
        });
        flipAnimator.start();
    }

    private void updateDescription() {
        ZonedDateTime now = ZonedDateTime.now();
        String pattern = DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm a";
        setContentDescription(now.format(DateTimeFormatter.ofPattern(pattern + ", EEEE, d MMMM", Locale.getDefault()))
                + ". " + weather.city() + ": " + (weatherSnapshot==null ? weather.status()
                : WeatherCodes.description(weatherSnapshot.code)+", "+Math.round(weatherSnapshot.temperature)+" degrees Celsius, high "
                +Math.round(weatherSnapshot.high)+", low "+Math.round(weatherSnapshot.low)+". "+weather.status()) + ". Weather by Open-Meteo.");
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        weather.prefs.registerOnSharedPreferenceChangeListener(weatherListener);
        updateWeather();
        removeCallbacks(tick);
        tick.run();

    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        weather.prefs.unregisterOnSharedPreferenceChangeListener(weatherListener);
        if(flipAnimator!=null)flipAnimator.cancel();
        if(weatherAnimator!=null)weatherAnimator.cancel();
        if(weatherScene!=null)weatherFrame=weatherScene.end;
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize((int) (374 * getResources().getDisplayMetrics().density), widthSpec);
        int height = resolveSize(Math.round(width * 272f / 374f), heightSpec);
        setMeasuredDimension(width, height);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 374f, getHeight() / 272f);
        canvas.save();
        canvas.translate((getWidth() - 374 * scale) / 2, (getHeight() - 272 * scale) / 2);
        canvas.scale(scale, scale);
        paint.setShader(new LinearGradient(0, 22, 0, 262,
                new int[]{0xa6616363, 0xa6242626, 0xa60b0d0d}, null, Shader.TileMode.CLAMP));
        bounds.set(2, 26, 372, 270);
        canvas.drawRoundRect(bounds, 12, 12, paint);
        paint.setShader(null);
        ZonedDateTime now = ZonedDateTime.now();
        boolean military = DateFormat.is24HourFormat(getContext());
        animatedPanel(canvas, 25, oldHour, hour);
        animatedPanel(canvas, 203, oldMinute, minute);
        if (!military) text(canvas, now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault())), 34, 143, 14, Color.BLACK, Paint.Align.LEFT);
        weatherLayout(canvas, now);
        canvas.restore();
    }

    private void updateWeather() {
        weatherSnapshot=weather.snapshot();
        int code=weatherSnapshot==null ? 0 : WeatherCodes.htcScene(weatherSnapshot.code,weatherSnapshot.day);
        if(code!=weatherSceneCode) {
            if(weatherAnimator!=null){weatherAnimator.cancel();weatherAnimator=null;}
            weatherSceneCode=code;
            weatherScene=code==0 ? null : new HtcWeatherScene(getContext(),code);
            if(weatherScene!=null) {
                weatherFrame=weatherScene.end;
                if(isAttachedToWindow() && android.animation.ValueAnimator.areAnimatorsEnabled()) {
                    weatherAnimator=android.animation.ValueAnimator.ofFloat(weatherScene.start,weatherScene.end);
                    weatherAnimator.setDuration(weatherScene.duration);
                    weatherAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
                    weatherAnimator.addUpdateListener(a->{weatherFrame=(float)a.getAnimatedValue();invalidate();});
                    weatherAnimator.start();
                }
            }
        }
        updateDescription();
        invalidate();
    }

    /** Lower section follows the stock 374 × 272 preview: city left, art center, date/temperature right. */
    private void weatherLayout(Canvas canvas, ZonedDateTime now) {
        if(weatherSnapshot!=null && weatherScene!=null){
            // The recovered square determines projection, not the width of visible cloud/particle effects.
            weatherClipBounds.set(2,Math.max(0,weatherArtBounds.top),372,Math.min(272,weatherArtBounds.bottom));
            weatherScene.draw(canvas,weatherArtBounds,weatherClipBounds,weatherFrame);
        }
        paint.setShadowLayer(1.5f,0,1,0xaa000000);
        fittedText(canvas, weather.city(), 14, 218, 20, 136, Color.WHITE, Paint.Align.LEFT);
        fittedText(canvas, weatherSnapshot==null ? weather.status() : WeatherCodes.description(weatherSnapshot.code), 14, 241, 15, 132, 0xffc5caca, Paint.Align.LEFT);
        String date=now.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()));
        fittedText(canvas, date, 360, 211, 18, 153, Color.WHITE, Paint.Align.RIGHT);
        text(canvas, weatherSnapshot==null ? "—°" : Math.round(weatherSnapshot.temperature)+"°", 312, 250, 36, Color.WHITE, Paint.Align.RIGHT);
        text(canvas, "H: "+(weatherSnapshot==null ? "—°" : Math.round(weatherSnapshot.high)+"°"), 362, 231, 14, 0xffd9dfda, Paint.Align.RIGHT);
        text(canvas, "L: "+(weatherSnapshot==null ? "—°" : Math.round(weatherSnapshot.low)+"°"), 362, 249, 14, 0xffd9dfda, Paint.Align.RIGHT);
        boolean cached=weatherSnapshot!=null && (!"Updated".equals(weather.status()) || System.currentTimeMillis()-weatherSnapshot.fetched>60*60_000);
        String source="Open-Meteo"+(weatherSnapshot==null ? "" : cached ? " · cached" : " · "+android.text.format.DateFormat.format("HH:mm",weatherSnapshot.fetched));
        fittedText(canvas,source,14,256,9,132,0xffb8c3bd,Paint.Align.LEFT);
        paint.clearShadowLayer();
    }

    private void fittedText(Canvas canvas, String value, float x, float baseline, float size,
                            float width, int color, Paint.Align align) {
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        paint.setTextSize(size);
        CharSequence fitted=android.text.TextUtils.ellipsize(value, new android.text.TextPaint(paint),
                width, android.text.TextUtils.TruncateAt.END);
        text(canvas, fitted.toString(), x, baseline, size, color, align);
    }

    private float sampledAngle(float[] samples, float progress) {
        float frame=Math.max(0,Math.min(10,progress*10));
        int index=Math.min(9,(int)frame);
        return samples[index]+(samples[index+1]-samples[index])*(frame-index);
    }

    private void animatedPanel(Canvas canvas,float left,String previous,String current) {
        if(flipProgress>=1f || previous==null || previous.equals(current)){panel(canvas,left,current);return;}
        // New upper and old lower halves form the stationary backing cards.
        halfPanel(canvas,left,current,true,0);
        halfPanel(canvas,left,previous,false,0);
        if(flipProgress<.5f) {
            float angle=sampledAngle(topAngles,flipProgress);
            if(angle>-90)halfPanel(canvas,left,previous,true,angle);
        } else {
            float angle=sampledAngle(bottomAngles,flipProgress);
            if(angle<90)halfPanel(canvas,left,current,false,angle);
        }
    }

    private void halfPanel(Canvas canvas,float left,String digits,boolean upper,float angle) {
        int saved=canvas.save();
        float hingeX=left+71.5f,hingeY=88f;
        flapCamera.save();
        flapCamera.rotateX(angle);
        flapCamera.getMatrix(flapMatrix);
        flapCamera.restore();
        flapMatrix.preTranslate(-hingeX,-hingeY);
        flapMatrix.postTranslate(hingeX,hingeY);
        canvas.concat(flapMatrix);
        canvas.clipRect(left-2,upper ? 10 : hingeY,left+146,upper ? hingeY : 169);
        panel(canvas,left,digits);
        if(angle!=0){
            paint.setColor(Color.argb(Math.round(70*Math.abs(angle)/90),0,0,0));
            canvas.drawRect(left,upper ? 13 : hingeY,left+143,upper ? hingeY : 164,paint);
        }
        canvas.restoreToCount(saved);
    }

    private void panel(Canvas canvas, float left, String digits) {
        paint.setColor(0xff080909);
        bounds.set(left - 2, 10, left + 146, 169);
        canvas.drawRoundRect(bounds, 11, 11, paint);
        paint.setShader(new LinearGradient(0, 13, 0, 164,
                new int[]{0xffeeeeec, 0xffffffff, 0xffd1d2cf, 0xfffcfcfa, 0xffb7b9b5},
                new float[]{0, .12f, .48f, .52f, 1}, Shader.TileMode.CLAMP));
        bounds.set(left, 13, left + 143, 164);
        canvas.drawRoundRect(bounds, 9, 9, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(0xffa7aaa5);
        bounds.inset(3, 3);
        canvas.drawRoundRect(bounds, 7, 7, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 17, 0, 87, 0x55ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
        bounds.set(left + 4, 17, left + 139, 87);
        canvas.drawRect(bounds, paint);
        paint.setShader(null);
        drawDigits(canvas, left + 71.5f, digits);
        paint.setColor(0xff777b77);
        canvas.drawRect(left, 87, left + 143, 89, paint);
        paint.setColor(0xfff7f8f5);
        canvas.drawRect(left, 89, left + 143, 90, paint);
    }

    private void drawDigits(Canvas canvas, float center, String digits) {
        // Original artwork: 99px wide, 79/83px halves, 84px digit advance
        // in the 530px Mode10 widget. Scale once into our 374-unit canvas.
        float scale=374f/530f;
        float width=99*scale, advance=84*scale;
        float start=center-(width+(digits.length()-1)*advance)/2;
        for (int i=0; i<digits.length(); i++) {
            int digit=digits.charAt(i)-'0';
            float x=start+i*advance;
            bounds.set(x,88-79*scale,x+width,88);
            drawDigitHalf(canvas,clockDigits[digit][0]);
            bounds.set(x,88,x+width,88+83*scale);
            drawDigitHalf(canvas,clockDigits[digit][1]);
        }
    }

    private void drawDigitHalf(Canvas canvas, Bitmap bitmap) {
        int save=canvas.save();
        canvas.scale(1,-1,bounds.centerX(),bounds.centerY());
        canvas.drawBitmap(bitmap,null,bounds,digitPaint);
        canvas.restoreToCount(save);
    }

    private void text(Canvas canvas, String value, float x, float y, float size, int color, Paint.Align align) {
        paint.setColor(color);
        paint.setTextAlign(align);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        paint.setTextSize(size);
        canvas.drawText(value, x, y, paint);
    }
}
