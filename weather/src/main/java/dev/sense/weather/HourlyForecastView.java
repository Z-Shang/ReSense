package dev.sense.weather;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** HTC-style horizontally scrolling temperature area chart and aligned icon strip. */
public final class HourlyForecastView extends HorizontalScrollView {
    record Sample(long time,double temperature,int code,boolean day){}
    private final java.util.List<Sample> samples=new ArrayList<>();
    private final Map<Integer,Drawable> icons=new HashMap<>();
    private final Plot plot;
    private ZoneId zone=ZoneOffset.UTC;
    public HourlyForecastView(Context context,AttributeSet attrs){
        super(context,attrs);setFillViewport(true);setBackgroundColor(0xffe7eff2);
        plot=new Plot(context);addView(plot,new LayoutParams(1,-1));setHorizontalScrollBarEnabled(true);
    }
    void forecast(JSONObject data,ZoneId cityZone){
        zone=cityZone;samples.clear();icons.clear();
        JSONObject hourly=data==null?null:data.optJSONObject("hourly");JSONArray times=hourly==null?null:hourly.optJSONArray("time");
        long now=ZonedDateTime.now(zone).truncatedTo(java.time.temporal.ChronoUnit.HOURS).toEpochSecond();
        if(times!=null)for(int i=0;i<times.length() && samples.size()<24;i++){
            long time=times.optLong(i);if(time<now)continue;
            double t=number(hourly,"temperature_2m",i),code=number(hourly,"weather_code",i),day=number(hourly,"is_day",i);
            boolean daylight=Double.isFinite(day)?day!=0:Instant.ofEpochSecond(time).atZone(zone).getHour()>=6 && Instant.ofEpochSecond(time).atZone(zone).getHour()<18;
            samples.add(new Sample(time,t,Double.isFinite(code)?(int)code:-1,daylight));
        }
        StringBuilder description=new StringBuilder("Hourly temperature forecast. Swipe horizontally for the next 24 hours.");
        for(Sample sample:samples)description.append(' ').append(time(sample)).append(' ').append(degree(sample.temperature())).append(' ').append(WeatherCodes.description(sample.code())).append('.');
        plot.setContentDescription(description);plot.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        resize();plot.invalidate();
    }
    int sampleCount(){return samples.size();}
    double temperatureAt(int index){return samples.get(index).temperature();}
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    private void resize(){plot.setLayoutParams(new LayoutParams(Math.max(getWidth(),Math.round(dp(48)+dp(50)*Math.max(1,samples.size()))),LayoutParams.MATCH_PARENT));}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);resize();}
    private String time(Sample sample){return DateTimeFormatter.ofPattern("ha",Locale.getDefault()).withZone(zone).format(Instant.ofEpochSecond(sample.time()));}
    private static String degree(double t){return Double.isFinite(t)?Math.round(t)+"°":"—";}
    private static double number(JSONObject object,String name,int i){JSONArray values=object==null?null:object.optJSONArray(name);return values==null||values.isNull(i)?Double.NaN:values.optDouble(i,Double.NaN);}
    private final class Plot extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Plot(Context context){super(context);setFocusable(true);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            // HorizontalScrollView measures content with an unspecified width.
            // A plain View otherwise reports zero and collapses to the viewport.
            int width=Math.max(HourlyForecastView.this.getWidth(),Math.round(dp(48)+dp(50)*Math.max(1,samples.size())));
            int height=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?Math.max(HourlyForecastView.this.getHeight(),Math.round(dp(400))):MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(height,heightSpec));
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float left=dp(42),top=dp(24),bottom=Math.max(top+dp(50),getHeight()-dp(108)),step=dp(50);
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(Sample sample:samples)if(Double.isFinite(sample.temperature())){min=Math.min(min,sample.temperature());max=Math.max(max,sample.temperature());}
            if(!Double.isFinite(min)){paint.setColor(0xff344b57);paint.setTextSize(dp(16));canvas.drawText("Hourly forecast unavailable",left,top+dp(30),paint);return;}
            double tick=Math.max(1,Math.ceil((max-min+2)/6));min=Math.floor((min-1)/tick)*tick;max=Math.ceil((max+1)/tick)*tick;
            final double low=min,high=max;float right=Math.max(left+step,getWidth()-dp(8));
            paint.setTextSize(dp(11));paint.setStrokeWidth(dp(.6f));
            for(double value=min;value<=max+.001;value+=tick){float y=(float)(bottom-(value-min)/(max-min)*(bottom-top));paint.setColor(0xffbdcbd1);canvas.drawLine(left,y,right,y,paint);paint.setColor(0xff637b86);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(Math.round(value)+"°",left-dp(6),y+dp(4),paint);}
            Path line=new Path(),fill=new Path();boolean segment=false;float lastX=0;
            for(int i=0;i<samples.size();i++){
                Sample sample=samples.get(i);float x=left+i*step;
                paint.setColor(0xffbdcbd1);canvas.drawLine(x,top,x,bottom,paint);
                if(Double.isFinite(sample.temperature())){
                    float y=(float)(bottom-(sample.temperature()-low)/(high-low)*(bottom-top));
                    if(!segment){line.moveTo(x,y);fill.moveTo(x,bottom);fill.lineTo(x,y);segment=true;}else{line.lineTo(x,y);fill.lineTo(x,y);}lastX=x;
                }else if(segment){fill.lineTo(lastX,bottom);fill.close();segment=false;}
            }
            if(segment){fill.lineTo(lastX,bottom);fill.close();}
            paint.setStyle(Paint.Style.FILL);paint.setShader(new LinearGradient(0,top,0,bottom,0xddb5a26d,0x55dfd4a8,Shader.TileMode.CLAMP));canvas.drawPath(fill,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));paint.setColor(0xffa18a52);canvas.drawPath(line,paint);paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<samples.size();i++){
                Sample sample=samples.get(i);float x=left+i*step;paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(10));paint.setColor(0xff637b86);canvas.drawText(time(sample),x,bottom+dp(17),paint);
                if(Double.isFinite(sample.temperature())){float y=(float)(bottom-(sample.temperature()-low)/(high-low)*(bottom-top));paint.setColor(0xfff9faf8);canvas.drawCircle(x,y,dp(2),paint);}
                int htc=WeatherCodes.htcScene(sample.code(),sample.day());
                if(htc!=0){Drawable icon=icons.get(htc);if(icon==null){int id=getResources().getIdentifier(String.format(Locale.ROOT,"wx_condition_%02d",htc),"drawable",getContext().getPackageName());if(id!=0){icon=getContext().getDrawable(id);icons.put(htc,icon);}}
                    if(icon!=null){icon.setBounds(Math.round(x-dp(20)),Math.round(bottom+dp(27)),Math.round(x+dp(20)),Math.round(bottom+dp(67)));icon.draw(canvas);}}
                paint.setColor(0xff344b57);paint.setTextSize(dp(14));canvas.drawText(degree(sample.temperature()),x,bottom+dp(87),paint);
            }
        }
    }
}
