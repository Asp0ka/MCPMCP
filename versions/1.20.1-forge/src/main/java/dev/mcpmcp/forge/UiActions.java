package dev.mcpmcp.forge;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import static dev.mcpmcp.core.Jsons.*;

/** Screen-native input works on the title screen and paused worlds; no private screen methods are invoked. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="mcpmcp",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class UiActions {
    private static final UiInputs INPUTS=new UiInputs();
    /** Client-thread only. Releases only inputs acquired by UI automation. */
    public static void releaseInputs(){INPUTS.releaseAll();}
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void screenOpening(net.minecraftforge.client.event.ScreenEvent.Opening event){
        if(event.getCurrentScreen()!=event.getNewScreen())releaseInputs();
    }
    private record Node(GuiEventListener listener,List<ContainerEventHandler> parents,String path) {}
    private record Handle(Screen screen,Node node,long readAt) {}
    private static final LinkedHashMap<String,Handle> HANDLES=new LinkedHashMap<>();
    private static Screen lastScreen;
    private static long revision;
    private static void refresh(Screen screen) {
        if(lastScreen!=screen){releaseInputs();lastScreen=screen;revision++;HANDLES.clear();}
        long now=System.currentTimeMillis();HANDLES.entrySet().removeIf(e->now-e.getValue().readAt>30000);
    }
    /** Lazy bounded depth-first traversal: never recursively copies a screen's full control tree. */
    private static Iterator<Node> nodes(Screen screen) {
        record Frame(ContainerEventHandler parent,List<ContainerEventHandler> ancestors,Iterator<? extends GuiEventListener> children,String prefix,int[] index) {}
        return new Iterator<>() {
            final ArrayDeque<Frame> stack=new ArrayDeque<>();
            {stack.push(new Frame(screen,List.of(screen),screen.children().iterator(),"",new int[]{0}));}
            public boolean hasNext(){while(!stack.isEmpty()&&!stack.peek().children.hasNext())stack.pop();return !stack.isEmpty();}
            public Node next(){if(!hasNext())throw new NoSuchElementException();Frame f=stack.peek();GuiEventListener l=f.children.next();
                String path=f.prefix+(f.prefix.isEmpty()?"":".")+f.index[0]++;Node n=new Node(l,f.ancestors,path);
                if(l instanceof ContainerEventHandler c&&f.ancestors.size()<16&&!f.ancestors.contains(c)){
                    ArrayList<ContainerEventHandler> parents=new ArrayList<>(f.ancestors);parents.add(c);stack.push(new Frame(c,List.copyOf(parents),c.children().iterator(),path,new int[]{0}));}
                return n;}
        };
    }
    public static JsonObject read(JsonObject args) {
        Minecraft mc=Minecraft.getInstance();Screen screen=mc.screen;refresh(screen);
        if(screen==null)return ObservJson.envelope(obj("open",false,"screen_revision",revision,"width",mc.getWindow().getGuiScaledWidth(),"height",mc.getWindow().getGuiScaledHeight()),"client_screen","current_screen",true,false);
        try {
            ObservPages.Page<Node> page=ObservPages.take("screen",screen,args,Observations.limit(args,128),()->nodes(screen));JsonArray controls=new JsonArray();
            for(Node n:page.candidates()) {
                String id=revision+":"+n.path;HANDLES.put(id,new Handle(screen,n,System.currentTimeMillis()));
                while(HANDLES.size()>4096)HANDLES.remove(HANDLES.keySet().iterator().next());
                var r=n.listener.getRectangle();JsonObject c=obj("id",id,"type",n.listener.getClass().getName(),"focused",n.listener.isFocused(),
                    "bounds",obj("x",r.left(),"y",r.top(),"width",r.width(),"height",r.height()));
                if(n.listener instanceof AbstractWidget w){c.addProperty("label",ObservJson.clipped(w.getMessage().getString(),256));
                    c.addProperty("active",w.active);c.addProperty("visible",w.visible);String key=translation(w.getMessage());if(key!=null)c.addProperty("translation_key",key);
                    if(w instanceof EditBox e){c.addProperty("editable",e.canConsumeInput());c.addProperty("text_length",e.getValue().length());c.addProperty("text_requires_focus",true);}
                    if(w instanceof MultiLineEditBox e)c.addProperty("text_length",e.getValue().length());
                }else {c.addProperty("visibility","unknown_generic_listener");c.addProperty("active","unknown_generic_listener");}
                controls.add(c);
            }
            return ObservJson.envelope(obj("open",true,"screen",screen.getClass().getName(),"title",ObservJson.clipped(screen.getTitle().getString(),256),"screen_revision",revision,
                "width",screen.width,"height",screen.height,"coordinate_space","gui_scaled_pixels","controls",controls,"next_cursor",page.next(),"examined_total",page.examined(),
                "control_scope","public_event_children_depth_16; custom_render_only_elements_may_need_screenshot","text_values","not_exposed"),"client_screen_public_controls","current_screen_page",!page.more(),page.more());
        }catch(IllegalArgumentException e){return obj("error","invalid_arguments","reason",e.getMessage());}
    }
    public static JsonObject execute(String action,JsonObject args) {
        Minecraft mc=Minecraft.getInstance();refresh(mc.screen);
        try {
            if(action.startsWith("ui."))UiSafety.authorize(Runtime.config().mode,mc.screen instanceof McpmcpSettingsScreen,false,false);
            JsonObject result=switch(action) {
                case "screenshot"->screenshot(mc,args);
                case "world.create"->create(mc,args);
                case "world.join"->join(mc,args);
                case "world.leave"->leave(mc);
                case "world.open_lan"->lan(mc,args);
                case "ui.click","ui.mouse"->mouse(mc,args,action.equals("ui.click"));
                case "ui.key"->key(mc,args);
                case "ui.scroll"->scroll(mc,args);
                case "ui.focus"->focus(mc,args);
                case "ui.text"->text(mc,args);
                default->obj("error","unknown_ui_action","reason",action);
            };
            if("observed_success".equals(str(result,"status","")))result.addProperty("observed_success",true);
            return result;
        }catch(IllegalArgumentException e){releaseInputs();return obj("error","invalid_arguments","reason",e.getMessage());}
        catch(IOException e){releaseInputs();return obj("error","image_encoding_failed","reason",e.getClass().getSimpleName());}
        catch(RuntimeException e){releaseInputs();throw e;}
        finally{refresh(mc.screen);}
    }
    private static Screen screen(Minecraft mc){if(mc.screen==null)throw new IllegalArgumentException("no_screen_open");return mc.screen;}
    private static Node target(Screen screen,String id) {
        Handle h=HANDLES.get(id);if(h==null||h.screen!=screen)throw new IllegalArgumentException("stale_control_read_screen_again");
        // Validate the exact parent/child chain by path; do not trust IDs after dynamic tab/control replacement.
        String[] parts=h.node.path.split("[.]");ContainerEventHandler parent=screen;GuiEventListener current=null;
        for(int i=0;i<parts.length;i++){
            int at=Integer.parseInt(parts[i]);var children=parent.children();if(at<0||at>=children.size())throw new IllegalArgumentException("stale_control_read_screen_again");
            current=children.get(at);if(i<parts.length-1){if(!(current instanceof ContainerEventHandler c))throw new IllegalArgumentException("stale_control_read_screen_again");parent=c;}
        }
        if(current!=h.node.listener)throw new IllegalArgumentException("stale_control_read_screen_again");return h.node;
    }
    private static void active(Node n) {
        if(n.listener instanceof AbstractWidget w&&(!w.active||!w.visible))throw new IllegalArgumentException("control_inactive_or_hidden");
    }
    private static void authorizeActivation(Screen screen,GuiEventListener control){
        boolean cheats=screen instanceof CreateWorldScreen c&&c.getUiState().isAllowCheats();
        // LAN's commands flag has no public getter; publishing via this screen is conservatively privileged.
        // world.open_lan {cheats:false} remains available in NORMAL/HACK.
        cheats|=screen instanceof ShareToLanScreen;
        if(screen instanceof CreateWorldScreen&&control instanceof AbstractWidget w)
            cheats|=containsTranslation(w.getMessage(),"selectWorld.allowCommands")||containsTranslation(w.getMessage(),"selectWorld.gameMode");
        UiSafety.authorize(Runtime.config().mode,screen instanceof McpmcpSettingsScreen,false,cheats);
    }
    private static boolean containsTranslation(Component c,String key){
        if(c.getContents() instanceof TranslatableContents t){
            if(t.getKey().equals(key))return true;
            for(Object arg:t.getArgs())if(arg instanceof Component child&&containsTranslation(child,key))return true;
        }
        for(Component sibling:c.getSiblings())if(containsTranslation(sibling,key))return true;
        return false;
    }
    private static boolean keyDown(Minecraft mc,int code){
        if(com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(),code))return true;
        for(var mapping:mc.options.keyMappings)if(mapping.matches(code,0)&&mapping.isDown())return true;
        return false;
    }
    private static JsonObject mouse(Minecraft mc,JsonObject args,boolean click) {
        Screen s=screen(mc);String id=str(args,"id","");double x,y;
        if(!id.isEmpty()) {Node n=target(s,id);active(n);var r=n.listener.getRectangle();x=r.left()+r.width()/2d;y=r.top()+r.height()/2d;}
        else {x=number(args,"x",-1);y=number(args,"y",-1);}
        x=UiArguments.coordinate(x,s.width);y=UiArguments.coordinate(y,s.height);int button=integer(args,"button",0);if(button<0||button>7)throw new IllegalArgumentException("invalid_mouse_button");
        String mode=click?"click":str(args,"action","click");boolean handled;
        if(!mode.equals("move")&&!mode.equals("release")){
            GuiEventListener control=null;for(var child:s.children())if(child.isMouseOver(x,y)){control=child;break;}
            authorizeActivation(s,control);
            if(s instanceof ChatScreen&&mc.gui!=null){
                var style=mc.gui.getChat().getClickedComponentStyleAt(x,y);
                UiSafety.authorize(Runtime.config().mode,false,style!=null&&style.getClickEvent()!=null&&style.getClickEvent().getAction()==net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,false);
            }
        }
        final double px=x,py=y;String input="mouse:"+button;
        boolean playerDown=org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),button)==1;
        switch(mode) {
            case "move"->{s.mouseMoved(x,y);handled=true;}
            case "press"->{handled=INPUTS.acquire(input,playerDown,()->{if(org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),button)!=1)s.mouseReleased(px,py,button);});if(handled)handled=s.mouseClicked(x,y,button);}
            case "release"->{INPUTS.release(input);handled=true;}
            case "click","tap"->{handled=INPUTS.acquire(input,playerDown,()->{if(org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),button)!=1)s.mouseReleased(px,py,button);});if(handled)try{handled=s.mouseClicked(x,y,button);}finally{INPUTS.release(input);}}
            default->throw new IllegalArgumentException("mouse_action_must_be_click_move_press_release");
        }
        s.afterMouseAction();return obj("status","submitted","handled",handled,"x",x,"y",y,"button",button,"screen_changed",mc.screen!=s,"verify","observe screen or resulting world state");
    }
    private static JsonObject key(Minecraft mc,JsonObject args) {
        int code;
        if(args.has("key")&&args.get("key").isJsonPrimitive()&&args.getAsJsonPrimitive("key").isString())code=UiArguments.key(args.get("key").getAsString());
        else code=integer(args,"key",-1);
        if(code<32||code>348)throw new IllegalArgumentException("invalid_glfw_key");
        int modifiers=integer(args,"modifiers",0);if(modifiers<0||modifiers>63)throw new IllegalArgumentException("invalid_modifiers");
        String action=str(args,"action","tap");long window=mc.getWindow().getWindow();
        if(!action.equals("release")&&mc.screen!=null){
            authorizeActivation(mc.screen,focused(mc.screen));
            if(mc.screen instanceof ChatScreen chat&&(code==257||code==335)){
                String value="";for(var child:chat.children())if(child instanceof EditBox box){value=chat.normalizeChatMessage(box.getValue());break;}
                UiSafety.authorizeKey(Runtime.config().mode,true,code,false,value);
            }
        }
        String input="key:"+code;
        final int key=code;
        Runnable release=()->{if(!com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,key))mc.keyboardHandler.keyPress(window,key,0,0,modifiers);};
        switch(action){case "press"->{if(INPUTS.acquire(input,keyDown(mc,key),release))mc.keyboardHandler.keyPress(window,key,0,1,modifiers);}
            case "release"->INPUTS.release(input);
            case "tap","click"->{if(INPUTS.acquire(input,keyDown(mc,key),release))try{mc.keyboardHandler.keyPress(window,key,0,1,modifiers);}finally{INPUTS.release(input);}}
            default->throw new IllegalArgumentException("key_action_must_be_tap_press_release");}
        return obj("status","submitted","key",code,"action",action,"verify","observe screen or resulting state");
    }
    private static JsonObject scroll(Minecraft mc,JsonObject args) {
        Screen s=screen(mc);double x=UiArguments.coordinate(number(args,"x",s.width/2d),s.width),y=UiArguments.coordinate(number(args,"y",s.height/2d),s.height);
        double delta=number(args,"delta",0);if(Math.abs(delta)>100)throw new IllegalArgumentException("scroll_delta_out_of_range");
        boolean handled=s.mouseScrolled(x,y,delta);s.afterMouseAction();return obj("status","submitted","handled",handled);
    }
    private static JsonObject focus(Minecraft mc,JsonObject args) {
        Screen s=screen(mc);Node n=target(s,str(args,"id",""));active(n);
        for(int i=0;i<n.parents.size();i++)n.parents.get(i).setFocused(i+1<n.parents.size()?n.parents.get(i+1):n.listener);
        n.listener.setFocused(true);return obj("status",n.listener.isFocused()?"observed_success":"submitted","id",str(args,"id",""));
    }
    private static GuiEventListener focused(Screen screen) {
        GuiEventListener current=screen;
        for(int i=0;i<16&&current instanceof ContainerEventHandler c;i++){GuiEventListener next=c.getFocused();if(next==null)break;current=next;}
        return current;
    }
    private static JsonObject text(Minecraft mc,JsonObject args) {
        Screen s=screen(mc);String id=str(args,"id","");GuiEventListener field;
        if(id.isEmpty())field=focused(s);else{Node n=target(s,id);active(n);field=n.listener;}
        if(!field.isFocused()||focused(s)!=field)throw new IllegalArgumentException("text_field_not_focused_use_ui_focus");
        String value=UiArguments.text(str(args,"text",""));boolean append=bool(args,"append",false);String actual;
        if(field instanceof EditBox e) {
            if(!e.canConsumeInput())throw new IllegalArgumentException("text_field_read_only");
            if(append){e.moveCursorToEnd();e.insertText(value);}else e.setValue(value);actual=e.getValue();
        } else if(field instanceof MultiLineEditBox e){String combined=append?e.getValue()+value:value;UiArguments.text(combined);e.setValue(combined);actual=e.getValue();}
        else throw new IllegalArgumentException("focused_control_is_not_a_supported_text_field");
        return obj("status","observed_success","text_length",actual.length(),"focus_preserved",focused(s)==field,"input_may_be_filtered",true);
    }
    private static JsonObject create(Minecraft mc,JsonObject args) {
        if(mc.level!=null)return obj("error","already_in_world","reason","Leave the current world before creating another");
        // Validate the entire request before opening or changing any UI.
        String name=UiArguments.text(str(args,"name","New World"));if(name.isBlank()||name.length()>128)throw new IllegalArgumentException("invalid_world_name");
        String seed=UiArguments.text(str(args,"seed",""));if(seed.length()>128)throw new IllegalArgumentException("invalid_seed");
        WorldCreationUiState.SelectedGameMode mode;
        try{mode=WorldCreationUiState.SelectedGameMode.valueOf(str(args,"gamemode","SURVIVAL").toUpperCase(Locale.ROOT));}
        catch(RuntimeException e){throw new IllegalArgumentException("gamemode_must_be_survival_creative_hardcore");}
        if(mode==WorldCreationUiState.SelectedGameMode.DEBUG)throw new IllegalArgumentException("debug_world_not_supported_by_create");
        Difficulty difficulty;try{difficulty=Difficulty.valueOf(str(args,"difficulty","NORMAL").toUpperCase(Locale.ROOT));}catch(RuntimeException e){throw new IllegalArgumentException("invalid_difficulty");}
        boolean structures=bool(args,"generate_structures",true),cheats=bool(args,"cheats",false);
        UiSafety.authorize(Runtime.config().mode,false,false,cheats);
        if(!(mc.screen instanceof CreateWorldScreen))CreateWorldScreen.openFresh(mc,mc.screen==null?new TitleScreen():mc.screen);
        if(!(mc.screen instanceof CreateWorldScreen screen))return obj("status","submitted","phase","opening_create_world_screen","next","Repeat world.create with the same arguments once screen is CreateWorldScreen");
        var ui=screen.getUiState();ui.setName(name);ui.setSeed(seed);ui.setGameMode(mode);ui.setDifficulty(difficulty);ui.setGenerateStructures(structures);ui.setAllowCheats(cheats);
        UiSafety.authorize(Runtime.config().mode,false,false,ui.isAllowCheats());
        Iterator<Node> nodes=nodes(screen);int count=0;
        while(nodes.hasNext()&&count++<4096) {
            GuiEventListener listener=nodes.next().listener;
            if(listener instanceof Button button&&button.active&&button.visible&&"selectWorld.create".equals(translation(button.getMessage()))) {
                button.onPress();return obj("status","submitted","phase","vanilla_create_button_pressed","name",name,"verify","observe state/player after the world loads; vanilla confirmation screens may require UI input");
            }
        }
        return obj("status","submitted","phase","create_world_settings_applied","reason","No active vanilla create button was found in public controls","next","observe screen and click the displayed create/confirmation control");
    }
    private static JsonObject join(Minecraft mc,JsonObject args) {
        if(mc.level!=null)return obj("error","already_in_world","reason","Leave the current world before joining another");
        String world=str(args,"world",""),address=str(args,"address","");if(world.isEmpty()==address.isEmpty())throw new IllegalArgumentException("exactly_one_of_world_or_address_required");
        Screen parent=mc.screen==null?new TitleScreen():mc.screen;
        if(!world.isEmpty()) {
            UiArguments.worldId(world);if(!mc.getLevelSource().levelExists(world))return obj("error","world_not_found","reason",world);
            mc.createWorldOpenFlows().loadLevel(parent,world);return obj("status","submitted","world",world,"verify","observe player/state; vanilla compatibility or backup confirmation may need UI input");
        }
        if(address.length()>255||address.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))||!ServerAddress.isValidAddress(address))throw new IllegalArgumentException("invalid_server_address");
        String name=str(args,"name",address);if(name.length()>128)throw new IllegalArgumentException("server_name_too_long");
        ConnectScreen.startConnecting(parent,mc,ServerAddress.parseString(address),new ServerData(name,address,false),false);
        return obj("status","submitted","address",address,"verify","observe player/state or connection error screen");
    }
    private static JsonObject leave(Minecraft mc) {
        if(mc.level==null){mc.setScreen(new TitleScreen());return obj("status","observed_success","in_world",false);}
        mc.level.disconnect();mc.clearLevel(new TitleScreen());return obj("status",mc.level==null?"observed_success":"submitted","in_world",mc.level!=null);
    }
    private static JsonObject lan(Minecraft mc,JsonObject args) {
        var server=mc.getSingleplayerServer();if(server==null)return obj("error","singleplayer_required","reason","Open LAN is only available for the local integrated server");
        int port=integer(args,"port",25565);if(port<1024||port>65535)throw new IllegalArgumentException("lan_port_out_of_range_1024_65535");
        GameType mode=GameType.byName(str(args,"gamemode",mc.gameMode==null?"survival":mc.gameMode.getPlayerMode().getName()),null);if(mode==null)throw new IllegalArgumentException("invalid_gamemode");
        boolean cheats=bool(args,"cheats",false);
        UiSafety.authorize(Runtime.config().mode,false,false,cheats);
        if(server.isPublished())return obj("status","observed_success","already_published",true,"port",server.getPort());
        boolean ok=server.publishServer(mode,cheats,port);return ok?obj("status","observed_success","published",server.isPublished(),"port",server.getPort()):obj("error","lan_publish_failed","reason","Vanilla server could not bind or publish the requested port");
    }
    private static JsonObject screenshot(Minecraft mc,JsonObject args) throws IOException {
        if(!RenderSystem.isOnRenderThread())return obj("error","render_thread_required","reason","Schedule screenshot on the render thread");
        var target=mc.getMainRenderTarget();int fullW=target.width,fullH=target.height;
        if(fullW<1||fullH<1||(long)fullW*fullH>16777216L)return obj("error","framebuffer_too_large","reason","Screenshot framebuffer limit is 16777216 pixels");
        int x=integer(args,"x",0),y=integer(args,"y",0),w=integer(args,"width",fullW-x),h=integer(args,"height",fullH-y);
        if(x<0||y<0||w<1||h<1||(long)x+w>fullW||(long)y+h>fullH||(long)w*h>4194304L)throw new IllegalArgumentException("invalid_screenshot_bounds_max_4194304_pixels");
        byte[] bytes;
        try(NativeImage original=Screenshot.takeScreenshot(target)) {
            if(x==0&&y==0&&w==fullW&&h==fullH)bytes=original.asByteArray();
            else try(NativeImage crop=new NativeImage(w,h,false)){original.copyRect(crop,x,y,0,0,w,h,false,false);bytes=crop.asByteArray();}
        }
        if(bytes.length>8*1024*1024)return obj("error","screenshot_payload_too_large","reason","PNG limit is 8 MiB; request a smaller region");
        JsonObject data=obj("mime","image/png","base64",Base64.getEncoder().encodeToString(bytes),"width",w,"height",h,"bounds",obj("x",x,"y",y,"width",w,"height",h),"framebuffer",obj("width",fullW,"height",fullH),"coordinate_space","framebuffer_pixels_top_left");
        return ObservJson.envelope(data,"render_framebuffer","requested_image_region",true,false);
    }
    private static String translation(Component message) {return message.getContents() instanceof TranslatableContents t?t.getKey():null;}
    private UiActions(){}
}
