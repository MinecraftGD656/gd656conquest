package org.mods.gd656conquest.client.social;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.social.*;

/** Shared authoritative snapshot; only the acknowledgement for the active action releases its wait state. */
@Mod.EventBusSubscriber(modid="gd656conquest",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SocialClient {
    private static Connection connection;
    private static SocialState.Snapshot snapshot;
    private static long sequence,pending,nextQuery;
    private static String error="";
    private SocialClient() {}
    public static boolean supported(){var c=Minecraft.getInstance().getConnection();return c!=null&&SocialNetwork.supported(c.getConnection());}
    public static SocialState.Snapshot snapshot(){return snapshot;}
    public static boolean pending(){return pending!=0;}
    public static String error(){return error;}
    public static void open(){var mc=Minecraft.getInstance();mc.setScreen(org.mods.gd656killicon.client.gui.theme.lobby.BattlefieldLobbyScreen.forPage(mc.screen,org.mods.gd656killicon.client.gui.theme.lobby.BattlefieldLobbyLayout.Section.SOCIAL,"social"));}
    public static void request(){if(supported()&&System.currentTimeMillis()>=nextQuery){nextQuery=System.currentTimeMillis()+750;SocialNetwork.query();}}
    public static void action(String kind,String target,String value){
        if(pending())return;if(!supported()){error="服务器尚未提供组队界面支持";return;}
        error="";pending=++sequence;SocialNetwork.act(new SocialState.Action(pending,kind,target==null?"":target,value==null?"":value));
    }
    public static void receive(SocialState.Snapshot value){
        if(value==null)return;
        snapshot=value;
        if(value.actionResult()&&value.requestId()==pending){pending=0;error=value.error();}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var client=Minecraft.getInstance().getConnection();Connection next=client==null?null:client.getConnection();
        if(next!=connection){connection=next;snapshot=null;pending=0;nextQuery=0;error="";}
    }
}
