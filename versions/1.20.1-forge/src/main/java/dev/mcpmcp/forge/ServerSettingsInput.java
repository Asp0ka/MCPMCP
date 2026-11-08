package dev.mcpmcp.forge;

/** Port text is validated before any configuration mutation or disk write. */
public final class ServerSettingsInput {
 public static int[] ports(String first,String last){
  if(first==null||last==null||!first.matches("[0-9]{1,5}")||!last.matches("[0-9]{1,5}"))throw new IllegalArgumentException("invalid_server_ports");
  int start=Integer.parseInt(first),end=Integer.parseInt(last);
  if(start<1024||end>65535||end<start||end-start>10)throw new IllegalArgumentException("invalid_server_ports");
  return new int[]{start,end};
 }
 private ServerSettingsInput(){}
}
