package dev.mcpmcp.forge;

import com.google.gson.JsonObject;
import java.util.Locale;
import static dev.mcpmcp.core.Jsons.*;

public final class RecipeQuery {
    public static boolean matches(JsonObject args,String id,String type,String output,String name) {
        String exact=str(args,"id","");String wantedType=str(args,"type","");String wantedOutput=str(args,"output","");
        String q=str(args,"query","").toLowerCase(Locale.ROOT);
        if(q.length()>256||exact.length()>256||wantedType.length()>256||wantedOutput.length()>256)throw new IllegalArgumentException("recipe_filter_too_long");
        return (exact.isEmpty()||exact.equals(id))&&(wantedType.isEmpty()||wantedType.equals(type))&&
            (wantedOutput.isEmpty()||wantedOutput.equals(output))&&
            (q.isEmpty()||id.toLowerCase(Locale.ROOT).contains(q)||output.toLowerCase(Locale.ROOT).contains(q)||name.toLowerCase(Locale.ROOT).contains(q));
    }
    private RecipeQuery(){}
}
