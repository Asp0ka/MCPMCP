package dev.mcpmcp.forge;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static dev.mcpmcp.core.Jsons.*;

/** Only received display messages are captured. Outgoing commands, tokens and credentials are never recorded. */
@Mod.EventBusSubscriber(modid="mcpmcp",value=Dist.CLIENT)
public final class ChatLog {
    private static final ChatHistory HISTORY=new ChatHistory(512,4096);
    /** Compatibility hook: Forge registers this class from its client-only subscriber annotation. */
    public static void init() {}
    @SubscribeEvent public static void received(ClientChatReceivedEvent event) {
        String kind=event instanceof ClientChatReceivedEvent.System s && s.isOverlay()?"overlay":event.isSystem()?"system":"chat";
        record(event.getMessage(),kind);
    }
    public static void record(Component message,String kind) {
        if(message==null)return;
        HISTORY.add(message.getString(),ObservJson.clipped(kind==null?"chat":kind,32),System.currentTimeMillis());
    }
    public static JsonObject read(JsonObject args) {
        init();long after=Observations.offset(args,"after",0);
        JsonObject page=HISTORY.read(after,ObservBounds.pageSize(integer(args,"limit",100),Runtime.config().scanBudget));
        page.addProperty("capture","received_since_mod_initialization");page.addProperty("persistent",false);
        boolean more=page.get("more").getAsBoolean();
        boolean gap=after<page.get("dropped_before").getAsLong();
        page.addProperty("history_gap",gap);
        return ObservJson.envelope(page,"forge_client_message_events","bounded_received_history",!more&&!gap,more||gap);
    }
    private ChatLog(){}
}
