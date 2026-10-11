package org.mods.gd656conquest.common.match;

/** Server snapshots own progress. Client interpolation never reopens a server-closed sequence. */
public final class TimelinePlaybackClock {
    private String room;
    private long match,sequence;
    private int authoritativeTicks,total,ticks;
    private boolean active;
    public boolean accept(String room,long match,long sequence,int elapsed,int total,boolean active) {
        if(room==null||elapsed<0||total<1||elapsed>total)throw new IllegalArgumentException("Invalid timeline clock");
        boolean same=room.equals(this.room)&&match==this.match;
        if(same && (sequence<this.sequence || (sequence==this.sequence && (elapsed<authoritativeTicks||(!this.active&&active)))))return false;
        this.room=room;this.match=match;this.sequence=sequence;this.authoritativeTicks=elapsed;this.ticks=elapsed;this.total=total;this.active=active;return true;
    }
    public void tick(){if(active)ticks=Math.min(total,ticks+1);}
    public int ticks(){return ticks;}
    public boolean playing(){return active&&ticks<total;}
    public void stop(){active=false;}
    public void reset(){room=null;match=sequence=0;authoritativeTicks=total=ticks=0;active=false;}
}
