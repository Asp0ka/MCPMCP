package dev.mcpmcp.wizards;

import com.google.gson.*;
import dev.mcpmcp.magic.NativeMagicBackend;
import mod.maxbogomol.wizards_reborn.common.recipe.*;
import mod.maxbogomol.wizards_reborn.common.block.wissen_altar.WissenAltarBlockEntity;
import mod.maxbogomol.wizards_reborn.common.block.arcane_workbench.ArcaneWorkbenchBlockEntity;
import mod.maxbogomol.wizards_reborn.common.block.wissen_crystallizer.WissenCrystallizerBlockEntity;
import mod.maxbogomol.wizards_reborn.common.block.wissen_cell.WissenCellBlockEntity;
import mod.maxbogomol.wizards_reborn.common.block.alchemy_machine.AlchemyMachineBlockEntity;
import mod.maxbogomol.wizards_reborn.common.block.crystal.CrystalBlockEntity;
import mod.maxbogomol.wizards_reborn.client.gui.container.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Pinned Wizards Reborn 1.20.1-0.2.9, no reflection or calls to resource setters. */
final class WizardsBackend extends NativeMagicBackend {
    WizardsBackend(){super("wizards_reborn","1.20.1-0.2.9","38f040319fe4dc16400b2507e3cb627a321cddba314de0d4acc4f2dcd8ffbfe7","mod.maxbogomol.wizards_reborn.common.recipe.");}
    protected List<String> knownRecipeTypes(){return List.of("wizards_reborn:alchemy_machine","wizards_reborn:arcane_iterator","wizards_reborn:arcane_workbench","wizards_reborn:arcanum_dust_transmutation","wizards_reborn:censer","wizards_reborn:crystal_infusion","wizards_reborn:crystal_ritual","wizards_reborn:jeweler_table","wizards_reborn:mortar","wizards_reborn:wissen_altar","wizards_reborn:wissen_crystallizer");}
    protected List<String> limitations(){return List.of(
        "Wissen, steam, light and fluid values refer to actual named mod resources, not an invented generic mana field.",
        "Only six inspected block entity types expose synchronized resource/progress fields; other mod blocks return unknown.",
        "Dynamic ritual effects, structure validity, recipe availability and server permissions are not inferred from client progress.",
        "Current menu slots are mapped from native objects; core container.move/sequence must explicitly reuse the already-open menu.",
        "Native right-click only; no wissenWandFunction, resource setters, ritual start/end, inventory injection or automatic crafting.");}
    protected JsonObject specialRecipe(Recipe<?> recipe){
        JsonObject o=obj("complete",true);
        if(recipe instanceof WissenAltarRecipe r){o.add("input",ingredient(r.getInput()));o.addProperty("wissen",r.getWissen());o.addProperty("wissen_role","generation_recipe_value");o.addProperty("output","resource_generation_not_static_item");}
        else if(recipe instanceof AlchemyMachineRecipe r){
            o.add("inputs",ingredients(r.getIngredients()));o.add("output",stack(r.getResultItem(net.minecraft.client.Minecraft.getInstance().level.registryAccess())));
            o.addProperty("wissen",r.getWissen());o.addProperty("steam",r.getSteam());
            JsonArray fluids=new JsonArray();for(var f:r.getFluidInputs())fluids.add(f.serialize());o.add("fluid_inputs",fluids);o.add("fluid_output",fluid(r.getFluidResult()));
            o.addProperty("alchemy_potion",r.getAlchemyPotion()==null?null:r.getAlchemyPotion().getId());o.addProperty("alchemy_potion_ingredient",r.getAlchemyPotionIngredient()==null?null:r.getAlchemyPotionIngredient().getId());
        }else if(recipe instanceof ArcaneIteratorRecipe r){
            o.add("inputs",ingredients(r.getIngredients()));o.add("output",stack(recipe.getResultItem(net.minecraft.client.Minecraft.getInstance().level.registryAccess())));
            o.addProperty("wissen",r.getWissen());o.addProperty("health",r.getHealth());o.addProperty("experience",r.getExperience());o.addProperty("save_nbt",r.getIsSaveNBT());
            o.addProperty("enchantment",r.getEnchantment()==null?null:Objects.toString(BuiltInRegistries.ENCHANTMENT.getKey(r.getEnchantment())));
            o.addProperty("arcane_enchantment",r.getArcaneEnchantment()==null?null:r.getArcaneEnchantment().getId());o.addProperty("crystal_ritual",r.getCrystalRitual()==null?null:r.getCrystalRitual().getId());
        }else if(recipe instanceof CenserRecipe r){
            o.add("input",ingredient(r.getInput()));JsonArray effects=new JsonArray();for(var effect:r.getEffects())effects.add(obj("effect_id",Objects.toString(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect())),"duration",effect.getDuration(),"amplifier",effect.getAmplifier(),"ambient",effect.isAmbient(),"visible",effect.isVisible()));o.add("effects",effects);
        }else if(recipe instanceof CrystalRitualRecipe r){o.add("inputs",ingredients(recipe.getIngredients()));o.addProperty("ritual",r.getRitual()==null?null:r.getRitual().getId());o.addProperty("output","ritual_effect_not_static_item");}
        else {
            if(recipe instanceof MortarRecipe r)o.add("input",ingredient(r.getInput()));
            else if(recipe instanceof ArcanumDustTransmutationRecipe r){o.add("input",ingredient(r.getInput()));o.addProperty("place_block",r.getPlaceBlock());o.add("display",stack(r.getDisplay()));}
            else o.add("inputs",ingredients(recipe.getIngredients()));
            o.add("output",stack(recipe.getResultItem(net.minecraft.client.Minecraft.getInstance().level.registryAccess())));
            if(recipe instanceof ArcaneWorkbenchRecipe r){o.addProperty("wissen",r.getWissen());o.addProperty("save_nbt_slot",r.getSaveNBT());}
            else if(recipe instanceof JewelerTableRecipe r){o.addProperty("wissen",r.getWissen());o.addProperty("save_nbt",r.getIsSaveNBT());}
            else if(recipe instanceof WissenCrystallizerRecipe r){o.addProperty("wissen",r.getWissen());o.addProperty("nbt_crystal",r.getIsNBTCrystal());o.addProperty("save_nbt",r.getIsSaveNBT());}
            else if(recipe instanceof CrystalInfusionRecipe r)o.addProperty("light",r.getLight());
            else if(!(recipe instanceof MortarRecipe)&&!(recipe instanceof ArcanumDustTransmutationRecipe)){o.addProperty("complete",false);o.addProperty("details","unknown_unmapped_recipe_class");}
        }
        o.addProperty("dynamic_output_nbt","unknown_without_actual_inputs");return o;
    }
    protected boolean supported(BlockEntity e){return e instanceof WissenAltarBlockEntity||e instanceof ArcaneWorkbenchBlockEntity||e instanceof WissenCrystallizerBlockEntity||e instanceof AlchemyMachineBlockEntity||e instanceof WissenCellBlockEntity||e instanceof CrystalBlockEntity;}
    protected JsonObject specialBlock(BlockEntity e){
        JsonObject o=unknown();
        if(e instanceof WissenAltarBlockEntity b){o.add("resources",obj("wissen",b.getWissen(),"max_wissen",b.getMaxWissen()));o.add("items",inventory((net.minecraft.world.Container)b));o.add("progress",obj("wissen_in_item",b.wissenInItem,"wissen_is_craft",b.wissenIsCraft));}
        else if(e instanceof ArcaneWorkbenchBlockEntity b){o.add("resources",obj("wissen",b.getWissen(),"max_wissen",b.getMaxWissen()));o.add("items",obj("input",inventory(b.itemHandler),"output",inventory(b.itemOutputHandler)));o.add("progress",obj("wissen_in_craft",b.wissenInCraft,"wissen_is_craft",b.wissenIsCraft,"start_craft",b.startCraft));}
        else if(e instanceof WissenCrystallizerBlockEntity b){o.add("resources",obj("wissen",b.getWissen(),"max_wissen",b.getMaxWissen()));o.add("items",inventory((net.minecraft.world.Container)b));o.add("progress",obj("wissen_in_craft",b.wissenInCraft,"wissen_is_craft",b.wissenIsCraft,"start_craft",b.startCraft));}
        else if(e instanceof WissenCellBlockEntity b){o.add("resources",obj("wissen",b.getWissen(),"max_wissen",b.getMaxWissen()));o.add("items",inventory((net.minecraft.world.Container)b));}
        else if(e instanceof AlchemyMachineBlockEntity b){
            JsonArray tanks=new JsonArray();int i=0;for(var holder:List.of(b.fluidHolder1,b.fluidHolder2,b.fluidHolder3)){var handler=holder.resolve().orElse(null);
                if(handler==null)tanks.add(obj("group",i++,"state","unknown_capability_unavailable"));
                else {for(int t=0;t<Math.min(16,handler.getTanks());t++)tanks.add(obj("group",i,"tank",t,"fluid",fluid(handler.getFluidInTank(t)),"capacity_mb",handler.getTankCapacity(t)));i++;}}
            o.add("resources",obj("fluid_tanks",tanks,"wissen_buffer","unknown_no_exposed_buffer","steam_buffer","unknown_no_exposed_buffer"));
            o.add("items",obj("input",inventory(b.itemHandler),"output",inventory(b.itemOutputHandler)));o.add("progress",obj("wissen_in_craft",b.wissenInCraft,"wissen_is_craft",b.wissenIsCraft,"steam_in_craft",b.steamInCraft,"steam_is_craft",b.steamIsCraft,"start_craft",b.startCraft));
        }else if(e instanceof CrystalBlockEntity b){o.add("resources",obj("light",b.getLight(),"max_light",b.getMaxLight()));o.add("items",inventory(b.getItemHandler()));o.add("progress",obj("cooldown",b.cooldown,"max_cooldown",b.maxCooldown,"start_ritual",b.startRitual,"tick_ritual",b.tickRitual));}
        o.addProperty("provenance","Inspected load/save fields and Fluffy Fur update-tag path. Current client copy can lag or locally animate; sync age and server acceptance unknown.");return o;
    }
    protected BlockEntity menuOwner(AbstractContainerMenu menu){
        if(menu instanceof ArcaneWorkbenchContainer m)return m.blockEntity;
        if(menu instanceof AlchemyMachineContainer m)return m.blockEntity;
        if(menu instanceof JewelerTableContainer m)return m.blockEntity;
        if(menu instanceof AlchemyFurnaceContainer m)return m.blockEntity;
        return null;
    }
}
