package dev.sense.lockscreen;

/** Authentication ordering is independent of gesture handling and testable without changing a PIN. */
final class UnlockGate {
    interface Callback {void success();void cancelled();}
    interface Keyguard {boolean locked();void dismiss(Callback callback);}
    private final Keyguard keyguard;private final java.util.function.IntConsumer launch;private final Runnable cancel;private boolean pending;private int generation;
    UnlockGate(Keyguard keyguard,java.util.function.IntConsumer launch,Runnable cancel){this.keyguard=keyguard;this.launch=launch;this.cancel=cancel;}
    void request(int slot){if(pending)return;pending=true;int attempt=++generation;if(!keyguard.locked()){finish(attempt,slot);return;}keyguard.dismiss(new Callback(){public void success(){finish(attempt,slot);}public void cancelled(){reset(attempt);}});}
    private void finish(int attempt,int slot){if(!pending||attempt!=generation)return;if(keyguard.locked()){reset(attempt);return;}pending=false;launch.accept(slot);}
    private void reset(int attempt){if(!pending||attempt!=generation)return;pending=false;cancel.run();}
}
