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
    public String text() { var clip=track.subtitleAt(ticks,seconds);return clip==null?"":clip.value(); }
    public void render(GuiGraphics gui,int centerX,int y,int maxWidth) {
        var clip=track.subtitleAt(ticks,seconds); if(clip==null) return;
        float alpha=(float)Math.min(1,Math.min((ticks-clip.start(seconds))/6.0,(clip.end(seconds)-ticks)/6.0));
        if(alpha<=.02) return;
        var font=Minecraft.getInstance().font;
        var lines=font.split(Component.literal(clip.value()),Math.max(40,maxWidth));
        int count=0,maxLines=Math.max(1,(gui.guiHeight()-y-8)/(font.lineHeight+3));
        for(var line:lines) { if(count++>=maxLines)break; gui.drawString(font,line,centerX-font.width(line)/2,y,(Math.max(4,Math.round(alpha*255))<<24)|0xFFFFFF,true);y+=font.lineHeight+3; }
    }
    public void stopSounds() { for(var sound:sounds.values()) sound.cancel();sounds.clear(); }
    public void stop() { stopSounds();started.clear();track=BriefingTimeline.Track.empty(); }
}
