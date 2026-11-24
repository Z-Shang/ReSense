package dev.sense.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** Real package-manager broadcasts; the temporary fixture APK has no code or data. */
final class AppCatalogInstrumentation {
    private static final String FIXTURE="dev.sense.launcher.catalogtest.fixture";
    private final Instrumentation test;
    private AppCatalog catalog;
    AppCatalogInstrumentation(Instrumentation test){this.test=test;}
    void run(){
        Bundle result=new Bundle();boolean installed=false;int status=Activity.RESULT_OK;
        try{
            try{test.getTargetContext().getPackageManager().getPackageInfo(FIXTURE,0);throw new AssertionError("Fixture already installed; refusing to modify it");}catch(android.content.pm.PackageManager.NameNotFoundException expected){}
            test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            test.runOnMainSync(()->catalog=AppCatalog.get(test.getTargetContext()));
            await(()->catalog.loaded() && !containsFixture(),"Initial catalog");
            if(!shell("pm install -r /data/local/tmp/sense-catalog-fixture.apk").contains("Success"))throw new AssertionError("Fixture install failed");
            installed=true;await(this::containsFixture,"Install broadcast refresh");
            shell("pm disable-user --user 0 "+FIXTURE);await(()->!containsFixture(),"Disable broadcast refresh");
            shell("pm enable --user 0 "+FIXTURE);await(this::containsFixture,"Enable broadcast refresh");
            if(!shell("pm uninstall "+FIXTURE).contains("Success"))throw new AssertionError("Fixture uninstall failed");
            installed=false;await(()->!containsFixture(),"Uninstall broadcast refresh");
            result.putString("result","Real install, disable, enable and uninstall broadcasts refreshed the cached app list without reopening the launcher");
        }catch(Throwable error){status=Activity.RESULT_CANCELED;result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(installed)try{shell("pm uninstall "+FIXTURE);}catch(Exception ignored){}}
        test.finish(status,result);
    }
    private boolean containsFixture(){return catalog.entries().stream().anyMatch(app->FIXTURE.equals(app.component().getPackageName()));}
    private String shell(String command)throws Exception{
        try(var input=new ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation().executeShellCommand(command))){return new String(input.readAllBytes(),StandardCharsets.UTF_8);}
    }
    private void await(BooleanSupplier condition,String label)throws Exception{
        long deadline=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<deadline){boolean[] ready={false};test.runOnMainSync(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(50);}
        throw new AssertionError(label+" timed out");
    }
}
