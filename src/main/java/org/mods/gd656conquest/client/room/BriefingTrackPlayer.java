package org.mods.gd656conquest.client.room;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.audio.TimelineSound;
import org.mods.gd656conquest.common.match.BriefingTimeline;

/** Used identically by the editor preview and authoritative room playback. */
public final class BriefingTrackPlayer {
    private BriefingTimeline.Track track=BriefingTimeline.Track.empty();
    private int seconds=20;
    private double ticks;
    private final Set<Integer> started = new HashSet<>();
    private final Map<Integer,TimelineSound> sounds = new HashMap<>();
    public void load(BriefingTimeline.Track track,int seconds,double ticks) { stop(); this.track=track;this.seconds=seconds;this.ticks=ticks; }
    public void position(double ticks, boolean playing) {
        this.ticks=ticks;
        if (!playing) { stopSounds(); return; }
        for(int i=0;i<track.sounds().size();i++) {
            var clip=track.sounds().get(i);
            if (!clip.active(ticks,seconds)) {
                var sound=sounds.remove(i); if(sound!=null) sound.cancel();
            } else if(started.add(i)) {
                try {
                    var mc=Minecraft.getInstance();
                    if(mc.getSoundManager().getSoundEvent(new net.minecraft.resources.ResourceLocation(clip.value()))==null) continue;
                    var sound=new TimelineSound(clip.value(),clip.volume(),(ticks-clip.start(seconds))/20,clip.durationTicks()/20.0);
                    sounds.put(i,sound);mc.getSoundManager().play(sound);
                } catch(RuntimeException error) { com.mojang.logging.LogUtils.getLogger().warn("Unable to play briefing sound {}",clip.value(),error); }
            }
        }
    }
    public void seek(double ticks,boolean playing) { stopSounds();started.clear();position(ticks,playing); }
    public boolean hasActiveSounds() {
        var manager = Minecraft.getInstance().getSoundManager();
        return manager != null && sounds.values().stream()
            .anyMatch(sound -> !sound.isStopped() && manager.isActive(sound) && sound.getVolume() > 0);
    }
    public String text() { var clip=track.subtitleAt(ticks,seconds);return clip==null?"":clip.value(); }
    /** Keep the editor preview and live playback at the same viewport position and wrap width. */
    public void renderAtGameplayPosition(GuiGraphics gui,double subtitleScale) {
        int y=(int)(gui.guiHeight()*.80);
        render(gui,gui.guiWidth()/2,y,(int)(gui.guiWidth()*.85),gui.guiHeight()-y-8,subtitleScale);
    }
    public void render(GuiGraphics gui,int centerX,int y,int maxWidth,int maxHeight,double subtitleScale) {
        var clip=track.subtitleAt(ticks,seconds); if(clip==null) return;
        float alpha=(float)Math.min(1,Math.min((ticks-clip.start(seconds))/6.0,(clip.end(seconds)-ticks)/6.0));
        if(alpha<=.02) return;
        var font=Minecraft.getInstance().font;
        float scale=(float)org.mods.gd656conquest.common.match.BriefingSubtitleStyle.validate(subtitleScale);
        var lines=font.split(Component.literal(clip.value()),Math.max(1,(int)Math.floor(maxWidth/scale)));
        int maxLines=Math.max(0,1+(int)Math.floor((maxHeight/scale-font.lineHeight)/(font.lineHeight+3)));
        gui.pose().pushPose();
        try {
            gui.pose().translate(centerX,y,0);gui.pose().scale(scale,scale,1);
            int count=0;
            for(var line:lines) {
                if(count>=maxLines)break;
                gui.drawString(font,line,-font.width(line)/2,count*(font.lineHeight+3),(Math.max(4,Math.round(alpha*255))<<24)|0xFFFFFF,true);
                count++;
            }
        } finally { gui.pose().popPose(); }
    }
    public void stopSounds() { for(var sound:sounds.values()) sound.cancel();sounds.clear(); }
    public void stop() { stopSounds();started.clear();track=BriefingTimeline.Track.empty(); }
}
