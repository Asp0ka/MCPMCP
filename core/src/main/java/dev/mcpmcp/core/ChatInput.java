package dev.mcpmcp.core;
/** Single-line native input: no prefix parsing or rewriting. */
public final class ChatInput {
 public static String validate(String text){
  if(text==null||text.isBlank()||text.length()>256||text.chars().anyMatch(c->c<32||c==127))throw new IllegalArgumentException("invalid_chat_input_single_line_max_256");
  return text;
 }
 private ChatInput(){}
}
