package dev.sense.phone;

import android.net.Uri;
import android.telecom.*;
import java.util.*;

/** Debug-only local provider. Its phone account is registered only by instrumentation. */
public final class LocalTestConnectionService extends ConnectionService {
    static final List<LocalConnection> calls=new ArrayList<>();
    static final class LocalConnection extends Connection {
        char tone;boolean rejected;
        LocalConnection(Uri address,boolean incoming){
            setAddress(address,TelecomManager.PRESENTATION_ALLOWED);setCallerDisplayName("Sense local test",TelecomManager.PRESENTATION_ALLOWED);
            setAudioModeIsVoip(true);setConnectionCapabilities(CAPABILITY_HOLD|CAPABILITY_SUPPORT_HOLD|CAPABILITY_MUTE);
            if(incoming)setRinging();else setDialing();
        }
        @Override public void onAnswer(){setActive();}
        @Override public void onAnswer(int state){setActive();}
        @Override public void onHold(){setOnHold();}
        @Override public void onUnhold(){setActive();}
        @Override public void onPlayDtmfTone(char digit){tone=digit;}
        @Override public void onReject(){rejected=true;setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));destroy();}
        @Override public void onDisconnect(){setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));destroy();}
    }
    private Connection create(ConnectionRequest request,boolean incoming){
        Uri address=request.getAddress();if(address==null&&request.getExtras()!=null)address=request.getExtras().getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,Uri.class);
        if(address==null)address=Uri.parse("tel:5550177999");LocalConnection connection=new LocalConnection(address,incoming);calls.add(connection);return connection;
    }
    @Override public Connection onCreateIncomingConnection(PhoneAccountHandle handle,ConnectionRequest request){return create(request,true);}
    @Override public Connection onCreateOutgoingConnection(PhoneAccountHandle handle,ConnectionRequest request){return create(request,false);}
}
