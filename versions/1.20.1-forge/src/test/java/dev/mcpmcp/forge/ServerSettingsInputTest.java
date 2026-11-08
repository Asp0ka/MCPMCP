package dev.mcpmcp.forge;

/** Public settings-input seam: typed input cannot write invalid port ranges. */
public final class ServerSettingsInputTest {
 public static void main(String[] args){
  int[] defaults=ServerSettingsInput.ports("5210","5220");
  if(defaults[0]!=5210||defaults[1]!=5220)throw new AssertionError("default range");
  int[] high=ServerSettingsInput.ports("65535","65535");
  if(high[0]!=65535||high[1]!=65535)throw new AssertionError("last port");
  for(String[] bad:new String[][]{{"","5220"},{"0","10"},{"1023","1033"},{"5210","5209"},{"5210","5221"},{"65536","65536"},{"-1","9"},{"5e3","5010"},{" 5210","5220"},{"9999999999999999","5220"}}){
   try{ServerSettingsInput.ports(bad[0],bad[1]);throw new AssertionError("invalid range accepted");}
   catch(IllegalArgumentException expected){}
  }
  System.out.println("ServerSettingsInputTest: valid/default/boundary and invalid typed ranges passed");
 }
}
