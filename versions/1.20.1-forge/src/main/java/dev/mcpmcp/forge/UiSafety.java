package dev.mcpmcp.forge;

/** Policy decisions shared by all UI mutation entry points. */
final class UiSafety {
    static boolean command(String text){return text.strip().startsWith("/");}
    static void authorizeKey(String mode,boolean chat,int key,boolean release,String text){
        authorize(mode,false,chat&&!release&&(key==257||key==335)&&command(text),false);
    }
    static void authorize(String mode,boolean settings,boolean command,boolean cheats){
        if(settings)throw new IllegalArgumentException("automation_cannot_change_mcp_settings");
        if((command||cheats)&&!mode.equals("CHEAT")&&!mode.equals("HACK_CHEAT"))
            throw new IllegalArgumentException("cheat_permission_required");
    }
    private UiSafety(){}
}
