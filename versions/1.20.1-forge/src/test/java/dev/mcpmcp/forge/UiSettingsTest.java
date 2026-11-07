package dev.mcpmcp.forge;

/** Regression for config instance replacement while the settings screen stays open. */
public final class UiSettingsTest {
    public static void main(String[] args){
        Configuration old=new Configuration(),fresh=new Configuration();
        Configuration[] current={old};int[] saves={0};
        java.util.function.Supplier<Configuration> supplier=()->current[0];
        java.util.function.Consumer<Configuration> callback=ignored->UiSettings.change(supplier,()->{
            if(!current[0].mode.equals("CHEAT"))throw new AssertionError("saved stale config");saves[0]++;
        },c->c.mode="CHEAT");
        current[0]=fresh;callback.accept(old);
        if(!old.mode.equals("NORMAL")||!fresh.mode.equals("CHEAT")||saves[0]!=1)throw new AssertionError("stale callback");
        System.out.println("UiSettingsTest: current instance mutated and saved; old instance untouched");
    }
}
