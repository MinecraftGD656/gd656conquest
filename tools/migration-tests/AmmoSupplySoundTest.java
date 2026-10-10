package tests;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import org.mods.gd656conquest.client.room.ClientAmmoSupplySound;
import org.mods.gd656conquest.mixin.client.AmmoSupplySoundAccessor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Real Forge sound events and channel state, without opening an audio device. */
public final class AmmoSupplySoundTest {
    static int checks;
    static sun.misc.Unsafe unsafe;
    static Method filter;
    static void check(boolean ok,String label) {checks++;if(!ok)throw new AssertionError(label);}
    static final class Engine extends SoundEngine implements AmmoSupplySoundAccessor {
        Map<SoundInstance,ChannelAccess.ChannelHandle> channels;
        boolean bookkeeping;
        private Engine() {super(null,null,null);}
        public Map<SoundInstance,ChannelAccess.ChannelHandle> gd656$ammoSoundChannels() {return channels;}
        @Override public boolean m_120305_(SoundInstance sound) {return bookkeeping;}
    }
    static Engine engine() throws Exception {
        var result=(Engine)unsafe.allocateInstance(Engine.class);result.channels=new HashMap<>();return result;
    }
    static final class Cue implements SoundInstance {
        private final ResourceLocation id;
        Cue(String id) {this.id=ResourceLocation.m_135820_(id);}
        public ResourceLocation m_7904_() {return id;}
        public net.minecraft.client.sounds.WeighedSoundEvents m_6775_(net.minecraft.client.sounds.SoundManager manager) {return null;}
        public net.minecraft.client.resources.sounds.Sound m_5891_() {return null;}
        public net.minecraft.sounds.SoundSource m_8070_() {return net.minecraft.sounds.SoundSource.PLAYERS;}
        public boolean m_7775_() {return false;}
        public boolean m_7796_() {return false;}
        public int m_7766_() {return 0;}
        public float m_7769_() {return 1;}
        public float m_7783_() {return 1;}
        public double m_7772_() {return 0;}
        public double m_7780_() {return 0;}
        public double m_7778_() {return 0;}
        public Attenuation m_7438_() {return Attenuation.NONE;}
    }
    static SoundInstance sound(String id) {return new Cue(id);}
    static boolean request(Engine engine,SoundInstance sound,long nanos) throws Exception {
        var event=new PlaySoundEvent(engine,sound);filter.invoke(null,event,nanos);return event.getSound()!=null;
    }
    static ChannelAccess.ChannelHandle handle(boolean stopped) throws Exception {
        var result=(ChannelAccess.ChannelHandle)unsafe.allocateInstance(ChannelAccess.ChannelHandle.class);
        var field=ChannelAccess.ChannelHandle.class.getDeclaredField("f_120147_");field.setAccessible(true);field.setBoolean(result,stopped);return result;
    }
    static MethodNode method(String owner,String name) throws Exception {
        return method(owner,name,null);
    }
    static MethodNode method(String owner,String name,String descriptor) throws Exception {
        try(var in=AmmoSupplySoundTest.class.getClassLoader().getResourceAsStream(owner.replace('.','/')+".class")) {
            var node=new ClassNode();new ClassReader(in.readAllBytes()).accept(node,0);
            return node.methods.stream().filter(m->m.name.equals(name)&&(descriptor==null||descriptor.equals(m.desc))).findFirst().orElseThrow();
        }
    }
    static long calls(MethodNode method,String owner,String name) {
        return Arrays.stream(method.instructions.toArray()).filter(i->i instanceof MethodInsnNode c&&c.owner.endsWith(owner)&&c.name.equals(name)).count();
    }
    public static void main(String[] args) throws Exception {
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);unsafe=(sun.misc.Unsafe)field.get(null);
        filter=ClientAmmoSupplySound.class.getDeclaredMethod("filter",PlaySoundEvent.class,long.class);filter.setAccessible(true);
        var engine=engine();var first=sound("gd656conquest:sfx.ammo_bag_pickup");var next=sound("gd656conquest:sfx.ammo_bag_pickup");
        ClientAmmoSupplySound.reset();
        check(request(engine,first,0),"first successful resupply plays");
        for(long time:new long[]{0,1,10_000_000,49_999_999})check(!request(engine,next,time),"same-frame and pending channel requests do not overlap");
        engine.channels.put(first,handle(false));
        for(long time:new long[]{50_000_000,100_000_000,500_000_000,1_000_000_000,5_000_000_000L})
            check(!request(engine,next,time),"active channel is never restarted or layered, including longer resource-pack audio");
        engine.channels.put(first,handle(true));engine.bookkeeping=true;
        check(request(engine,next,5_100_000_000L),"released channel allows next sound despite stale one-second engine bookkeeping");
        engine.channels.put(next,handle(false));
        check(request(engine,sound("gd656conquest:sfx.ammo_bag_throw"),5_100_000_000L),"throw sound is unaffected");
        check(request(engine,sound("minecraft:entity.player.hurt"),5_100_000_000L),"unrelated audio is unaffected");
        ClientAmmoSupplySound.reset();engine.channels.clear();engine.bookkeeping=false;
        SoundInstance current=first;
        for(int second=0;second<6;second++) {
            check(request(engine,current,second*1_000_000_000L),"normal one-second replenishment plays each time");
            engine.channels.put(current,handle(true));current=sound("gd656conquest:sfx.ammo_bag_pickup");
        }
        ClientAmmoSupplySound.reset();engine.channels.clear();engine.bookkeeping=false;
        check(request(engine,first,0),"first source starts cue");engine.channels.put(first,handle(false));
        check(!request(engine,next,100_000_000),"another box or bag cue shares the recipient's active playback");
        ClientAmmoSupplySound.reset();check(request(engine,next,100_000_001),"disconnect/resource reload clears sound ownership");
        check(request(engine(),first,100_000_002),"a replacement sound engine starts cleanly");
        ClientAmmoSupplySound.reset();engine.channels.clear();engine.bookkeeping=false;
        check(request(engine,first,0),"initial asynchronous request accepted");engine.bookkeeping=true;
        check(!request(engine,next,60_000_000),"registered or queued sound without a channel remains protected");
        engine.bookkeeping=false;check(request(engine,next,100_000_000),"failed or inaudible request does not lock later successful sound");
        ClientAmmoSupplySound.reset();engine.channels.clear();engine.bookkeeping=false;
        var heal=sound("gd656conquest:sfx.player_heal");var nextHeal=sound("gd656conquest:sfx.player_heal");
        check(request(engine,heal,0),"medical box first successful heal plays");
        check(!request(engine,nextHeal,0),"simultaneous medical box heals share pending playback");
        engine.channels.put(heal,handle(false));
        check(!request(engine,nextHeal,500_000_000),"half-second healing does not overlap the existing 0.64-second clip");
        check(request(engine,first,500_000_000),"ammo sound can play independently while healing sound is active");
        engine.channels.put(first,handle(false));
        check(!request(engine,next,600_000_000),"ammo retains its own overlap protection during healing");
        check(!request(engine,nextHeal,600_000_000),"another medical box or natural regen cannot layer the same healing cue");
        engine.channels.put(heal,handle(true));engine.bookkeeping=true;
        check(request(engine,nextHeal,1_000_000_000),"next successful heal plays after channel ends despite stale engine records");
        engine.channels.put(nextHeal,handle(false));
        check(!request(engine,sound("gd656conquest:sfx.player_heal"),1_500_000_000),"subsequent half-second heal still cannot overlap");
        engine.channels.put(nextHeal,handle(true));
        check(request(engine,sound("gd656conquest:sfx.player_heal"),2_000_000_000),"continuous box healing repeats when prior cue has ended");
        check(!request(engine,next,2_000_000_000),"healing playback updates do not release the active ammo cue");
        ClientAmmoSupplySound.reset();
        check(request(engine,nextHeal,2_000_000_001),"disconnect or audio reload clears healing playback ownership too");
        check(request(engine,next,2_000_000_001),"reset clears ammo independently too");
        var replacement=engine();
        check(request(replacement,heal,2_000_000_002),"replacement engine clears healing state");
        check(request(replacement,first,2_000_000_002),"replacement engine clears ammo state");
        ClientAmmoSupplySound.reset();engine.channels.clear();engine.bookkeeping=false;
        check(request(engine,heal,0),"long replacement healing audio begins");engine.channels.put(heal,handle(false));
        check(!request(engine,nextHeal,5_000_000_000L),"long resource-pack healing audio stays protected until real channel release");
        engine.channels.put(heal,handle(true));
        check(request(engine,nextHeal,5_100_000_000L),"long healing audio releases next request when playback really ends");
        ClientAmmoSupplySound.reset();engine.channels.clear();engine.bookkeeping=true;
        for(int second=0;second<6;second++) {
            var segment=sound("gd656conquest:sfx.player_heal");
            check(request(engine,segment,second*1_000_000_000L),"medical bag immediate and one-second healing segments play after prior cue ends");
            engine.channels.put(segment,handle(true));
        }
        String root="org.mods.gd656conquest.server.support.";
        check(calls(method(root+"SupportAmmo","bag"),"SupportRuntime","ammoSupplied")==1,"bag success emits exactly one shared cue request");
        check(calls(method(root+"SupportRuntime","debitAmmo"),"SupportRuntime","ammoSupplied")==1,"box weapon and consumable successes emit the shared cue request");
        check(calls(method(root+"SupportRuntime","ammoSupplied"),"ServerPlayer","m_6330_")==1,"shared ammo success cue plays only to the recipient");
        check(Arrays.stream(method(root+"SupportRuntime","ammoSupplied").instructions.toArray()).anyMatch(i->i instanceof FieldInsnNode f&&f.name.equals("lastAmmoSoundTick")),"same-server-tick successes coalesced");
        var healing=method(root+"SupportRuntime","heal");
        check(calls(healing,"SupportRuntime","medicalSupplied")==1,"all successful medical healing requests one common cue");
        MethodInsnNode healCall=null,medicalCue=null;
        for(var insn:healing.instructions)if(insn instanceof MethodInsnNode c) {
            if(c.name.equals("m_5634_"))healCall=c;
            if(c.owner.endsWith("SupportRuntime")&&c.name.equals("medicalSupplied"))medicalCue=c;
        }
        boolean guarded=false;
        for(var insn=healCall.getNext();insn!=medicalCue;insn=insn.getNext())
            if(insn instanceof JumpInsnNode jump&&jump.getOpcode()==org.objectweb.asm.Opcodes.IFLE)
                guarded=healing.instructions.indexOf(jump.label)>healing.instructions.indexOf(medicalCue);
        check(guarded,"medical sound skipped when actual healing amount is nonpositive");
        check(calls(method(root+"SupportRuntime","medicalSupplied"),"ServerPlayer","m_6330_")==1,"medical success plays one healing cue only to the recipient");
        check(Arrays.stream(method(root+"SupportRuntime","medicalSupplied").instructions.toArray()).anyMatch(i->i instanceof FieldInsnNode f&&f.name.equals("lastMedicalSoundTick")),"medical success coalesces per tick separately from ammo");
        check(calls(method(root+"SupportRuntime","pickup"),"SupportRuntime","heal")==1,"medical bag pickup immediate heal reaches common success cue");
        check(calls(method(root+"SupportRuntime","tick","()V"),"SupportRuntime","heal")==1,"medical bag subsequent healing reaches common success cue");
        var boxes=method(root+"SupportRuntime","boxes");
        check(calls(boxes,"SupportRuntime","heal")==1,"medical box healing also reaches common success cue");
        check(calls(boxes,"SupportRuntime","medicalSupplied")==0,"medical box no longer sends a duplicate cue outside common healing");
        check(Arrays.stream(method(root+"SupportRuntime","<clinit>").instructions.toArray()).anyMatch(i->i instanceof LdcInsnNode c&&"sfx.player_heal".equals(c.cst)),"medical sound uses existing healing asset");
        var passive=method("org.mods.gd656conquest.server.player.regen.PassiveRegenService","onPlayerTick");
        check(calls(passive,"ServerPlayer","m_6330_")==1,"natural regen startup audio also plays only to the recipient");
        for(var cue:List.of(passive,method(root+"SupportRuntime","ammoSupplied"),method(root+"SupportRuntime","medicalSupplied")))
            check(Arrays.stream(cue.instructions.toArray()).noneMatch(i->i instanceof MethodInsnNode c&&c.name.equals("m_6263_")),"healing and ammo cue paths never broadcast to nearby players: "+cue.name);
        var notify=method("net.minecraft.server.level.ServerPlayer","m_6330_");
        check(calls(notify,"ServerGamePacketListenerImpl","m_9829_")==1,"actual Minecraft player audio sends one sound packet to one player connection");
        check(Arrays.stream(notify.instructions.toArray()).filter(i->i instanceof FieldInsnNode f&&f.name.equals("f_8906_")).count()==1,"recipient-only API reads only the recipient's connection");
        check(Arrays.stream(notify.instructions.toArray()).anyMatch(i->i instanceof TypeInsnNode t&&t.desc.endsWith("ClientboundSoundPacket")),"recipient-only sound remains a vanilla sound packet compatible with existing playback protection");
        System.out.println("PASS real Forge ammo/healing sound event/channel deduplication and success hooks: "+checks);
    }
}
