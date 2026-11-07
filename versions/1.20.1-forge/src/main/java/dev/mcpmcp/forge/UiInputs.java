package dev.mcpmcp.forge;

import java.util.LinkedHashMap;

/** Own only inputs actually acquired by automation. Never synthesize unowned releases. */
final class UiInputs {
    private final LinkedHashMap<String,Runnable> owned=new LinkedHashMap<>();
    boolean acquire(String input,boolean alreadyDown,Runnable release){
        if(alreadyDown||owned.containsKey(input))return false;
        owned.put(input,release);return true;
    }
    void release(String input){Runnable release=owned.remove(input);if(release!=null)release.run();}
    void releaseAll(){
        var releases=java.util.List.copyOf(owned.values());owned.clear();
        for(Runnable release:releases)try{release.run();}catch(RuntimeException ignored){/* Continue releasing other owned inputs. */}
    }
}
