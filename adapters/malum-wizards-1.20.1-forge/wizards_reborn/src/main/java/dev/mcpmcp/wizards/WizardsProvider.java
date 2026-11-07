package dev.mcpmcp.wizards;
import dev.mcpmcp.magic.MagicProvider;
public final class WizardsProvider extends MagicProvider {
    public WizardsProvider(){super("wizards_reborn",new WizardsBackend());}
    public WizardsProvider(Backend backend){super("wizards_reborn",backend);}
}
