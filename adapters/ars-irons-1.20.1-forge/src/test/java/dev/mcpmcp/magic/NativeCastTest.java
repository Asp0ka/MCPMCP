package dev.mcpmcp.magic;

import com.google.gson.JsonObject;
import dev.mcpmcp.core.Jobs;
import dev.mcpmcp.core.PluginStep;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Input seam fixtures only: does not claim network/server casting success. */
public final class NativeCastTest {
    private static int assertions;
    private static void check(boolean value,String why){assertions++;if(!value)throw new AssertionError(why);}
    private static final class Signal implements NativeCast.Signal {
        boolean down,pending;int presses,releases,clears;
        public boolean down(){return down;}
        public boolean pending(){return pending;}
        public void setDown(boolean value){down=value;if(value){presses++;pending=true;}else releases++;}
        public void clearOwnedPulse(){clears++;pending=false;}
        void consume(){pending=false;}
    }
    private static JsonObject selection(String mod){return obj("source","mainhand","source_index",0,"spell",obj("spell_id",mod+":oakskin"));}
    private static JsonObject args(String mod){return obj("expected_spell",mod+":oakskin","expected_source","mainhand","expected_source_index",0);}
    public static void main(String[] ignored)throws Exception {
        for(String mod:List.of("irons_spellbooks","traveloptics")){
            Signal target=new Signal(),otherModSamePhysicalKey=new Signal();int[] calls={0};
            JsonObject r=NativeCast.submit(mod,selection(mod),args(mod),List.of(target),target,()->{calls[0]++;check(target.down&&target.pending,"native handler sees pulse");target.consume();});
            check(r.get("status").getAsString().equals("submitted")&&!r.get("observed_success").getAsBoolean(),"submitted needs effects readback");
            check(calls[0]==1&&target.presses==1&&target.releases==1&&!target.down&&!target.pending,"exactly one pulse, handler and release");
            check(otherModSamePhysicalKey.presses==0&&!otherModSamePhysicalKey.pending,"same physical key in another mod untouched");
            for(String changed:List.of("expected_spell","expected_source","expected_source_index")){
                Signal s=new Signal();JsonObject a=args(mod);if(changed.equals("expected_source_index"))a.addProperty(changed,1);else a.addProperty(changed,"changed");
                r=NativeCast.submit(mod,selection(mod),a,List.of(s),s,()->{throw new AssertionError("must not dispatch changed identity");});
                check(r.has("error")&&s.presses==0,"identity checked before input: "+changed);
            }
            for(boolean queued:List.of(false,true)){
                Signal t=new Signal(),busy=new Signal();busy.pending=queued;busy.down=!queued;
                r=NativeCast.submit(mod,selection(mod),args(mod),List.of(t,busy),t,()->{throw new AssertionError("busy mod input must not dispatch");});
                check(r.get("error").getAsString().equals("native_input_busy")&&t.presses==0&&busy.releases==0&&busy.clears==0,"held/pending mod input preserved");
            }
            Signal failed=new Signal();
            r=NativeCast.submit(mod,selection(mod),args(mod),List.of(failed),failed,()->{throw new IllegalStateException("fixture handler failure");});
            check(r.get("delivery").getAsString().equals("unknown")&&failed.presses==1&&!failed.down&&!failed.pending&&failed.clears==1,"failure releases and clears only owned pulse; no retry");
            Signal unconsumed=new Signal();
            r=NativeCast.submit(mod,selection(mod),args(mod),List.of(unconsumed),unconsumed,()->{});
            check(r.get("error").getAsString().equals("native_input_not_consumed")&&!unconsumed.down&&!unconsumed.pending,"unconsumed pulse cannot cast on later tick");
            Signal quick0=new Signal(),quick1=new Signal();
            r=NativeCast.submit(mod,selection(mod),args(mod),List.of(quick0,quick1),quick1,()->{check(!quick0.pending&&quick1.pending,"only selected quick mapping pulsed");quick1.consume();});
            check(r.has("status")&&quick0.presses==0&&quick1.presses==1,"quick cast keeps exact index");
        }
        Jobs jobs=new Jobs();Signal once=new Signal();int[] calls={0};
        Jobs.Step submission=()->NativeCast.submit("irons_spellbooks",selection("irons_spellbooks"),args("irons_spellbooks"),List.of(once),once,()->{calls[0]++;once.consume();});
        jobs.submit("native-once","irons_spellbooks.cast",obj(),new PluginStep(submission,null),1,10000);
        jobs.submit("native-once","irons_spellbooks.cast",obj(),new PluginStep(submission,null),1,10000);
        jobs.pump(1);jobs.pump(1);check(calls[0]==1&&once.presses==1,"duplicate action id and repeated pumps submit once");
        jobs.submit("native-cancel","irons_spellbooks.cast",obj(),new PluginStep(submission,null),1,10000);
        jobs.cancel("native-cancel");jobs.pump(1);check(calls[0]==1,"cancel before native job executes sends none");
        System.out.println("NativeCastTest PASS assertions="+assertions+"; input fixtures only; no game execution");
    }
}
