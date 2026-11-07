package dev.mcpmcp.ftbquests;
import dev.mcpmcp.api.*;
import java.util.Objects;
public final class FtbQuestsProvider implements McpmcpPlugin {
 private FtbService service;
 public FtbQuestsProvider(){}
 public FtbQuestsProvider(FtbService service){this.service=Objects.requireNonNull(service);}
 private FtbService service(){if(service==null)service=new FtbService(new LiveFtbAccess());return service;}
 public String id(){return "mcpmcp.ftbquests";}
 public void register(PluginContext ctx){
  for(String n:Contract.READS)ctx.observer(n,100,a->{if(n.equals("ftbquests")){Contract.validate(n,a);return Contract.discovery();}return service().read(n,a);});
  for(String n:Contract.ACTIONS)ctx.action(n,Contract.schema(n),"packet","normal",a->service().act(n,a));
 }
}
