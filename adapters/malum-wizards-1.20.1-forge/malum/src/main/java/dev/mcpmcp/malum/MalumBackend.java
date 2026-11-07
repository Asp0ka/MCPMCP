package dev.mcpmcp.malum;

import com.google.gson.*;
import dev.mcpmcp.magic.NativeMagicBackend;
import com.sammy.malum.common.recipe.*;
import com.sammy.malum.common.block.curiosities.spirit_altar.SpiritAltarBlockEntity;
import com.sammy.malum.common.block.curiosities.spirit_crucible.SpiritCrucibleCoreBlockEntity;
import com.sammy.malum.common.block.curiosities.ritual_plinth.RitualPlinthBlockEntity;
import com.sammy.malum.common.block.curiosities.weavers_workbench.WeaversWorkbenchBlockEntity;
import com.sammy.malum.common.block.storage.MalumItemHolderBlockEntity;
import com.sammy.malum.common.block.storage.jar.SpiritJarBlockEntity;
import com.sammy.malum.common.container.WeaversWorkbenchContainer;
import team.lodestar.lodestone.systems.recipe.IngredientWithCount;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Pinned Malum 1.20.1-1.6.7 public fields; see saved javap evidence. */
final class MalumBackend extends NativeMagicBackend {
    MalumBackend(){super("malum","1.20.1-1.6.7","ceda6ce4cc19de21ff6c21a732d8f9270ecc83cdae69027b13aaa24b536307f8","com.sammy.malum.common.recipe.");}
    protected List<String> knownRecipeTypes(){return List.of("malum:spirit_infusion","malum:spirit_focusing","malum:spirit_repair","malum:runeworking","malum:spirit_transmutation","malum:favor_of_the_void");}
    protected List<String> limitations(){return List.of(
        "Recipes come only from the current client RecipeManager, including any synchronized datapack changes.",
        "RitualRecipeReloadListener is a separate server reload-listener path; global plinth ritual recipes are unknown here.",
        "Altar isCrafting/recipe and crucible recipe are not loaded from the inspected sync tags: reported unknown, not inferred from progress.",
        "Weaver workbench items are read from its real open menu only; no block tag inventory sync was found.",
        "One normal native right-click only; no automatic rituals, spirit generation, crafting or direct inventory/field mutation.");}
    private static JsonObject counted(IngredientWithCount c){JsonObject o=ingredient(c.ingredient);o.addProperty("count",c.count);return o;}
    private static JsonArray counted(List<IngredientWithCount> in){JsonArray a=new JsonArray();for(var c:in)a.add(counted(c));return a;}
    protected JsonObject specialRecipe(Recipe<?> recipe){
        JsonObject o=obj("complete",true);
        if(recipe instanceof AbstractSpiritListMalumRecipe r){
            JsonArray spirits=new JsonArray();for(var s:r.spirits)spirits.add(obj("spirit",s.type.identifier,"count",s.count,"item",stack(s.getStack())));o.add("spirits",spirits);
        }
        if(recipe instanceof SpiritInfusionRecipe r){o.add("input",counted(r.input));o.add("extra_items",counted(r.extraItems));o.add("output",stack(r.output));o.addProperty("copy_input_nbt",r.useNbtFromInput);}
        else if(recipe instanceof SpiritFocusingRecipe r){o.add("input",ingredient(r.input));o.add("output",stack(r.output));o.addProperty("time",r.time);o.addProperty("durability_cost",r.durabilityCost);}
        else if(recipe instanceof RunicWorkbenchRecipe r){o.add("primary_input",counted(r.primaryInput));o.add("secondary_input",counted(r.secondaryInput));o.add("output",stack(r.output));}
        else if(recipe instanceof SpiritRepairRecipe r){JsonArray inputs=new JsonArray();for(var item:r.inputs)inputs.add(stack(new ItemStack(item)));o.add("repairable_items",inputs);o.add("repair_material",counted(r.repairMaterial));finite(o,"durability_percentage",r.durabilityPercentage);o.addProperty("output","dynamic_input_repair; unknown_without_actual_input");}
        else if(recipe instanceof SpiritTransmutationRecipe r){o.add("input",ingredient(r.ingredient));o.add("output",stack(r.output));o.addProperty("group",r.group);}
        else if(recipe instanceof FavorOfTheVoidRecipe r){o.add("input",ingredient(r.input));o.add("output",stack(r.output));}
        else {o.addProperty("complete",false);o.addProperty("details","unknown_unmapped_recipe_class");}
        return o;
    }
    protected boolean supported(BlockEntity e){return e instanceof SpiritAltarBlockEntity||e instanceof SpiritCrucibleCoreBlockEntity||e instanceof RitualPlinthBlockEntity||e instanceof SpiritJarBlockEntity||e instanceof MalumItemHolderBlockEntity||e instanceof WeaversWorkbenchBlockEntity;}
    protected JsonObject specialBlock(BlockEntity e){
        JsonObject o=unknown();
        if(e instanceof SpiritAltarBlockEntity b){
            o.add("items",obj("input",inventory(b.inventory),"extras",inventory(b.extrasInventory),"spirits",inventory(b.spiritInventory)));
            o.addProperty("progress",b.progress);o.addProperty("idle_progress",b.idleProgress);finite(o,"spirit_amount",b.spiritAmount);finite(o,"speed",b.speed);
            o.addProperty("is_crafting","unknown_not_sync_loaded");
        }else if(e instanceof SpiritCrucibleCoreBlockEntity b){
            o.add("items",obj("input",inventory(b.inventory),"spirits",inventory(b.spiritInventory),"augments",inventory(b.augmentInventory),"core_augments",inventory(b.coreAugmentInventory)));
            finite(o,"progress",b.progress);finite(o,"spirit_amount",b.spiritAmount);o.addProperty("queued_cracks",b.queuedCracks);o.addProperty("tuning_type",b.tuningType==null?"unknown":b.tuningType.name());
        }else if(e instanceof RitualPlinthBlockEntity b){
            o.add("items",obj("input",inventory(b.inventory),"extras",inventory(b.extrasInventory)));o.addProperty("progress",b.progress);o.addProperty("spirit_amount",b.spiritAmount);
            o.addProperty("setup_complete",b.setupComplete);finite(o,"active_duration",b.activeDuration);
            o.addProperty("ritual_type",b.ritualType==null?"unknown":b.ritualType.identifier.toString());o.addProperty("ritual_tier",b.ritualTier==null?"unknown":b.ritualTier.identifier.toString());
        }else if(e instanceof SpiritJarBlockEntity b){
            o.add("resources",obj("spirit",b.type==null?"unknown_or_empty":b.type.identifier,"count",b.count));
        }else if(e instanceof MalumItemHolderBlockEntity b){o.add("items",inventory(b.getSuppliedInventory()));}
        else if(e instanceof WeaversWorkbenchBlockEntity){o.addProperty("items","unknown_until_real_menu_sync");}
        o.addProperty("provenance","Inspected load/save fields and Lodestone update-tag path; current client copy, may be stale or locally animated. No server read.");return o;
    }
    protected BlockEntity menuOwner(AbstractContainerMenu menu){return menu instanceof WeaversWorkbenchContainer m?m.blockEntity:null;}
}
