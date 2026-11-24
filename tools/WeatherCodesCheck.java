package dev.sense.launcher;

public final class WeatherCodesCheck {
    public static void main(String[] args) {
        int[] known={0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,97,99};
        for(int code:known)if(WeatherCodes.description(code).equals("Conditions unavailable"))throw new AssertionError("Unmapped WMO code "+code);
        if(!WeatherCodes.description(2).equals("Partly cloudy"))throw new AssertionError("Wrong cloud description");
        if(!WeatherCodes.snow(85)||WeatherCodes.rain(85))throw new AssertionError("Snow shown as rain");
        if(!WeatherCodes.rain(66)||!WeatherCodes.rain(95))throw new AssertionError("Missing precipitation");
        if(WeatherCodes.rain(999)||WeatherCodes.snow(999))throw new AssertionError("Unknown code invented precipitation");
        if(!WeatherCodes.description(-1).equals("Conditions unavailable"))throw new AssertionError("Unknown fallback");
        for(int code:known)for(boolean day:new boolean[]{false,true})
            if(WeatherCodes.htcScene(code,day)==0)throw new AssertionError("Missing day/night scene mapping "+code);
        if(WeatherCodes.htcScene(0,false)!=33||WeatherCodes.htcScene(2,false)!=35)throw new AssertionError("Wrong night scene");
        if(WeatherCodes.htcScene(999,true)!=0)throw new AssertionError("Unknown code invented a scene");
        System.out.println("WMO mapping checks passed for 29 documented codes and unknown-code fallbacks");
    }
}
