package dev.sense.lockscreen;

/** One presentation per screen-off/unlock cycle; never a timer or an authentication substitute. */
final class AfterUnlockPolicy {
    private boolean armed;
    AfterUnlockPolicy(boolean initiallyLocked){armed=initiallyLocked;}
    boolean armed(){return armed;}
    void screenOff(){armed=true;}
    void suppress(){armed=false;}
    boolean userPresent(boolean enabled,boolean permitted,boolean interactive,boolean locked,boolean showing){
        if(!armed||!interactive||locked)return false;
        armed=false;
        return enabled&&permitted&&!showing;
    }
}
