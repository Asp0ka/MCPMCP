package dev.mcpmcp.jei;

import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Stream;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.*;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.*;
import net.minecraft.resources.ResourceLocation;

/** Headless regression at the real query -> handle -> layout boundary, on each pinned JEI API. */
@SuppressWarnings({"rawtypes", "unchecked", "removal"})
final class FocusRegressionTest {
    interface Call { Object call(Method method, Object[] args); }
    static <T> T proxy(Class<T> type, Call call) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)-> {
            if(m.getDeclaringClass()==Object.class)return switch(m.getName()) {
                case "toString" -> type.getSimpleName()+" fixture";
                case "hashCode" -> System.identityHashCode(p);
                case "equals" -> p==a[0];
                default -> throw new AssertionError(m);
            };
            return call.call(m,a==null?new Object[0]:a);
        });
    }
    static Object invoke(Object target,String name,Class<?>[] types,Object... args)throws Exception {
        Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);
        try{return method.invoke(target,args);}catch(InvocationTargetException ex){
            if(ex.getCause() instanceof Exception e)throw e;
            if(ex.getCause() instanceof Error e)throw e;
            throw ex;
        }
    }
    static IFocusGroup group(List<IFocus<?>> focuses) {
        List<IFocus<?>> saved=List.copyOf(focuses);
        return proxy(IFocusGroup.class,(m,a)->switch(m.getName()) {
            case "isEmpty" -> saved.isEmpty();
            case "getAllFocuses" -> saved;
            case "getFocuses" -> saved.stream();
            default -> throw new AssertionError(m);
        });
    }
    static void run()throws Exception {
        Object backend=TargetBackend.create();
        RecipeType<String> recipeType=new RecipeType<>(new ResourceLocation("test:generic"),String.class);
        IIngredientType<String> ingredientType=proxy(IIngredientType.class,(m,a)->switch(m.getName()) {
            case "getUid" -> "test:variant";
            case "getIngredientClass" -> String.class;
            default -> throw new AssertionError(m);
        });
        IFocusFactory focusFactory=proxy(IFocusFactory.class,(m,a)->switch(m.getName()) {
            case "getEmptyFocusGroup" -> group(List.of());
            case "createFocusGroup" -> group(new ArrayList<>((Collection<IFocus<?>>)a[0]));
            case "createFocus" -> {
                ITypedIngredient<?> typed=proxy(ITypedIngredient.class,(tm,ta)->switch(tm.getName()) {
                    case "getType" -> a[1];
                    case "getIngredient" -> a[2];
                    default -> throw new AssertionError(tm);
                });
                yield proxy(IFocus.class,(fm,fa)->switch(fm.getName()) {
                    case "getRole" -> a[0];
                    case "getTypedValue" -> typed;
                    default -> throw new AssertionError(fm);
                });
            }
            default -> throw new AssertionError(m);
        });
        IJeiHelpers helpers=proxy(IJeiHelpers.class,(m,a)-> {
            if(m.getName().equals("getFocusFactory"))return focusFactory;
            throw new AssertionError(m);
        });
        IIngredientHelper<String> helper=proxy(IIngredientHelper.class,(m,a)->switch(m.getName()) {
            case "getUniqueId", "getDisplayName" -> a[0];
            case "getResourceLocation" -> new ResourceLocation("test:"+a[0]);
            case "getAmount" -> 1L;
            default -> throw new AssertionError(m);
        });
        IIngredientManager ingredients=proxy(IIngredientManager.class,(m,a)->switch(m.getName()) {
            case "getIngredientTypeForUid" -> Optional.of(ingredientType);
            case "getIngredientByUid" -> Optional.of(a[1]);
            case "getIngredientHelper" -> helper;
            default -> throw new AssertionError(m);
        });
        IRecipeCategory<String> category=proxy(IRecipeCategory.class,(m,a)->switch(m.getName()) {
            case "getRecipeType" -> recipeType;
            case "getRegistryName" -> new ResourceLocation("test:generic_recipe");
            case "getSourceRecipeIds" -> List.of(new ResourceLocation("test:generic_recipe"));
            default -> throw new AssertionError(m);
        });
        List<IFocus<?>>[] lookupFocus=new List[]{List.of()};
        List<IFocusGroup> layouts=new ArrayList<>();
        IRecipeLookup lookup=proxy(IRecipeLookup.class,(m,a)->switch(m.getName()) {
            case "limitFocus" -> { lookupFocus[0]=List.copyOf((Collection<IFocus<?>>)a[0]);yield null; }
            case "get" -> Stream.of("generic recipe","generic recipe");
            case "includeHidden" -> null;
            default -> throw new AssertionError(m);
        });
        IRecipeManager manager=proxy(IRecipeManager.class,(m,a)->switch(m.getName()) {
            case "getRecipeType" -> Optional.of(recipeType);
            case "getRecipeCategory" -> category;
            case "createRecipeLookup" -> lookup;
            case "createRecipeLayoutDrawable" -> {
                IFocusGroup focus=(IFocusGroup)a[2];layouts.add(focus);
                String variant=focus.isEmpty()?"wrong_variant":(String)focus.getAllFocuses().get(0).getTypedValue().getIngredient();
                ITypedIngredient<?> value=proxy(ITypedIngredient.class,(tm,ta)->switch(tm.getName()) {
                    case "getType" -> ingredientType;
                    case "getIngredient" -> variant;
                    default -> throw new AssertionError(tm);
                });
                IRecipeSlotView slot=proxy(IRecipeSlotView.class,(sm,sa)->switch(sm.getName()) {
                    case "getRole" -> RecipeIngredientRole.OUTPUT;
                    case "getAllIngredients", "getDisplayedIngredients" -> Stream.of(value);
                    case "getAllIngredientsList" -> List.of(value);
                    case "getDisplayedIngredient" -> Optional.of(value);
                    case "getSlotName" -> Optional.of("output");
                    default -> throw new AssertionError(sm);
                });
                IRecipeSlotsView slots=proxy(IRecipeSlotsView.class,(sm,sa)-> {
                    if(sm.getName().equals("getSlotViews"))return List.of(slot);
                    throw new AssertionError(sm);
                });
                yield Optional.of(proxy(IRecipeLayoutDrawable.class,(dm,da)-> {
                    if(dm.getName().equals("getRecipeSlotsView"))return slots;
                    throw new AssertionError(dm);
                }));
            }
            case "getRecipeIngredients" -> proxy((Class)m.getReturnType(),(sm,sa)-> {
                if(sm.getName().equals("getIngredients"))return List.of();
                throw new AssertionError(sm);
            });
            default -> throw new AssertionError(m);
        });
        List<IFocus<?>>[] shownFocus=new List[]{List.of()};
        IRecipesGui gui=proxy(IRecipesGui.class,(m,a)-> {
            if(!m.getName().equals("showRecipes"))throw new AssertionError(m);
            AdapterTest.check(a[0]==category&&a[1].equals(List.of("generic recipe")),"show keeps the handle's exact category and recipe");
            shownFocus[0]=List.copyOf((List<IFocus<?>>)a[2]);return null;
        });
        IJeiRuntime runtime=proxy(IJeiRuntime.class,(m,a)->switch(m.getName()) {
            case "getRecipeManager" -> manager;
            case "getJeiHelpers" -> helpers;
            case "getIngredientManager" -> ingredients;
            case "getRecipesGui" -> gui;
            default -> throw new AssertionError(m);
        });
        Field storeField=backend.getClass().getDeclaredField("queries");storeField.setAccessible(true);
        QueryStore store=(QueryStore)storeField.get(backend);
        for(RecipeIngredientRole role:List.of(RecipeIngredientRole.INPUT,RecipeIngredientRole.OUTPUT,RecipeIngredientRole.CATALYST)) {
            JsonObject args=JsonParser.parseString("{\"category\":\"test:generic\",\"ingredient_type\":\"test:variant\",\"ingredient_uid\":\"oak\",\"role\":\""+role.name().toLowerCase(Locale.ROOT)+"\",\"limit\":1}").getAsJsonObject();
            JsonObject page=(JsonObject)invoke(backend,"recipes",new Class[]{IJeiRuntime.class,JsonObject.class},runtime,args);
            JsonObject data=page.getAsJsonObject("data");
            AdapterTest.check(data.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("outputs").get(0).getAsJsonObject().get("uid").getAsString().equals("oak"),"recipe output summary uses focused layout");
            AdapterTest.check(data.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("displayed_outputs").get(0).getAsJsonObject().get("uid").getAsString().equals("oak"),"displayed output reports the focused variant separately");
            String handle=data.getAsJsonArray("entries").get(0).getAsJsonObject().get("handle").getAsString();
            AdapterTest.check(lookupFocus[0].size()==1&&lookupFocus[0].get(0).getRole()==role,"lookup receives requested typed focus and role");
            // Mutate the request and issue a different query before using the original handle.
            args.addProperty("ingredient_uid","birch");
            invoke(backend,"recipes",new Class[]{IJeiRuntime.class,JsonObject.class},runtime,args);
            QueryStore.RecipeRef ref=store.resolve(runtime,0,handle);
            IRecipeLayoutDrawable<?> layout=(IRecipeLayoutDrawable<?>)invoke(backend,"layout",new Class[]{IJeiRuntime.class,QueryStore.RecipeRef.class},runtime,ref);
            Object variant=layout.getRecipeSlotsView().getSlotViews().get(0).getAllIngredients().findFirst().orElseThrow().getIngredient();
            AdapterTest.check(variant.equals("oak"),"original handle retains oak variant after another query, not empty or latest focus");
            IFocusGroup saved=layouts.get(layouts.size()-1);
            AdapterTest.check(saved.getAllFocuses().get(0).getRole()==role,"handle retains original focus role");
            AdapterTest.check(saved==ref.focus(),"layout consumes the complete focus group held by the handle");
            invoke(backend,"show",new Class[]{IJeiRuntime.class,QueryStore.RecipeRef.class},runtime,ref);
            AdapterTest.check(shownFocus[0].size()==1&&shownFocus[0].get(0).getRole()==role&&shownFocus[0].get(0).getTypedValue().getIngredient().equals("oak"),"show receives original handle focus after another query");
            args.addProperty("ingredient_uid","oak");args.addProperty("cursor",data.get("next_cursor").getAsString());
            JsonObject next=(JsonObject)invoke(backend,"recipes",new Class[]{IJeiRuntime.class,JsonObject.class},runtime,args);
            String continued=next.getAsJsonObject("data").getAsJsonArray("entries").get(0).getAsJsonObject().get("handle").getAsString();
            IRecipeLayoutDrawable<?> continuedLayout=(IRecipeLayoutDrawable<?>)invoke(backend,"layout",new Class[]{IJeiRuntime.class,QueryStore.RecipeRef.class},runtime,store.resolve(runtime,0,continued));
            AdapterTest.check(continuedLayout.getRecipeSlotsView().getSlotViews().get(0).getAllIngredients().findFirst().orElseThrow().getIngredient().equals("oak"),"continued query handles retain the query focus");
        }
        JsonObject unfiltered=JsonParser.parseString("{\"category\":\"test:generic\",\"limit\":1}").getAsJsonObject();
        JsonObject all=(JsonObject)invoke(backend,"recipes",new Class[]{IJeiRuntime.class,JsonObject.class},runtime,unfiltered);
        String allHandle=all.getAsJsonObject("data").getAsJsonArray("entries").get(0).getAsJsonObject().get("handle").getAsString();
        AdapterTest.check(((IFocusGroup)store.resolve(runtime,0,allHandle).focus()).isEmpty(),"omitting focus deliberately preserves an empty group");
        store.clear();
    }
}
