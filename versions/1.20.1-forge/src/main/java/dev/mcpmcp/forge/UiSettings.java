package dev.mcpmcp.forge;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class UiSettings {
    static void change(Supplier<Configuration> current,Runnable save,Consumer<Configuration> edit){
        edit.accept(current.get());save.run();
    }
    private UiSettings(){}
}
