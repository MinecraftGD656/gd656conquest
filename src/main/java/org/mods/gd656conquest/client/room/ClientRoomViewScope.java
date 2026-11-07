package org.mods.gd656conquest.client.room;
import net.minecraft.client.Minecraft;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;

/** A room snapshot becomes visible when it agrees with the active native room dimension. */
public final class ClientRoomViewScope {
 private ClientRoomViewScope() {}
 public static boolean matches(String dimension,String room){
  if(dimension.equals("gd656conquest:hub"))return false;
  String prefix="gd656conquest:room_";
  return !dimension.startsWith(prefix)||dimension.substring("gd656conquest:".length()).equals(room);
 }
 public static boolean current(){var mc=Minecraft.getInstance();return mc.level!=null&&matches(mc.level.dimension().location().toString(),ClientHudDebugState.getRoomId());}
}
