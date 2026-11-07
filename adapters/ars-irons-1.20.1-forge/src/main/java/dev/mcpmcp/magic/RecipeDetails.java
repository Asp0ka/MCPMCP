package dev.mcpmcp.magic;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import io.netty.buffer.Unpooled;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Encodes already synchronized recipe objects; never invokes assemble/matches or rolls outputs. */
final class RecipeDetails {
    static JsonObject encode(Recipe<?> recipe) {
        JsonObject r=obj("id",recipe.getId().toString(),"type",BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString(),"serializer",BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString(),"recipe_class",recipe.getClass().getName(),"execution_supported",false);
        try {
            r.add("result",stack(recipe.getResultItem(Minecraft.getInstance().level.registryAccess())));
            if(recipe.getClass().getName().startsWith("com.hollingsworth.arsnouveau.")) {
                // Exact target JAR's public asRecipe builds JSON from this synchronized recipe instance.
                JsonElement details=(JsonElement)Reflect.call(recipe,"asRecipe");r.add("details",details.deepCopy());r.addProperty("details_source","target_recipe.asRecipe; client synchronized fields");
                r.addProperty("details_complete",true);return r;
            }
            String type=r.get("type").getAsString();
            if(type.startsWith("irons_spellbooks:alchemist_cauldron_")) {
                switch(type) {
                    case "irons_spellbooks:alchemist_cauldron_fill" -> {r.add("input",ingredient((Ingredient)Reflect.call(recipe,"input")));r.add("fluid_result",fluid((FluidStack)Reflect.call(recipe,"result")));r.add("returned_item",stack((ItemStack)Reflect.call(recipe,"returned")));r.addProperty("must_fit_all",Reflect.bool(recipe,"mustFitAll"));}
                    case "irons_spellbooks:alchemist_cauldron_empty" -> {r.add("input",ingredient((Ingredient)Reflect.call(recipe,"input")));r.add("fluid_input",fluid((FluidStack)Reflect.call(recipe,"fluid")));r.add("item_result",stack((ItemStack)Reflect.call(recipe,"result")));}
                    case "irons_spellbooks:alchemist_cauldron_brew" -> {r.add("reagent",ingredient((Ingredient)Reflect.call(recipe,"reagent")));r.add("fluid_input",fluid((FluidStack)Reflect.call(recipe,"fluidIn")));JsonArray results=new JsonArray();for(Object f:Reflect.list(Reflect.call(recipe,"results")))results.add(fluid((FluidStack)f));r.add("fluid_results",results);Optional<?> by=(Optional<?>)Reflect.call(recipe,"byproduct");r.add("byproduct",by.isPresent()?stack((ItemStack)by.get()):JsonNull.INSTANCE);}
                    default -> throw new ReflectiveOperationException("unknown_cauldron_type");
                }
                r.addProperty("details_complete",true);return r;
            }
            if(recipe instanceof SmithingTransformRecipe) {
                FriendlyByteBuf bytes=new FriendlyByteBuf(Unpooled.buffer());
                try {serialize(recipe,bytes);r.add("template",ingredient(Ingredient.fromNetwork(bytes)));r.add("base",ingredient(Ingredient.fromNetwork(bytes)));r.add("addition",ingredient(Ingredient.fromNetwork(bytes)));r.add("result",stack(bytes.readItem()));r.addProperty("details_complete",bytes.readableBytes()==0);}
                finally{bytes.release();}return r;
            }
            if(recipe.getClass().getName().equals("com.github.L_Ender.cataclysm.crafting.WeaponfusionRecipe")) {
                r.add("base",ingredient((Ingredient)Reflect.call(recipe,"getbaseIngredient")));r.add("addition",ingredient((Ingredient)Reflect.call(recipe,"getAdditionIngredient")));r.addProperty("details_complete",true);return r;
            }
            JsonArray inputs=new JsonArray();for(Ingredient i:recipe.getIngredients())inputs.add(ingredient(i));r.add("ingredients",inputs);
            if(recipe instanceof ShapedRecipe shaped){r.addProperty("width",shaped.getWidth());r.addProperty("height",shaped.getHeight());}
            if(recipe instanceof AbstractCookingRecipe cooking){r.addProperty("cooking_time_ticks",cooking.getCookingTime());r.addProperty("experience",cooking.getExperience());}
            r.addProperty("details_complete",recipe instanceof CraftingRecipe||recipe instanceof AbstractCookingRecipe||recipe instanceof StonecutterRecipe);
            if(!r.get("details_complete").getAsBoolean())r.addProperty("unknown","additional_nonstandard_recipe_constraints");
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){r.addProperty("details_complete",false);r.addProperty("unknown","target_recipe_details_unavailable_"+e.getClass().getSimpleName());}
        return r;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void serialize(Recipe recipe,FriendlyByteBuf bytes){((RecipeSerializer)recipe.getSerializer()).toNetwork(bytes,recipe);}
    private static JsonObject ingredient(Ingredient i){JsonArray options=new JsonArray();ItemStack[] stacks=i.getItems();for(int n=0;n<Math.min(32,stacks.length);n++)options.add(stack(stacks[n]));return obj("alternatives",options,"total_alternatives",stacks.length,"complete",stacks.length<=32);}
    private static JsonObject stack(ItemStack s){return obj("item_id",BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),"count",s.getCount(),"nbt",s.hasTag()?s.getTag().toString():null);}
    private static JsonObject fluid(FluidStack f){return obj("fluid_id",BuiltInRegistries.FLUID.getKey(f.getFluid()).toString(),"amount_mb",f.getAmount(),"nbt",f.hasTag()?f.getTag().toString():null);}
    static JsonArray fluidsFromHandler(Object handler){JsonArray fluids=new JsonArray();if(handler instanceof IFluidHandler h)for(int i=0;i<Math.min(32,h.getTanks());i++)fluids.add(obj("tank",i,"fluid",fluid(h.getFluidInTank(i)),"capacity_mb",h.getTankCapacity(i)));return fluids;}
    private RecipeDetails(){}
}
