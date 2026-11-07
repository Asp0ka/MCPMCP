package dev.mcpmcp.rs;
import com.google.gson.JsonObject;
import java.util.List;
/** Live implementation alone touches Minecraft. All exposed values are detached. */
public interface GridAccess {
 record Frame(String session,int menuId,boolean active,String gridType,boolean canCraft,JsonObject cursor,JsonObject crafting,JsonObject inventory,String search) {}
 record Entry(String id,String otherId,boolean craftable,long quantity,JsonObject item) {}
 record Command(String action,String stackId,String mode,int quantity,boolean single,int slot) {}
 Frame capture();
 List<Entry> entries();
 void submit(Command command);
}
