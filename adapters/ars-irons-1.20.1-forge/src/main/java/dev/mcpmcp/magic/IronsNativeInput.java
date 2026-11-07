package dev.mcpmcp.magic;

import com.google.gson.JsonObject;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.lang.reflect.*;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Pinned to Iron's 3.16.3. Never posts Forge/global keyboard events or constructs packets. */
final class IronsNativeInput {
    private static final String BASE="io.redspace.ironsspellbooks.player.";
    static JsonObject submit(String mod,String action,JsonObject args,JsonObject selected)throws Exception {
        JsonObject dependency=IronsRuntimeDependency.check();if(dependency!=null)return dependency;
        Class<?> keys=Reflect.type(BASE+"KeyMappings"),extended=Reflect.type(BASE+"ExtendedKeyMapping");
        List<?> quick=Reflect.list(Reflect.field(keys,"QUICK_CAST_MAPPINGS"));
        int index=integer(args,"index",-1);
        if(action.equals("quick_cast")&&(index<0||index>=quick.size()))return error("quick_cast_key_not_available");
        KeyMapping target=(KeyMapping)(action.equals("quick_cast")?quick.get(index):Reflect.field(keys,"SPELLBOOK_CAST_ACTIVE_KEYMAP"));
        List<KeyMapping> mappings=new ArrayList<>();
        for(String name:List.of("SPELLBOOK_CAST_ACTIVE_KEYMAP","SPELL_WHEEL_KEYMAP","SPELL_WHEEL_TOGGLE_KEYMAP","SPELLBAR_SCROLL_MODIFIER_KEYMAP"))mappings.add((KeyMapping)Reflect.field(keys,name));
        for(Object mapping:quick)mappings.add((KeyMapping)mapping);
        if((Boolean)Reflect.field(Reflect.field(Reflect.type("io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay"),"instance"),"active"))return error("close_spell_wheel_first");
        // Read-only private input flags are necessary: public isDown() hides inactive modifiers,
        // and consume() would destroy a user's pending input. These fixed fields are never written.
        Field down=ObfuscationReflectionHelper.findField(KeyMapping.class,"f_90817_");
        Field clicks=ObfuscationReflectionHelper.findField(KeyMapping.class,"f_90818_");
        Field wasDown=inputFlag(extended,"wasDown"),pending=inputFlag(extended,"canBeConsumed");
        Method consume=extended.getMethod("consume");
        Method handler=Reflect.type(BASE+"ClientInputEvents").getMethod("onClientTick",TickEvent.ClientTickEvent.class);
        List<NativeCast.Signal> signals=new ArrayList<>();NativeCast.Signal pulse=null;
        for(KeyMapping mapping:mappings){
            NativeCast.Signal signal=new NativeCast.Signal(){
                public boolean down()throws IllegalAccessException{return down.getBoolean(mapping)||wasDown.getBoolean(mapping);}
                public boolean pending()throws IllegalAccessException{return clicks.getInt(mapping)>0||pending.getBoolean(mapping);}
                public void setDown(boolean value){mapping.setDown(value);}
                public void clearOwnedPulse()throws ReflectiveOperationException{consume.invoke(mapping);}
            };
            signals.add(signal);if(mapping==target)pulse=signal;
        }
        // Invoke this one mod subscriber directly, not MinecraftForge.EVENT_BUS.post().
        // Its verified body consumes one pulse and sends CastPacket/QuickCastPacket normally.
        return NativeCast.submit(mod,selected,args,signals,pulse,()->handler.invoke(null,new TickEvent.ClientTickEvent(TickEvent.Phase.END)));
    }
    private static Field inputFlag(Class<?> type,String name)throws ReflectiveOperationException {
        Field field=type.getDeclaredField(name);
        if(!field.trySetAccessible())throw new IllegalAccessException("native_input_flag_unavailable_"+name);
        return field;
    }
    private IronsNativeInput(){}
}
