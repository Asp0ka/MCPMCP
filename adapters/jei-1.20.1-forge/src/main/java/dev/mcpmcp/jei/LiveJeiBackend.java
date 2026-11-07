package dev.mcpmcp.jei;

import com.google.gson.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.*;
import mezz.jei.api.gui.ingredient.*;
import mezz.jei.api.ingredients.*;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.*;
import mezz.jei.api.runtime.*;
import mezz.jei.common.Internal;
import mezz.jei.common.transfer.RecipeTransferLifecycleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import java.util.stream.Stream;
import static dev.mcpmcp.core.Jsons.*;

/** Exact-JAR bridge. Internal runtime/lifecycle access is deliberately isolated here. */
@SuppressWarnings({"unchecked","rawtypes","deprecation"})
final class LiveJeiBackend implements JeiProvider.Backend {
    private final QueryStore queries=new QueryStore();
    private final LinkedHashMap<String,Transfer> transfers=new LinkedHashMap<>();
    private Object owner,world;
    private long epoch;
    private RecipeTransferLifecycleManager lifecycle;

    private IJeiRuntime runtime() {
        IJeiRuntime r=Internal.getOptionalJeiRuntime().orElse(null);
        Object level=Minecraft.getInstance().level;
        if(owner!=r||world!=level){queries.clear();transfers.clear();owner=r;world=level;epoch++;lifecycle=r==null?null:new RecipeTransferLifecycleManager(r.getRecipeTransferManager().getRecipeTransferListeners());}
        return r;
    }
    public JsonObject read(String kind,JsonObject args) {
        try{
            IJeiRuntime r=runtime();
            if(kind.equals("jei"))return status(r);
            if(r==null)return error("jei_runtime_unavailable");
            return switch(kind){
                case "jei.categories" -> categories(r,args);
                case "jei.ingredients" -> ingredients(r,args);
                case "jei.recipes" -> recipes(r,args);
                case "jei.recipe" -> detail(r,queries.resolve(r,epoch,str(args,"handle","")));
                case "jei.transfer" -> transferStatus(r,args);
                default -> error("unknown_jei_observer");
            };
        }catch(Exception|LinkageError ex){return failure(ex);}
    }
    public JsonObject act(String kind,JsonObject args) {
        try{
            IJeiRuntime r=runtime();if(r==null)return error("jei_runtime_unavailable");
            QueryStore.RecipeRef ref=queries.resolve(r,epoch,str(args,"handle",""));
            Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return error("not_in_world");
            if(kind.equals("jei.show")){
                if(mc.screen!=null&&!(mc.screen instanceof AbstractContainerScreen))return error("close_existing_screen_first");
                show(r,ref);
                return obj("delivery","submitted","source","jei","effect","recipe_gui_requested");
            }
            return transfer(r,ref,args);
        }catch(Exception|LinkageError ex){return failure(ex);}
    }
    private JsonObject status(IJeiRuntime r){
        JsonObject contracts=new JsonObject();for(String kind:List.of("jei","jei.categories","jei.ingredients","jei.recipes","jei.recipe","jei.transfer","jei.transfer.action","jei.show.action"))contracts.add(kind,JeiProvider.schema(kind));
        return obj("data",obj("source","jei","adapter_version","0.1.0","target_version","15.74.0.234","available",r!=null,"runtime_epoch",epoch,
            "contracts",contracts,"actions",List.of("jei.transfer","jei.show"),"core_recipes_replaced",false,
            "runtime_access","pinned internal getOptionalJeiRuntime; remaining queries use JEI public APIs",
            "examples",List.of("observe(kind=jei.categories, arguments={})","observe(kind=jei.recipes, arguments={category:minecraft:crafting,item_id:minecraft:oak_planks,role:output})","execute_action(action=jei.transfer, arguments={handle:<fresh_handle>,dry_run:true})"),
            "transfer_contract","already-open compatible container only; dry_run defaults true; submitted != filled != crafted; query jei.transfer token and inventory"));
    }
    private JsonObject categories(IJeiRuntime r,JsonObject a){
        return page(r,"jei.categories",a,()->{
            IRecipeCategoriesLookup lookup=r.getRecipeManager().createRecipeCategoryLookup();if(bool(a,"include_hidden",false))lookup.includeHidden();return lookup.get();
        },v->{IRecipeCategory<?> c=(IRecipeCategory<?>)v;JsonObject row=obj("category",c.getRecipeType().getUid().toString(),"title",clip(c.getTitle().getString(),512),"recipe_class",c.getRecipeType().getRecipeClass().getName());return matches(a,row)?row:null;});
    }
    private JsonObject ingredients(IJeiRuntime r,JsonObject a){
        String type=str(a,"ingredient_type","");
        return page(r,"jei.ingredients",a,()->{
            Stream<IIngredientType<?>> types=r.getIngredientManager().getRegisteredIngredientTypes().stream();
            if(!type.isEmpty())types=types.filter(t->t.getUid().equals(type));
            return types.flatMap(t->((Collection<?>)r.getIngredientManager().getAllIngredients((IIngredientType)t)).stream().map(v->new Typed(t,v)));
        },v->{Typed value=(Typed)v;JsonObject row=ingredient(r,value.type,value.value);return matches(a,row)?row:null;});
    }
    private record Typed(IIngredientType type,Object value){}
    private List<IFocus<?>> focus(IJeiRuntime r,JsonObject a){
        JeiProvider.validateFocus(a);
        String item=str(a,"item_id",""),type=str(a,"ingredient_type",""),uid=str(a,"ingredient_uid","");
        if(!item.isEmpty()&&(!type.isEmpty()||!uid.isEmpty()))throw new IllegalArgumentException("jei_focus_mutually_exclusive");
        if(type.isEmpty()!=uid.isEmpty())throw new IllegalArgumentException("jei_focus_requires_type_and_uid");
        RecipeIngredientRole role=switch(str(a,"role","output")){case "input"->RecipeIngredientRole.INPUT;case "output"->RecipeIngredientRole.OUTPUT;case "catalyst"->RecipeIngredientRole.CATALYST;default->throw new IllegalArgumentException("invalid_jei_role");};
        if(!item.isEmpty()){
            ResourceLocation id=new ResourceLocation(item);if(!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("unknown_item_id");
            ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(id));if(stack.isEmpty())throw new IllegalArgumentException("empty_item_focus");
            return List.of(r.getJeiHelpers().getFocusFactory().createFocus(role,VanillaTypes.ITEM_STACK,stack));
        }
        if(!type.isEmpty()){
            IIngredientType t=r.getIngredientManager().getIngredientTypeForUid(type).orElseThrow(()->new IllegalArgumentException("unknown_jei_ingredient_type"));
            Optional<?> found=r.getIngredientManager().getIngredientByUid(t,uid);
            if(found.isEmpty())throw new IllegalArgumentException("unknown_jei_ingredient_uid");Object value=found.get();
            return List.of(r.getJeiHelpers().getFocusFactory().createFocus(role,t,value));
        }
        return List.of();
    }
    private JsonObject recipes(IJeiRuntime r,JsonObject a){
        RecipeType<?> type=r.getRecipeManager().getRecipeType(new ResourceLocation(str(a,"category",""))).orElseThrow(()->new IllegalArgumentException("unknown_jei_category"));
        IRecipeCategory category=r.getRecipeManager().getRecipeCategory(type);
        IFocusGroup focus=r.getJeiHelpers().getFocusFactory().createFocusGroup(focus(r,a));
        return page(r,"jei.recipes",a,()->{
            IRecipeLookup lookup=r.getRecipeManager().createRecipeLookup(type);if(bool(a,"include_hidden",false))lookup.includeHidden();if(!focus.isEmpty())lookup.limitFocus(focus.getAllFocuses());return lookup.get();
        },recipe->{JsonObject row=summary(r,category,recipe,focus);if(!matches(a,row))return null;row.addProperty("handle",queries.remember(r,epoch,category,recipe,focus));return row;});
    }
    private JsonObject summary(IJeiRuntime r,IRecipeCategory c,Object recipe,IFocusGroup focus){
        JsonObject out=obj("category",c.getRecipeType().getUid().toString(),"recipe_class",recipe.getClass().getName());
        ResourceLocation id=c.getRegistryName(recipe);if(id==null&&recipe instanceof Recipe<?> vanilla)id=vanilla.getId();out.add("recipe_id",id==null?JsonNull.INSTANCE:new JsonPrimitive(id.toString()));
        List<ResourceLocation> source=c.getSourceRecipeIds(recipe);out.add("source_recipe_ids",toJson(source.stream().limit(64).map(Object::toString).toList()));out.addProperty("source_ids_truncated",source.size()>64);
        Optional<?> drawable=r.getRecipeManager().createRecipeLayoutDrawable(c,recipe,focus);
        List<ITypedIngredient<?>> outputs=new ArrayList<>();if(drawable.isPresent())for(IRecipeSlotView slot:((IRecipeLayoutDrawable)drawable.get()).getRecipeSlotsView().getSlotViews())if(slot.getRole()==RecipeIngredientRole.OUTPUT)outputs.addAll(slot.getAllIngredientsList().stream().limit(17).toList());JsonArray list=new JsonArray();for(ITypedIngredient<?> t:outputs.stream().limit(16).toList())list.add(ingredient(r,t.getType(),t.getIngredient()));out.add("outputs",list);out.addProperty("outputs_truncated",outputs.size()>16);out.addProperty("outputs_semantics","all_recipe_alternatives; not only the current focused variant");JsonArray displayed=new JsonArray();if(drawable.isPresent())for(IRecipeSlotView slot:((IRecipeLayoutDrawable)drawable.get()).getRecipeSlotsView().getSlotViews())if(slot.getRole()==RecipeIngredientRole.OUTPUT&&displayed.size()<16)slot.getDisplayedIngredient().ifPresent(t->displayed.add(ingredient(r,t.getType(),t.getIngredient())));out.add("displayed_outputs",displayed);return out;
    }
    private void show(IJeiRuntime r,QueryStore.RecipeRef ref){r.getRecipesGui().showRecipes((IRecipeCategory)ref.category(),List.of(ref.recipe()),((IFocusGroup)ref.focus()).getAllFocuses());}
    private IRecipeLayoutDrawable layout(IJeiRuntime r,QueryStore.RecipeRef ref){Optional<?> found=r.getRecipeManager().createRecipeLayoutDrawable((IRecipeCategory)ref.category(),ref.recipe(),(IFocusGroup)ref.focus());if(found.isEmpty())throw new IllegalArgumentException("jei_layout_unavailable");return (IRecipeLayoutDrawable)found.get();}
    private JsonObject detail(IJeiRuntime r,QueryStore.RecipeRef ref){
        IRecipeCategory c=(IRecipeCategory)ref.category();JsonObject out=summary(r,c,ref.recipe(),(IFocusGroup)ref.focus());JsonArray slots=new JsonArray();
        List<IRecipeSlotView> views=layout(r,ref).getRecipeSlotsView().getSlotViews();
        for(IRecipeSlotView slot:views.stream().limit(128).toList()){
            JsonArray values=new JsonArray();List<ITypedIngredient<?>> all=slot.getAllIngredientsList();
            for(ITypedIngredient<?> t:all.stream().limit(32).toList())values.add(ingredient(r,t.getType(),t.getIngredient()));
            JsonObject row=obj("role",slot.getRole().name().toLowerCase(Locale.ROOT),"name",slot.getSlotName().orElse(null),"alternatives",values,"alternatives_truncated",all.size()>32);slot.getDisplayedIngredient().ifPresent(t->row.add("displayed",ingredient(r,t.getType(),t.getIngredient())));slots.add(row);
        }
        JsonArray catalysts=new JsonArray();try(Stream<ITypedIngredient<?>> stream=r.getRecipeManager().createRecipeCatalystLookup(c.getRecipeType()).get()){stream.limit(33).forEach(v->catalysts.add(ingredient(r,v.getType(),v.getIngredient())));}
        boolean ct=catalysts.size()>32;if(ct)catalysts.remove(32);out.add("catalysts",catalysts);out.addProperty("catalysts_truncated",ct);out.add("slots",slots);out.addProperty("slots_truncated",views.size()>128);
        Minecraft mc=Minecraft.getInstance();out.addProperty("current_menu_transfer_handler",mc.player!=null&&r.getRecipeTransferManager().getRecipeTransferHandler(mc.player.containerMenu,c).isPresent());
        return obj("data",out,"source","jei");
    }
    private JsonObject ingredient(IJeiRuntime r,IIngredientType type,Object value){
        try{
            IIngredientHelper helper=r.getIngredientManager().getIngredientHelper(type);JsonObject out=obj("ingredient_type",type.getUid(),"uid",clip(helper.getUniqueId(value,UidContext.Ingredient),4096),"name",clip(helper.getDisplayName(value),512),"resource_id",helper.getResourceLocation(value).toString(),"amount",helper.getAmount(value));
            if(value instanceof ItemStack s){out.addProperty("item_id",BuiltInRegistries.ITEM.getKey(s.getItem()).toString());out.addProperty("count",s.getCount());if(s.hasTag())out.addProperty("nbt",clip(s.getTag().toString(),4096));}
            if(value instanceof FluidStack s){out.addProperty("fluid_id",BuiltInRegistries.FLUID.getKey(s.getFluid()).toString());out.addProperty("amount_mb",s.getAmount());if(s.hasTag())out.addProperty("nbt",clip(s.getTag().toString(),4096));}
            return out;
        }catch(Exception|LinkageError ex){return obj("ingredient_type",type.getUid(),"error","jei_ingredient_unreadable","reason",clip(ex.toString(),512));}
    }
    private JsonObject page(IJeiRuntime r,String kind,JsonObject a,java.util.function.Supplier<? extends Stream<?>> factory,java.util.function.Function<Object,JsonObject> mapper){
        JsonObject query=a.deepCopy();for(String key:List.of("cursor","limit","scan_budget"))query.remove(key);
        QueryStore.Page result=queries.page(r,epoch,kind,query,str(a,"cursor",""),integer(a,"limit",32),integer(a,"scan_budget",512),factory,mapper);
        return obj("data",obj("entries",result.entries(),"complete",result.complete(),"next_cursor",result.next(),"scanned",result.scanned(),"source","jei","runtime_epoch",epoch));
    }
    private JsonObject transfer(IJeiRuntime r,QueryStore.RecipeRef ref,JsonObject a){
        Minecraft mc=Minecraft.getInstance();if(!(mc.screen instanceof AbstractContainerScreen<?> screen))return error("open_compatible_container_first");
        AbstractContainerMenu menu=mc.player.containerMenu;if(menu!=screen.getMenu())return error("jei_menu_screen_mismatch");
        if(!menu.getCarried().isEmpty())return error("cursor_not_empty");
        if(menu.slots.size()>512)return error("jei_menu_slot_limit");
        IRecipeCategory category=(IRecipeCategory)ref.category();
        Optional<IRecipeTransferHandler<AbstractContainerMenu,Object>> handler=r.getRecipeTransferManager().getRecipeTransferHandler(menu,category);
        if(handler.isEmpty())return error("jei_transfer_handler_unavailable");
        expireTransfers();for(Transfer active:transfers.values())if(active.callback==null&&System.currentTimeMillis()-active.created<10000&&active.menu==menu)return error("jei_transfer_pending");
        Transfer t=new Transfer(r,epoch,menu,screen,mc.player,ref,layout(r,ref).getRecipeSlotsView(),bool(a,"max_transfer",false),lifecycle.getNextTransferId());
        IRecipeTransferError check=handler.get().transferRecipe(t,false);if(check!=null&&!check.getType().allowsTransfer)return transferError(check,"preflight");
        if(bool(a,"dry_run",true))return obj("observed_success",true,"source","jei","dry_run",true,"can_transfer",true,"menu_id",menu.containerId,"max_transfer",t.max,"note","No transfer submitted. Preflight is JEI handler validation, not proof of future inventory state.");
        while(transfers.size()>=64)transfers.remove(transfers.keySet().iterator().next());String token=UUID.randomUUID().toString();transfers.put(token,t);
        lifecycle.beforeRecipeTransfer(t);
        IRecipeTransferError result;
        try{result=handler.get().transferRecipe(t,true);}catch(Exception|LinkageError ex){t.exception=clip(ex.toString(),512);return obj("error","jei_transfer_delivery_unknown","transfer_token",token,"reason",t.exception,"retry","Read jei.transfer with the same token before any retry.");}
        if(result!=null&&!result.getType().allowsTransfer){t.callback=RecipeTransferResult.REJECTED;JsonObject err=transferError(result,"submit");err.addProperty("transfer_token",token);return err;}
        return obj("delivery","submitted","source","jei","transfer_token",token,"menu_id",menu.containerId,"max_transfer",t.max,"next","observe(kind=jei.transfer, arguments={transfer_token:<token>}); then read container/inventory. No automatic result pickup.");
    }
    private JsonObject transferError(IRecipeTransferError e,String phase){return obj("error","jei_transfer_rejected","phase",phase,"jei_error_type",e.getType().name(),"missing_count_hint",e.getMissingCountHint(),"tooltip",e.getTooltip().stream().limit(16).map(c->clip(c.getString(),512)).toList());}
    private JsonObject transferStatus(IJeiRuntime r,JsonObject a){
        expireTransfers();Transfer t=transfers.get(str(a,"transfer_token",""));if(t==null||t.runtime!=r||t.epoch!=epoch)return error("stale_jei_transfer_token");
        Minecraft mc=Minecraft.getInstance();boolean same=mc.player==t.player&&mc.player.containerMenu==t.menu;
        boolean changed=same&&!t.before.equals(snapshot(t.menu));long elapsed=System.currentTimeMillis()-t.created;
        String state=t.callback==RecipeTransferResult.REJECTED?"rejected":t.callback==RecipeTransferResult.SUCCESS?"server_confirmed":!same?"menu_changed":elapsed>=10000?"confirmation_unknown":"awaiting_confirmation";
        return obj("data",obj("source","jei","state",state,"callback",t.callback==null?null:t.callback.name(),"same_menu",same,"menu_items_changed",changed,"elapsed_ms",elapsed,"crafted_output_verified",false,"delivery_exception",t.exception,"note","Menu changes alone may be unrelated; read recipe input slots and inventory. Callback confirms JEI transfer, not crafting."));
    }
    private void expireTransfers(){long now=System.currentTimeMillis();transfers.entrySet().removeIf(e->now-e.getValue().created>120000);}
    private static List<String> snapshot(AbstractContainerMenu menu){List<String> out=new ArrayList<>();for(var slot:menu.slots){ItemStack s=slot.getItem();out.add(s.isEmpty()?"":BuiltInRegistries.ITEM.getKey(s.getItem())+"/"+s.getCount()+"/"+(s.hasTag()?s.getTag():""));}return out;}
    private final class Transfer implements IRecipeTransferContext<Object,AbstractContainerMenu>{
        final IJeiRuntime runtime;final long epoch;final AbstractContainerMenu menu;final AbstractContainerScreen screen;final Player player;final QueryStore.RecipeRef ref;final IRecipeSlotsView slots;final boolean max;final int id;final long created=System.currentTimeMillis();final List<String> before;RecipeTransferResult callback;String exception;
        Transfer(IJeiRuntime runtime,long epoch,AbstractContainerMenu menu,AbstractContainerScreen screen,Player player,QueryStore.RecipeRef ref,IRecipeSlotsView slots,boolean max,int id){this.runtime=runtime;this.epoch=epoch;this.menu=menu;this.screen=screen;this.player=player;this.ref=ref;this.slots=slots;this.max=max;this.id=id;before=snapshot(menu);}
        public int getTransferId(){return id;}
        public void completeRecipeTransfer(RecipeTransferResult result){if(callback!=null)return;callback=result;try{if(lifecycle!=null)lifecycle.completeRecipeTransfer(this,result);}catch(Exception|LinkageError ex){exception=clip(ex.toString(),512);}}
        public Object getRecipe(){return ref.recipe();}
        public RecipeType<Object> getRecipeType(){return ((IRecipeCategory<Object>)ref.category()).getRecipeType();}
        public AbstractContainerMenu getContainer(){return menu;}
        public AbstractContainerScreen<AbstractContainerMenu> getScreen(){return screen;}
        public IRecipeSlotsView getRecipeSlots(){return slots;}
        public Player getPlayer(){return player;}
        public boolean isMaxTransfer(){return max;}
    }
    private static boolean matches(JsonObject a,JsonObject row){String q=str(a,"query","").toLowerCase(Locale.ROOT);return q.isEmpty()||row.toString().toLowerCase(Locale.ROOT).contains(q);}
    private static String clip(String s,int n){return s==null?"":s.length()>n?s.substring(0,n):s;}
    private static JsonObject failure(Throwable ex){return obj("error",ex instanceof IllegalArgumentException?"invalid_jei_request":"jei_adapter_failed","reason",clip(ex.getClass().getSimpleName()+": "+Objects.toString(ex.getMessage(),""),1024));}
    private static JsonElement toJson(Object value){return new Gson().toJsonTree(value);}
}
