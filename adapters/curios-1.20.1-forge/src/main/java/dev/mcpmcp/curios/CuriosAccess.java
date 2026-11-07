package dev.mcpmcp.curios;

import com.google.gson.*;

/** Game boundary. Detached test implementations do not model a running server. */
public interface CuriosAccess {
 JsonObject capture();
 JsonArray slots();
 default JsonArray types(){return new JsonArray();}
 JsonArray candidates(JsonObject arguments);
 void preflight(String action,JsonObject arguments);
 void submit(String action,JsonObject arguments);
}
