package dev.mcpmcp.ftbquests;
import com.google.gson.JsonObject;
public interface FtbAccess {
 /** Capture immutable JSON from the current client's synced file and self team only. */
 JsonObject capture();
 /** Called at most once after a second full preflight capture; no local prediction. */
 void submit(String action,JsonObject arguments);
}
