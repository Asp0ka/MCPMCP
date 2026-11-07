package dev.mcpmcp.magic;

import com.google.gson.JsonObject;
import net.minecraftforge.fml.ModList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import static dev.mcpmcp.core.Jsons.*;

/** Bind both native cast entrypoints to the exact Iron's implementation reviewed offline. */
final class IronsRuntimeDependency {
    private static final String MOD="irons_spellbooks";
    private static final String VERSION="1.20.1-3.16.3";
    private static final String SHA256="54b5aaa52887c38f570fbd820288171234d8541c17a5e953f9271189fdd480fb";
    // Forge's loaded mods are immutable for this JVM. Trusted Java is not sandboxed;
    // each cold adapter classloader verifies independently and caches only success.
    private static boolean verified;

    /** Null means verified; every failure returns before touching native input classes/state. */
    static synchronized JsonObject check() {
        if(verified)return null;
        try {
            ModList mods=ModList.get();
            if(mods==null)return failure("dependency_verification_failed");
            if(!mods.isLoaded(MOD))return failure("dependency_mod_missing");
            var container=mods.getModContainerById(MOD);
            if(container.isEmpty())return failure("dependency_mod_missing");
            String version=container.get().getModInfo().getVersion().toString();
            if(!VERSION.equals(version)){
                JsonObject error=failure("dependency_version_mismatch");
                error.addProperty("actual_version",version);return error;
            }
            var info=mods.getModFileById(MOD);
            if(info==null||info.getFile()==null)return failure("dependency_verification_failed");
            Path jar=info.getFile().getFilePath();
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            // Hash the entire actual Forge mod file, never a name, prefix or class resource.
            try(var input=Files.newInputStream(jar)){
                byte[] buffer=new byte[65536];int count;
                while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);
            }
            String actual=HexFormat.of().formatHex(digest.digest());
            if(!SHA256.equals(actual)){
                JsonObject error=failure("dependency_hash_mismatch");
                error.addProperty("actual_sha256",actual);return error;
            }
            verified=true;
            return null;
        } catch(Exception|LinkageError e) {
            JsonObject error=failure("dependency_verification_failed");
            error.addProperty("reason",e.getClass().getSimpleName());return error;
        }
    }
    private static JsonObject failure(String error){
        return obj("error",error,"dependency",MOD,"expected_version",VERSION,"expected_sha256",SHA256,"delivery","not_submitted");
    }
    private IronsRuntimeDependency(){}
}
