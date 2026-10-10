package org.mods.gd656conquest.server.compat.ragdoll;

import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A small main-thread view of a native record. Never copies the part transforms. */
public final class RagdollRecordView {
    public final Object record;
    public final int id, expiresTick;
    public final ResourceKey<Level> dimension;
    public final UUID owner, victim;
    public final boolean live, moving;
    public final Vec3 anchor;
    public final AABB bounds;
    private final Object settledPose;

    private static final ClassValue<Fields> FIELDS = new ClassValue<>() {
        protected Fields computeValue(Class<?> type) { return new Fields(type); }
    };
    private record Fields(Field id, Field dimension, Field origin, Field expires, Field owner,
                          Field victim, Field live, Field settled, Field latest, Field streamAnchor) {
        Fields(Class<?> type) { this(field(type,"entityId"),field(type,"dimension"),field(type,"deathPosition"),
            field(type,"expiresTick"),field(type,"streamOwner"),field(type,"victim"),field(type,"live"),
            field(type,"settledTransforms"),field(type,"latestStreamTransforms"),field(type,"streamAnchor")); }
    }
    private record Coordinates(Field position, Field x, Field y, Field z) {}
    private static final ClassValue<Coordinates> COORDINATES = new ClassValue<>() {
        protected Coordinates computeValue(Class<?> type) {
            Field position = field(type,"position");
            return new Coordinates(position,field(position.getType(),"x"),field(position.getType(),"y"),field(position.getType(),"z"));
        }
    };
    private static Field field(Class<?> type, String name) {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) try { Field f=c.getDeclaredField(name); f.setAccessible(true); return f; }
        catch (NoSuchFieldException ignored) {}
        throw new IllegalStateException("Ragdollified record API changed: "+name);
    }
    @SuppressWarnings("unchecked")
    public RagdollRecordView(Object record) {
        this.record=record;
        try {
            Fields f=FIELDS.get(record.getClass());
            id=f.id.getInt(record); expiresTick=f.expires.getInt(record);
            dimension=(ResourceKey<Level>)f.dimension.get(record);
            owner=(UUID)f.owner.get(record); victim=(UUID)f.victim.get(record); live=f.live.getBoolean(record);
            Object[] settled=(Object[])f.settled.get(record), latest=(Object[])f.latest.get(record);
            settledPose=settled;
            moving=settled==null;
            Object[] pose=settled!=null ? settled : latest;
            Vec3 p=pose!=null && pose.length>0 && pose[0]!=null ? position(pose[0]) : (Vec3)f.streamAnchor.get(record);
            anchor=p!=null ? p : (Vec3)f.origin.get(record);
            double x0=anchor.x, y0=anchor.y, z0=anchor.z, x1=x0,y1=y0,z1=z0;
            if(pose!=null) for(Object part:pose) if(part!=null) {
                Vec3 v=position(part);
                x0=Math.min(x0,v.x); y0=Math.min(y0,v.y); z0=Math.min(z0,v.z);
                x1=Math.max(x1,v.x); y1=Math.max(y1,v.y); z1=Math.max(z1,v.z);
            }
            // Include the extent of the limbs, not just their centres; keep a usable initial box.
            bounds=new AABB(x0-.75,y0-.75,z0-.75,x1+.75,y1+.75,z1+.75);
        } catch(IllegalAccessException error) { throw new IllegalStateException(error); }
    }
    /** Native validated poses are replaced, never mutated after publication. */
    public boolean reusable(Object current) {
        if(current!=record||settledPose==null)return false;
        try {
            Fields f=FIELDS.get(current.getClass());
            return f.settled.get(current)==settledPose&&java.util.Objects.equals(owner,f.owner.get(current))
                &&live==f.live.getBoolean(current)&&expiresTick==f.expires.getInt(current);
        } catch(IllegalAccessException error) {throw new IllegalStateException(error);}
    }
    private static Vec3 position(Object part) throws IllegalAccessException {
        Coordinates f=COORDINATES.get(part.getClass()); Object p=f.position.get(part);
        return new Vec3(f.x.getFloat(p),f.y.getFloat(p),f.z.getFloat(p));
    }
}
