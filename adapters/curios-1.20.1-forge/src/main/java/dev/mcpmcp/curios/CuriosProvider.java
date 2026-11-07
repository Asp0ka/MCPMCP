package dev.mcpmcp.curios;
import dev.mcpmcp.api.*;
import java.util.Objects;
public final class CuriosProvider implements McpmcpPlugin {
 private CuriosService service;
 public CuriosProvider(){}
 public CuriosProvider(CuriosService service){this.service=Objects.requireNonNull(service);}
 private CuriosService service(){if(service==null)service=new CuriosService(new LiveCuriosAccess());return service;}
 public String id(){return "mcpmcp.curios";}
 public void register(PluginContext ctx){
  for(String n:Contract.READS)ctx.observer(n,100,a->{if(n.equals("curios")){Contract.validate(n,a);return Contract.discovery();}return service().read(n,a);});
  for(String n:Contract.ACTIONS)ctx.action(n,Contract.schema(n),"packet","normal",a->service().act(n,a));
 }
}
