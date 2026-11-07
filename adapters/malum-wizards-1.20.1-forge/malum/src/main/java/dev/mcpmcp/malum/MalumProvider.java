package dev.mcpmcp.malum;
import dev.mcpmcp.magic.MagicProvider;
public final class MalumProvider extends MagicProvider {
    public MalumProvider(){super("malum",new MalumBackend());}
    public MalumProvider(Backend backend){super("malum",backend);}
}
