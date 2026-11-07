package dev.mcpmcp.core;
import com.google.gson.*;
public final class PluginStepTest {
 public static void main(String[] args)throws Exception{
  int[] sent={0},poll={0};Jobs.Step step;
  try{Class<?> c=Class.forName("dev.mcpmcp.core.PluginStep");step=(Jobs.Step)c.getConstructor(Jobs.Step.class,java.util.function.BooleanSupplier.class).newInstance((Jobs.Step)()->{sent[0]++;return Jsons.obj("delivery","submitted");},(java.util.function.BooleanSupplier)()->++poll[0]>=3);}catch(ClassNotFoundException e){throw new AssertionError("missing verify state machine",e);}
  if(!step.tick().get("pending").getAsBoolean()||!step.tick().get("pending").getAsBoolean()||!step.tick().get("observed_success").getAsBoolean()||sent[0]!=1)throw new AssertionError("send once and poll");
  step.tick();if(sent[0]!=1||poll[0]!=3)throw new AssertionError("terminal tick reran work");
  int[] timeoutSends={0};Jobs jobs=new Jobs();Jobs.Step never=(Jobs.Step)Class.forName("dev.mcpmcp.core.PluginStep").getConstructor(Jobs.Step.class,java.util.function.BooleanSupplier.class).newInstance((Jobs.Step)()->{timeoutSends[0]++;return new JsonObject();},(java.util.function.BooleanSupplier)()->false);jobs.submit("timeout","verify",new JsonObject(),never,1,10);jobs.pump(1);Thread.sleep(25);jobs.pump(1);if(!jobs.result("timeout").toString().contains("task_timeout")||timeoutSends[0]!=1)throw new AssertionError("bounded verify timeout");
  System.out.println("PluginStepTest passed");
 }
}
