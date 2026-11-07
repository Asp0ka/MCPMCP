package dev.mcpmcp.sophisticated;

import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.*;

/** Registration never observes or executes another kind, so providers cannot recurse. */
public abstract class AdapterBase implements McpmcpPlugin {
 public interface Access { JsonObject observe(JsonObject args) throws Exception; JsonObject execute(String op,JsonObject args) throws Exception; }
 private final String type; private final Access access;
 protected AdapterBase(String type) {this(type,new LiveAccess(type));}
 protected AdapterBase(String type,Access access) {this.type=type;this.access=access;}
 @Override public final String id() {return "mcpmcp."+Contract.namespace(type);}
 @Override public final void register(PluginContext context) {
  context.observer(Contract.namespace(type),100,args -> {
   Schema.validate(args,Contract.observationSchema());
   JsonObject out=Contract.describe(type);
   try {out.add("data",access.observe(args));} catch(Exception|LinkageError e) {out.add("data",failure(e));}
   return out;
  });
  for(String op:Contract.operations(type))context.action(Contract.namespace(type)+"."+op,Contract.schema(op),Contract.transport(op),Contract.permission(op),args -> {
   Contract.validate(args,op);
   try {return access.execute(op,args);} catch(Exception|LinkageError e) {return failure(e);}
  });
 }
 private static JsonObject failure(Throwable e) {return Jsons.obj("error","adapter_precondition_or_api_failed","reason",e.getClass().getSimpleName()+": "+e.getMessage());}
}
