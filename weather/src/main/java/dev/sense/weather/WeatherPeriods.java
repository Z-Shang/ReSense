package dev.sense.weather;

import org.json.*;
import java.time.*;
import java.util.*;

/** Three stock Now cards derived from actual data in the selected city's timezone. */
final class WeatherPeriods {
    record Period(String label,LocalDate date,int code,boolean day,double temperature){}
    record Day(LocalDate date,int code,double high,double low){}
    static List<Day> upcoming(JSONObject data,ZoneId zone,LocalDate today){
        List<Day> result=new ArrayList<>();JSONObject daily=data==null?null:data.optJSONObject("daily");
        for(int i=1;i<=5;i++){
            LocalDate date=today.plusDays(i);int index=index(daily,zone,date);double code=number(daily,"weather_code",index);
            result.add(new Day(date,Double.isFinite(code)?(int)code:-1,number(daily,"temperature_2m_max",index),number(daily,"temperature_2m_min",index)));
        }
        return result;
    }
    static List<Period> build(JSONObject data,ZoneId zone,LocalDate today){
        List<Period> result=new ArrayList<>();result.add(daily(data,zone,today,"Today"));
        JSONObject hourly=data==null?null:data.optJSONObject("hourly");
        JSONArray times=hourly==null?null:hourly.optJSONArray("time");
        double low=Double.NaN;Map<Integer,Integer> counts=new LinkedHashMap<>();
        long start=today.atTime(18,0).atZone(zone).toEpochSecond();
        long end=today.plusDays(1).atTime(6,0).atZone(zone).toEpochSecond();
        JSONObject daily=data==null?null:data.optJSONObject("daily");
        double sunset=number(daily,"sunset",index(daily,zone,today)),sunrise=number(daily,"sunrise",index(daily,zone,today.plusDays(1)));
        if(Double.isFinite(sunset) && Double.isFinite(sunrise) && sunrise>sunset){start=(long)sunset;end=(long)sunrise;}
        if(times!=null)for(int i=0;i<times.length();i++){
            long time=times.optLong(i);if(time<start || time>=end)continue;
            double isDay=number(hourly,"is_day",i);if(Double.isFinite(isDay) && isDay!=0)continue;
            double value=number(hourly,"temperature_2m",i);if(Double.isFinite(value))low=Double.isFinite(low)?Math.min(low,value):value;
            double code=number(hourly,"weather_code",i);
            if(Double.isFinite(code) && WeatherCodes.htcScene((int)code,false)!=0)counts.merge((int)code,1,Integer::sum);
        }
        if(!Double.isFinite(low))low=dailyLow(data,zone,today);
        int nightCode=-1,best=0;for(var item:counts.entrySet())if(item.getValue()>best){best=item.getValue();nightCode=item.getKey();}
        result.add(new Period("Tonight",today,nightCode,false,low));
        result.add(daily(data,zone,today.plusDays(1),"Tomorrow"));return result;
    }
    private static Period daily(JSONObject data,ZoneId zone,LocalDate date,String label){
        JSONObject daily=data==null?null:data.optJSONObject("daily");int index=index(daily,zone,date);
        double code=number(daily,"weather_code",index);
        return new Period(label,date,Double.isFinite(code)?(int)code:-1,true,number(daily,"temperature_2m_max",index));
    }
    private static double dailyLow(JSONObject data,ZoneId zone,LocalDate date){
        JSONObject daily=data==null?null:data.optJSONObject("daily");return number(daily,"temperature_2m_min",index(daily,zone,date));
    }
    private static int index(JSONObject series,ZoneId zone,LocalDate date){
        JSONArray times=series==null?null:series.optJSONArray("time");
        if(times!=null)for(int i=0;i<times.length();i++)if(Instant.ofEpochSecond(times.optLong(i)).atZone(zone).toLocalDate().equals(date))return i;
        return -1;
    }
    private static double number(JSONObject series,String key,int index){
        JSONArray values=series==null?null:series.optJSONArray(key);return values==null||index<0||values.isNull(index)?Double.NaN:values.optDouble(index,Double.NaN);
    }
}
