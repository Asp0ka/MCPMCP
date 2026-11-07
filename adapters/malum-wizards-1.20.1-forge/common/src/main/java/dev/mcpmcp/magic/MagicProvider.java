package dev.mcpmcp.magic;

import com.google.gson.JsonObject;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.Schema;
import java.util.Objects;

public abstract class MagicProvider implements McpmcpPlugin {
    public interface Backend {JsonObject read(String part,JsonObject args)throws Exception; JsonObject interact(JsonObject args)throws Exception;}
    private final String mod; private final Backend backend;
    protected MagicProvider(String mod,Backend backend){this.mod=mod;this.backend=Objects.requireNonNull(backend);}
    public final String id(){return "mcpmcp."+mod;}
    public final void register(PluginContext context){
        for(String part:MagicContracts.OBSERVERS) context.observer(part.isEmpty()?mod:mod+"."+part,100,args->{
            Schema.validate(args,MagicContracts.schema(part));
            JsonObject result=backend.read(part,args);
            if(part.isEmpty())result.add("contract",MagicContracts.discovery(mod));
            return result;
        });
        context.action(mod+".interact",MagicContracts.schema("interact"),"client","normal",args->{Schema.validate(args,MagicContracts.schema("interact"));return backend.interact(args);});
    }
}
