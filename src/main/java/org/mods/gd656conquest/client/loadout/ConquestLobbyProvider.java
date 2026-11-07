package org.mods.gd656conquest.client.loadout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.mods.gd656conquest.client.loadout.screen.LoadoutConfigScreen;
import org.mods.gd656conquest.client.loadout.screen.LoadoutVehicleDeployScreen;
import org.mods.gd656conquest.client.loadout.state.ClientRoomSelectionState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiRequestPacket;
import org.mods.gd656conquest.network.packet.RoomSelectionRequestPacket;
import org.mods.gd656killicon.client.gui.theme.lobby.BattlefieldLobbySource;
import org.mods.gd656killicon.client.gui.scoreboard.ScoreboardMatchSource;
@Mod.EventBusSubscriber(modid="gd656conquest",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ConquestLobbyProvider implements BattlefieldLobbySource.Provider {
 @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{
  BattlefieldLobbySource.register(new ConquestLobbyProvider());ScoreboardMatchSource.register(new ConquestMatchProvider());
  org.mods.gd656killicon.client.gui.theme.compat.SquadRosterAccess.register(ConquestTacticalProvider::squad);
  org.mods.gd656killicon.client.gui.theme.BattlefieldMinimapSource.register(ConquestTacticalProvider::objectives);
  org.mods.gd656killicon.client.gui.theme.BattlefieldMinimapSource.registerMarkers(ConquestTacticalProvider::markers);
 });}
 @Override public void refresh(){
  Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.getConnection()==null)return;
  NetworkManager.getChannel().sendToServer(new LoadoutUiRequestPacket(false));NetworkManager.getChannel().sendToServer(new RoomSelectionRequestPacket());
 }
 @Override public BattlefieldLobbySource.Identity identity(){
  var mc=Minecraft.getInstance();var snapshot=ClientLoadoutUiState.snapshot();var rooms=ClientRoomSelectionState.snapshot();
  var team=mc.player==null?null:mc.player.getTeam();var match=ScoreboardMatchSource.read(team==null?"":team.getName());
  String context=!match.mode().equals("—")?match.mode()+"  /  "+match.map():rooms==null?"读取房间列表":rooms.entries().size()+" 个房间 · 选择房间开始作战";
  var cls=snapshot==null?null:snapshot.classes().get(snapshot.currentClassKey());
  return new BattlefieldLobbySource.Identity(context,cls==null?"—":cls.className(),snapshot==null?"—":Integer.toString(snapshot.playerLevel()),snapshot!=null&&snapshot.admin()&&mc.player!=null&&mc.player.hasPermissions(2));
 }
 @Override public boolean deploymentActive(){return org.mods.gd656conquest.client.room.ClientDeathFlowState.shouldRenderDeployBoundaries();}
 @Override public boolean quickJoin(){
  if(Minecraft.getInstance().player==null)return false;
  NetworkManager.getChannel().sendToServer(new org.mods.gd656conquest.network.packet.QuickAccessUsePacket());
  return true;
 }
 /** Closes the lobby once the server confirms the quick-join item actually did something. */
 public static void onQuickJoinResult(boolean success){
  org.mods.gd656killicon.client.gui.theme.lobby.BattlefieldLobbyScreen.notifyQuickJoinResult(success);
 }
 @Override public Screen page(String id,Screen parent){
  if(Minecraft.getInstance().player==null)return null;
  if(id.equals("social"))return new org.mods.gd656conquest.client.social.BattlefieldSocialScreen(parent);
  if(id.equals("vehicles"))return new LoadoutVehicleDeployScreen(parent);
  int index=switch(id){case "rooms"->1;case "room-stats"->2;case "parkour"->identity().admin()?7:3;case "administration"->identity().admin()?4:1;default->0;};
  if(id.startsWith("native:"))index=Integer.parseInt(id.substring(7));
  return new LoadoutConfigScreen(parent,index);
 }
}
