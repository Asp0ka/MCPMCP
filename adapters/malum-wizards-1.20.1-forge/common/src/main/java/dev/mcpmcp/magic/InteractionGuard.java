package dev.mcpmcp.magic;

/** Pure checks also exercised without launching Minecraft. Native visibility is checked afterwards. */
public final class InteractionGuard {
    public record State(boolean connected,boolean sameDimension,boolean noScreen,boolean playerMenu,boolean cursorEmpty,
                        boolean alive,boolean canBuild,boolean loaded,boolean supported,boolean blockMatches,
                        boolean itemMatches,boolean countMatches,boolean nbtMatches){}
    public static String rejection(State s){
        if(!s.connected)return "not_in_world";
        if(!s.sameDimension)return "dimension_changed";
        if(!s.noScreen||!s.playerMenu)return "close_existing_screen_or_container_first";
        if(!s.cursorEmpty)return "cursor_not_empty";
        if(!s.alive||!s.canBuild)return "player_not_allowed_to_interact";
        if(!s.loaded)return "target_unloaded_or_outside_world";
        if(!s.supported)return "unsupported_work_block_entity";
        if(!s.blockMatches)return "target_block_changed";
        if(!s.itemMatches||!s.countMatches||!s.nbtMatches)return "held_item_changed";
        return null;
    }
    private InteractionGuard(){}
}
