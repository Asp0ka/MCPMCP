package dev.mcpmcp.magic;

import com.google.gson.JsonObject;
import java.util.List;
import static dev.mcpmcp.core.Jsons.*;

/** One synchronous mod-local pulse. No key codes, global input or resource writes. */
final class NativeCast {
    interface Signal {
        boolean down() throws ReflectiveOperationException;
        boolean pending() throws ReflectiveOperationException;
        void setDown(boolean value) throws ReflectiveOperationException;
        void clearOwnedPulse() throws ReflectiveOperationException;
    }
    @FunctionalInterface interface Handler {void run() throws Exception;}
    static JsonObject submit(String mod,JsonObject selected,JsonObject args,List<? extends Signal> signals,Signal target,Handler handler)throws Exception {
        if(!selected.has("spell")||!selected.getAsJsonObject("spell").has("spell_id"))return error("empty_selected_spell");
        String spell=str(selected.getAsJsonObject("spell"),"spell_id","");
        if(!spell.startsWith(mod+":")||!spell.equals(str(args,"expected_spell",""))||!str(selected,"source","").equals(str(args,"expected_source",""))||integer(selected,"source_index",-1)!=integer(args,"expected_source_index",-2))return error("selected_spell_or_source_changed");
        if(!signals.contains(target))return error("native_input_target_missing");
        for(Signal signal:signals)if(signal.down()||signal.pending())return error("native_input_busy");
        try {
            target.setDown(true);
            handler.run();
            if(target.pending())return obj("error","native_input_not_consumed","delivery","unknown","retry_safe",false);
            return obj("status","submitted","observed_success",false,"native_handler","ClientInputEvents.onClientTick","global_key_event",false,"verification","read_selection_mana_casting_cooldowns_and_actual_world_effect_after_server_sync","guard_timing","client selection checked in the same client-thread job before synchronous native handler; server selection/acceptance still requires readback");
        } catch(Exception|LinkageError e) {
            return obj("error","native_input_dispatch_failed","reason",e.getClass().getSimpleName(),"delivery","unknown","retry_safe",false);
        } finally {
            // The preflight proved no pre-existing target input. Remove only our pulse.
            target.setDown(false);
            if(target.pending())target.clearOwnedPulse();
        }
    }
    private NativeCast(){}
}
