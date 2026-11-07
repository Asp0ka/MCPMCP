package dev.mcpmcp.forge;

/** Runs without a Minecraft client; covers policy and ownership semantics. */
public final class UiSafetyTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static void denied(Runnable action){try{action.run();throw new AssertionError("permission accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        for(String mode:new String[]{"NORMAL","HACK"}){
            denied(()->UiSafety.authorize(mode,true,false,false));
            denied(()->UiSafety.authorize(mode,false,true,false));
            denied(()->UiSafety.authorize(mode,false,false,true));
            UiSafety.authorize(mode,false,false,false);checks++;
        }
        for(String mode:new String[]{"CHEAT","HACK_CHEAT"}){
            UiSafety.authorize(mode,false,true,true);checks++;
            denied(()->UiSafety.authorize(mode,true,false,false));
        }
        check(UiSafety.command("  /time set day  "),"normalized slash command");
        check(!UiSafety.command("hello /world"),"ordinary chat");
        for(int enter:new int[]{257,335}){
            denied(()->UiSafety.authorizeKey("NORMAL",true,enter,false," /time set day "));
            denied(()->UiSafety.authorizeKey("HACK",true,enter,false,"/give @s diamond"));
            UiSafety.authorizeKey("NORMAL",true,enter,false,"hello /world");checks++;
            UiSafety.authorizeKey("NORMAL",true,enter,true,"/time set day");checks++;
        }
        UiSafety.authorizeKey("NORMAL",true,84,false,"/time set day");checks++;
        UiSafety.authorizeKey("NORMAL",false,257,false,"/time set day");checks++;
        UiSafety.authorizeKey("CHEAT",true,257,false,"/time set day");checks++;
        UiInputs inputs=new UiInputs();int[] released={0};
        check(!inputs.acquire("player",true,()->released[0]++),"do not own player input");
        inputs.release("player");check(released[0]==0,"unowned release is inert");
        check(inputs.acquire("key",false,()->released[0]++),"own automation key");
        check(!inputs.acquire("key",false,()->released[0]+=100),"duplicate press does not overwrite owner");
        inputs.releaseAll();inputs.releaseAll();check(released[0]==1,"idempotent cleanup");
        inputs.acquire("mouse",false,()->{released[0]++;throw new IllegalStateException();});
        inputs.acquire("key",false,()->released[0]++);inputs.releaseAll();
        check(released[0]==3,"cleanup continues after failing listener");
        System.out.println("UiSafetyTest: "+checks+" checks passed");
    }
}
