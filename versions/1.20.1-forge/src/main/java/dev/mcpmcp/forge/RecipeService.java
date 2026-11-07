package dev.mcpmcp.forge;

import com.google.gson.*;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import static dev.mcpmcp.core.Jsons.*;

/** Vanilla recipe manager provider; has no JEI/REI types or runtime dependency. */
public final class RecipeService {
    public static JsonObject read(JsonObject args) {
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return obj("error","world_unavailable","reason","Recipe synchronization requires a client world");
        try {
            RecipeManager manager=mc.level.getRecipeManager();int budget=Observations.limit(args,128);JsonArray entries=new JsonArray();boolean[] clipped={false};
            String id=str(args,"id","");JsonObject d;boolean more=false;
            if(!id.isEmpty()) {
                ResourceLocation key=ResourceLocation.tryParse(id);if(key==null)throw new IllegalArgumentException("invalid_recipe_id");
                var found=manager.byKey(key);if(found.isPresent())entries.add(describe(found.get(),mc,args,new int[]{Math.max(0,ObservBounds.budget(Runtime.config().scanBudget)-1)},clipped));
                d=obj("recipes",entries,"examined",found.isPresent()?1:0,"next_cursor",null);
            } else {
                // getRecipes() materializes every recipe; getRecipeIds() is a lazy stream over existing maps.
                ObservPages.Page<ResourceLocation> page=ObservPages.take("recipes",manager,args,budget,()->manager.getRecipeIds().iterator());
                int[] remaining={Math.max(0,ObservBounds.budget(Runtime.config().scanBudget)-page.candidates().size())};
                for(ResourceLocation key:page.candidates()) {
                    var found=manager.byKey(key);if(found.isEmpty())continue;Recipe<?> recipe=found.get();ItemStack result;
                    try{result=recipe.getResultItem(mc.level.registryAccess());}catch(RuntimeException ex){result=ItemStack.EMPTY;}
                    if(!RecipeQuery.matches(args,key.toString(),BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString(),BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),result.getHoverName().getString()))continue;
                    entries.add(describe(recipe,mc,args,remaining,clipped));
                }
                more=page.more();d=obj("recipes",entries,"examined",page.candidates().size(),"examined_total",page.examined(),"next_cursor",page.next());
            }
            d.addProperty("provider","vanilla");d.addProperty("synchronized_only",true);d.addProperty("detail_truncated",clipped[0]);
            d.addProperty("dynamic_recipe_note","Special crafting, brewing, anvil and enchantment rules may be dynamic rather than fixed recipe entries; empty output is not proof of no recipe");
            return ObservJson.envelope(d,"vanilla_client_recipe_manager","synchronized_recipes_page",!more&&!clipped[0],more||clipped[0]);
        }catch(IllegalArgumentException e){return obj("error","invalid_arguments","reason",e.getMessage());}
    }
    private static JsonObject describe(Recipe<?> recipe,Minecraft mc,JsonObject args,int[] budget,boolean[] clipped) {
        JsonObject out=obj("id",recipe.getId().toString(),"type",BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString(),
            "serializer",BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString(),"special",recipe.isSpecial(),"group",ObservJson.clipped(recipe.getGroup(),256));
        try{out.add("result",ObservValues.item(recipe.getResultItem(mc.level.registryAccess()),Observations.detailed(args)));}
        catch(RuntimeException ex){out.add("result",obj("status","unknown","reason","custom_result_requires_inputs_or_failed"));}
        if(recipe instanceof ShapedRecipe s){out.addProperty("width",s.getWidth());out.addProperty("height",s.getHeight());}
        if(recipe instanceof AbstractCookingRecipe cooking){out.addProperty("cooking_ticks",cooking.getCookingTime());out.addProperty("experience",cooking.getExperience());}
        JsonArray ingredients=new JsonArray();int n=0;
        for(Ingredient ingredient:recipe.getIngredients()) {
            if(n>=64||budget[0]<=0){clipped[0]=true;break;}budget[0]--;ingredients.add(ingredient(ingredient,n++,budget,clipped));
        }
        // Vanilla smithing does not implement getIngredients(); read only verified Ingredient-typed fields.
        // This is typed data inspection, never arbitrary reflective method invocation.
        if(n==0&&(recipe instanceof SmithingTransformRecipe||recipe instanceof SmithingTrimRecipe)) {
            for(Field field:recipe.getClass().getDeclaredFields())if(field.getType()==Ingredient.class) {
                if(budget[0]<=0){clipped[0]=true;break;}budget[0]--;
                try {if(!field.trySetAccessible())throw new IllegalAccessException();
                    JsonObject entry=ingredient((Ingredient)field.get(recipe),n++,budget,clipped);entry.addProperty("declared_field",field.getName());ingredients.add(entry);
                } catch(ReflectiveOperationException|RuntimeException ex) {out.addProperty("ingredients_status","unknown_private_data_access_denied");}
            }
        }
        out.add("ingredients",ingredients);out.addProperty("ingredients_semantics",recipe.isSpecial()?"dynamic_inputs_may_not_be_fully_enumerated":"declared_ingredient_definitions");return out;
    }
    private static JsonObject ingredient(Ingredient ingredient,int slot,int[] budget,boolean[] clipped) {
        JsonObject out=obj("slot",slot);JsonArray definitions=new JsonArray();
        if(ingredient.isVanilla()) {
            // Avoid getItems(): expanding a tag builds an array proportional to the entire item registry.
            try {
                Field values=null;for(Field f:Ingredient.class.getDeclaredFields())if(f.getType()==Ingredient.Value[].class){if(values!=null)throw new IllegalStateException("ambiguous");values=f;}
                if(values==null||!values.trySetAccessible())throw new IllegalAccessException();
                Ingredient.Value[] source=(Ingredient.Value[])values.get(ingredient);int count=0;
                for(Ingredient.Value value:source) {
                    if(count>=64||budget[0]<=0){clipped[0]=true;break;}budget[0]--;count++;
                    if(value instanceof Ingredient.ItemValue||value instanceof Ingredient.TagValue)definitions.add(ObservJson.bounded(value.serialize(),32,256));
                    else definitions.add(obj("status","unknown_custom_ingredient_value","class",value.getClass().getName()));
                }
                out.addProperty("definitions_total",source.length);out.addProperty("truncated",count<source.length);
            } catch(ReflectiveOperationException|RuntimeException ex) {out.addProperty("status","unknown_vanilla_ingredient_data_access_failed");}
        } else {
            // Forge custom serializers can require arbitrary inputs. Expose bounded provider metadata honestly.
            try{out.add("definition",ObservJson.bounded(ingredient.toJson(),64,256));}
            catch(RuntimeException ex){out.addProperty("status","unknown_custom_ingredient_serialization_failed");}
        }
        out.add("alternatives",definitions);return out;
    }
    private RecipeService(){}
}
