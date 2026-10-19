package com.zianblk.zianutilities.neoforge.breeding.client;
public final class GuiReturn {
 private GuiReturn(){}
 public static boolean returnToMenu(){try{return (Boolean)Class.forName("com.ianblk.ziangui.client.ZianGuiClient").getMethod("returnToLastMenu").invoke(null);}catch(ReflectiveOperationException|LinkageError unavailable){return false;}}
}
