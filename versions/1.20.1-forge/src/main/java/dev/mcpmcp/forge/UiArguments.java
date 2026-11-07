package dev.mcpmcp.forge;

import java.util.Locale;

/** Input validation independent of a live Minecraft client. */
public final class UiArguments {
    public static int key(String name) {
        String n=name.toUpperCase(Locale.ROOT);
        if(n.startsWith("GLFW_KEY_"))n=n.substring(9);
        if(n.length()==1&&((n.charAt(0)>='A'&&n.charAt(0)<='Z')||(n.charAt(0)>='0'&&n.charAt(0)<='9')))return n.charAt(0);
        if(n.matches("F([1-9]|1[0-9]|2[0-5])"))return 289+Integer.parseInt(n.substring(1));
        return switch(n){
            case "SPACE"->32;case "ESCAPE","ESC"->256;case "ENTER","RETURN"->257;
            case "TAB"->258;case "BACKSPACE"->259;case "INSERT"->260;case "DELETE"->261;
            case "RIGHT"->262;case "LEFT"->263;case "DOWN"->264;case "UP"->265;
            case "PAGE_UP"->266;case "PAGE_DOWN"->267;case "HOME"->268;case "END"->269;
            case "LEFT_SHIFT"->340;case "LEFT_CONTROL"->341;case "LEFT_ALT"->342;case "LEFT_SUPER"->343;
            case "RIGHT_SHIFT"->344;case "RIGHT_CONTROL"->345;case "RIGHT_ALT"->346;case "RIGHT_SUPER"->347;
            default->throw new IllegalArgumentException("unknown_key");
        };
    }
    public static String text(String text) {
        if(text.length()>8192||text.indexOf(0)>=0)throw new IllegalArgumentException("invalid_text");return text;
    }
    public static String worldId(String id) {
        if(id.isBlank()||id.length()>128||id.equals(".")||id.equals("..")||id.contains("/")||id.contains("\\")||id.contains(":")||id.chars().anyMatch(c->c<32)||id.matches(".*[<>\"|?*].*"))
            throw new IllegalArgumentException("invalid_world_id");
        return id;
    }
    public static double coordinate(double value,int extent) {
        if(!Double.isFinite(value)||value<0||value>=extent)throw new IllegalArgumentException("outside_screen");return value;
    }
    private UiArguments(){}
}
