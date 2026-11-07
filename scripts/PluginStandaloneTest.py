#!/usr/bin/env python3
"""Standalone plugin contracts; no Gradle, game, network or production stubs."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

STUBS = {'dev/mcpmcp/forge/Runtime.java': 'package dev.mcpmcp.forge; public final class Runtime {public static final class Config {public String mode="HACK",transport="PACKET";public boolean trustJavaPlugins=true;}private static final Config c=new Config();public static Config config(){return c;}public static boolean isBuiltin(String n){return false;}}', 'dev/mcpmcp/forge/PacketService.java': 'package dev.mcpmcp.forge;import com.google.gson.*;import dev.mcpmcp.core.*;public final class PacketService {static int sends;public static JsonObject read(String k,JsonObject a){JsonArray ctors=new JsonArray();JsonArray sig=new JsonArray();sig.add("int");ctors.add(sig);JsonArray entries=new JsonArray();entries.add(Jsons.obj("class",Jsons.str(a,"query",""),"direction","C2S","channel","minecraft:play","id","vanilla:C2S:1"));if(k.equals("packets"))return Jsons.obj("entries",entries);return Jsons.obj("direction","C2S","channel","minecraft:play","id","vanilla:C2S:1","constructors",ctors);}public static JsonObject send(JsonObject a){sends++;return Jsons.obj("delivery","submitted");}}', 'dev/mcpmcp/forge/Actions.java': 'package dev.mcpmcp.forge;import com.google.gson.*;public final class Actions {static int polls;public static boolean condition(JsonObject c){return ++polls>=3;}}', 'net/minecraft/client/Minecraft.java': 'package net.minecraft.client;public final class Minecraft {public Player player=new Player();public static Minecraft getInstance(){return new Minecraft();}public static class Player {public Menu containerMenu=new Menu();public java.util.UUID getUUID(){return java.util.UUID.randomUUID();}}public static class Menu {public int containerId;public int getStateId(){return 0;}}}', 'net/minecraftforge/fml/ModList.java': 'package net.minecraftforge.fml;import java.nio.file.*;public final class ModList {public static ModList get(){return new ModList();}public boolean isLoaded(String id){return false;}public Info getModFileById(String id){return new Info();}public static class Info {public Info getFile(){return this;}public Path getFilePath(){return Path.of("missing");}}}'}

def main():
    repo = Path(__file__).resolve().parents[1]
    gradle = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
    gson = list((gradle / 'caches/modules-2/files-2.1/com.google.code.gson/gson').glob('*/*/gson-*.jar'))
    if not gson:
        raise SystemExit('Cached Gson jar required; no download attempted')
    jdk = Path(os.environ.get('JAVA_HOME', '')) / 'bin'
    javac = str(jdk / 'javac.exe') if (jdk / 'javac.exe').is_file() else shutil.which('javac')
    java = str(jdk / 'java.exe') if (jdk / 'java.exe').is_file() else shutil.which('java')
    if not javac or not java:
        raise SystemExit('javac and java must be on PATH (Java 17+)')
    scratch = Path(os.environ.get('LOCALAPPDATA', str(Path.home()))) / 'hermes/cache/scratch'
    scratch.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='plugin-tests-', dir=scratch) as temporary:
        tmp = Path(temporary)
        sources = []
        for name, content in STUBS.items():
            path = tmp / 'stubs' / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding='utf-8')
            sources.append(path)
        for name in ['Jsons', 'Schema', 'Policy', 'Jobs', 'PluginFiles', 'PluginValidation', 'PluginStep']:
            sources.append(repo / ('core/src/main/java/dev/mcpmcp/core/' + name + '.java'))
        for name in ['PluginContext', 'McpmcpPlugin']:
            sources.append(repo / ('plugin-api/src/main/java/dev/mcpmcp/api/' + name + '.java'))
        sources.append(repo / 'versions/1.20.1-forge/src/main/java/dev/mcpmcp/forge/PluginManager.java')
        for name in ['PluginValidationTest', 'PluginStepTest']:
            sources.append(repo / ('core/src/test/java/dev/mcpmcp/core/' + name + '.java'))
        for name in ['PluginManagerTest', 'PluginReloadTest']:
            sources.append(repo / ('versions/1.20.1-forge/src/test/java/dev/mcpmcp/forge/' + name + '.java'))
        classes = tmp / 'classes'
        classes.mkdir()
        subprocess.run([javac, '-encoding', 'UTF-8', '--release', '17', '-cp', str(gson[0]), '-d', str(classes), *map(str, sources)], check=True, cwd=repo)
        cp = str(classes) + os.pathsep + str(gson[0])
        for test in ['dev.mcpmcp.core.PluginValidationTest', 'dev.mcpmcp.core.PluginStepTest', 'dev.mcpmcp.forge.PluginReloadTest', 'dev.mcpmcp.forge.PluginManagerTest']:
            subprocess.run([java, '-cp', cp, test, str(tmp)], check=True, cwd=repo)
        print('Standalone plugin contracts: PASS (temporary stubs only)')

if __name__ == '__main__':
    main()
