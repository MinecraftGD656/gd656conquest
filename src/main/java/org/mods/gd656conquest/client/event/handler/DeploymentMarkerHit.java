package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import org.mods.gd656killicon.client.gui.theme.*;
import org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Theme;

/** One shape and panel-exclusion rule is shared by hover and actual selection. */
final class DeploymentMarkerHit {
    static boolean contains(ClientCaptureOverlayModel.CaptureIconEntry entry,ClientCaptureOverlayModel.TeamToken self,double cx,double cy,double radius,Minecraft mc){
        double x=mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/mc.getWindow().getScreenWidth();
        double y=mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/mc.getWindow().getScreenHeight();
        var theme=BattlefieldUiTheme.current();
        if(panel(theme,x,y,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight()))return false;
        boolean neutral=entry.owner()==ClientCaptureOverlayModel.TeamToken.NEUTRAL;
        boolean ally=!neutral&&entry.owner()==self;
        if(entry.isTeammateMarker()||entry.isBeaconMarker())return Math.hypot(x-cx,y-cy)<=radius;
        if(entry.isVehicleMarker())return Math.abs(x-cx)+Math.abs(y-cy)<=radius;
        return BattlefieldDeployMarkerLayout.contains(theme,entry.isSpawnPoint(),ally,neutral,x-cx,y-cy,radius);
    }
    static boolean shape(Theme theme,boolean ally,boolean neutral,double dx,double dy,double radius){
        return BattlefieldDeployMarkerLayout.contains(theme,false,ally,neutral,dx,dy,radius);
    }
    static boolean panel(Theme theme,double x,double y,int width,int height){
        double scale=Math.min(width/1920d,height/1080d);x=(x-(width-1920*scale)/2)/scale;y=(y-(height-1080*scale)/2)/scale;
        if(y<145||y>=952)return true;
        if(theme==Theme.BF6&&(x>=1480&&y>=460||x<430&&y<385))return true;
        if(theme==Theme.BF1&&y>=920)return true;
        for(var action:BattlefieldDeploymentLayout.Action.values()){
            var area=BattlefieldDeploymentLayout.action(theme,action);
            if(x>=area.x()&&x<=area.x()+area.w()&&y>=area.y()&&y<=area.y()+area.h())return true;
        }
        var r=BattlefieldDeploymentLayout.classRect(theme);
        return x>=r.x()&&x<=r.x()+r.w()&&y>=r.y()&&y<=r.y()+r.h();
    }
}
