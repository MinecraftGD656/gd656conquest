package tests;

import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.mods.gd656conquest.server.room.runtime.*;
import org.mods.gd656conquest.server.room.model.*;
import org.mods.gd656conquest.server.room.config.*;
import org.mods.gd656conquest.client.room.BoundaryPresentation;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;

public final class BoundaryTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static List<RoomPosition> polygon(int... points) {
        List<RoomPosition> result=new ArrayList<>();
        for(int i=0;i<points.length;i+=2) result.add(new RoomPosition(points[i],0,points[i+1]));
        return result;
    }
    static RoomConfigDescriptor descriptor(String id) {
        return new RoomConfigDescriptor(id,RoomConfigScope.COMMON,RoomConfigCategory.RULES,id,
            "commonConfig."+id,RoomConfigValueType.NUMBER,List.of(),List.of(),RoomConfigGroupOperationMode.NONE,null,true);
    }
    static RoomDefinition room(RoomCommonConfig common) {
        return new RoomDefinition("test","test",RoomGameplayId.DEATHMATCH,RoomLifecycleState.DISABLED,common,RoomDeathmatchConfig.defaults());
    }
    static Object field(Object object,String name)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    public static void main(String[] args)throws Exception {
        long start=1000,foot=BoundaryCountdown.tighten(null,start,10);
        check(foot==1200,"10 second infantry deadline");
        check(BoundaryCountdown.remainingTicks(BoundaryCountdown.tighten(foot,start+160,30),start+160)==40,"mount at 8s keeps 2s");
        long vehicle=BoundaryCountdown.tighten(null,start,30);
        long dismount=BoundaryCountdown.tighten(vehicle,start+200,10);
        check(BoundaryCountdown.remainingTicks(dismount,start+200)==200,"20s remaining clamps to 10s, not zero");
        check(BoundaryCountdown.tighten(dismount,start+220,30)==dismount,"remount cannot recover time");
        check(BoundaryCountdown.tighten(vehicle,start+440,10)==vehicle,"8s remaining is not lengthened to 10s");
        for(int limit:new int[]{1,2,10,30,60,3600}) {
            long deadline=BoundaryCountdown.tighten(null,start,limit);
            for(int tick=0;tick<=limit*20+1;tick++) {
                long tightened=BoundaryCountdown.tighten(deadline,start+tick,limit+10);
                check(tightened==deadline,"continuous updates cannot extend deadline");
                int remaining=BoundaryCountdown.remainingTicks(deadline,start+tick);
                check(remaining==Math.max(0,limit*20-tick),"exact remaining ticks");
                check(BoundaryCountdown.displaySeconds(remaining)==(int)Math.ceil(remaining/20D),"ceil seconds");
            }
        }
        var square=List.of(polygon(0,0,100,0,100,100,0,100));
        check(!FlightBoundaryGeometry.outside(50,50,square,0),"inside original");
        check(!FlightBoundaryGeometry.outside(100,50,square,0),"edge included");
        check(FlightBoundaryGeometry.outside(100.01,50,square,0),"zero padding restores original edge");
        check(!FlightBoundaryGeometry.outside(200,50,square,100),"buffer threshold included");
        check(FlightBoundaryGeometry.outside(200.01,50,square,100),"beyond buffer");
        check(!FlightBoundaryGeometry.outside(160,180,square,100),"rounded corner buffer");
        check(FlightBoundaryGeometry.outside(171,171,square,100),"diagonal distance uses Euclidean buffer");
        var concave=List.of(polygon(0,0,100,0,100,30,30,30,30,100,0,100));
        check(FlightBoundaryGeometry.outside(50,50,concave,10),"concave void remains outside");
        check(!FlightBoundaryGeometry.outside(50,50,concave,20),"concave nearest edge buffer");
        check(!FlightBoundaryGeometry.outside(300,50,List.of(square.get(0),polygon(250,0,350,0,350,100,250,100)),0),"separate areas");
        check(!FlightBoundaryGeometry.outside(10000,10000,List.of(),100),"missing boundary imposes no horizontal penalty");
        check(!FlightBoundaryGeometry.outside(10000,10000,null,100),"null boundary imposes no horizontal penalty");
        check(!FlightBoundaryGeometry.outside(-5,0,List.of(polygon(0,0,0,0,100,0,100,100,0,100)),5),"duplicate vertices supported");

        BoundaryPresentation presentation=new BoundaryPresentation();
        long now=10_000_000_000L;
        check(presentation.tryVoice(now),"first entry plays voice");
        presentation.advance(true,.05F);
        check(Math.abs(presentation.strength()-.5F)<.00001,"50ms fade midpoint");
        presentation.advance(false,.025F);
        check(Math.abs(presentation.strength()-.25F)<.00001,"interrupted fade remains continuous");
        check(!presentation.tryVoice(now+BoundaryPresentation.VOICE_DURATION_NANOS),"cooldown includes tail gap");
        presentation.advance(false,1);
        check(presentation.strength()==0,"filter leaves fully");
        check(!presentation.tryVoice(now+BoundaryPresentation.VOICE_COOLDOWN_NANOS-1),"return does not reset cooldown");
        check(presentation.tryVoice(now+BoundaryPresentation.VOICE_COOLDOWN_NANOS),"cooldown expires at exact time");
        for(int fps:new int[]{20,30,60,144,240}) {
            presentation.reset();
            for(int frame=0;frame<fps;frame++)presentation.advance(true,1F/fps);
            check(presentation.strength()==1,"fade completes at "+fps+"fps");
            for(int frame=0;frame<fps;frame++)presentation.advance(false,1F/fps);
            check(presentation.strength()==0,"fade out completes at "+fps+"fps");
        }

        Gson gson=new Gson();
        RoomCommonConfig migrated=gson.fromJson("{\"outsideBoundaryDeathSeconds\":10,\"aircraftOutsideBoundaryCleanupSeconds\":30}",RoomCommonConfig.class);
        check(migrated.flightVehicleOutsideBoundaryDeathSeconds()==30,"old vehicle time migrates to flight");
        check(migrated.flightBoundaryPaddingBlocks()==100D,"old room buffer defaults to 100 blocks");
        RoomCommonConfig explicit=gson.fromJson("{\"aircraftOutsideBoundaryCleanupSeconds\":30,\"flightVehicleOutsideBoundaryDeathSeconds\":0,\"flightBoundaryPaddingBlocks\":0}",RoomCommonConfig.class);
        check(explicit.flightVehicleOutsideBoundaryDeathSeconds()==0&&explicit.flightBoundaryPaddingBlocks()==0,"explicit zero differs from missing config");
        check(gson.fromJson(gson.toJson(migrated),RoomCommonConfig.class).equals(migrated),"config JSON roundtrip");
        RoomDefinition configured=RoomDefinitionScalarConfigEditor.apply(room(migrated),descriptor("flightVehicleOutsideBoundaryDeathSeconds"),"60",null);
        configured=RoomDefinitionScalarConfigEditor.apply(configured,descriptor("flightBoundaryPaddingBlocks"),"125.5",null);
        check(configured.commonConfig().flightVehicleOutsideBoundaryDeathSeconds()==60,"native scalar editor sets flight seconds");
        check(configured.commonConfig().flightBoundaryPaddingBlocks()==125.5,"native scalar editor handles double buffer");
        configured=RoomDefinitionScalarConfigEditor.apply(configured,descriptor("outsideBoundaryDeathSeconds"),"15",null);
        check(configured.commonConfig().flightVehicleOutsideBoundaryDeathSeconds()==60&&configured.commonConfig().flightBoundaryPaddingBlocks()==125.5,"unrelated edit preserves additions");
        for(String invalid:new String[]{"-1","NaN","Infinity","abc"}) {
            try {BoundaryConfigNumbers.parse("flightBoundaryPaddingBlocks",invalid);throw new AssertionError("invalid buffer accepted: "+invalid);}
            catch(IllegalArgumentException expected){check(true,"invalid buffer rejected");}
        }
        // Invoke the actual repository normalization method without creating a server or writing files.
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        Class<?> repository=Class.forName("org.mods.gd656conquest.server.data.roomdefinition.RoomDefinitionRepository");
        Method normalize=repository.getDeclaredMethod("normalizeDefinition",RoomDefinition.class);normalize.setAccessible(true);
        RoomDefinition normalized=(RoomDefinition)normalize.invoke(unsafe.allocateInstance(repository),configured);
        check(normalized.commonConfig().flightVehicleOutsideBoundaryDeathSeconds()==60&&normalized.commonConfig().flightBoundaryPaddingBlocks()==125.5,"real repository reload preserves both values");
        Class<?> templates=Class.forName("org.mods.gd656conquest.server.room.entry.RoomAdminConfigTemplateService");
        Method copy=templates.getDeclaredMethod("buildCopiedDefinition",RoomDefinition.class,RoomDefinition.class);copy.setAccessible(true);
        RoomDefinition copied=(RoomDefinition)copy.invoke(unsafe.allocateInstance(templates),configured,room(migrated));
        check(copied.commonConfig().flightVehicleOutsideBoundaryDeathSeconds()==60&&copied.commonConfig().flightBoundaryPaddingBlocks()==125.5,"real template copy transfers new values");

        Method group=RoomDefinitionGroupConfigEditor.class.getDeclaredMethod("replaceCommonStringGroup",RoomDefinition.class,String.class,List.class);
        group.setAccessible(true);
        RoomDefinition grouped=(RoomDefinition)group.invoke(null,configured,"supportcampaherokits",List.of("kit"));
        check(grouped.commonConfig().flightVehicleOutsideBoundaryDeathSeconds()==60&&grouped.commonConfig().flightBoundaryPaddingBlocks()==125.5,"real group editor preserves boundary fields");
        RoomDefinition zero=RoomDefinitionScalarConfigEditor.apply(configured,descriptor("flightVehicleOutsideBoundaryDeathSeconds"),"0",null);
        check(zero.commonConfig().flightVehicleOutsideBoundaryDeathSeconds()==0,"explicit zero flight duration editable");
        Method validate=org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidator.class.getDeclaredMethod("validateCommon",org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult.class,RoomDefinition.class);
        validate.setAccessible(true);
        var valid=new org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult();
        validate.invoke(null,valid,configured);
        check(!valid.getMissingConfigIds().contains("flightVehicleOutsideBoundaryDeathSeconds"),"positive flight duration accepted by actual start validator");
        var disabled=new org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult();
        validate.invoke(null,disabled,zero);
        check(disabled.getMissingConfigIds().contains("flightVehicleOutsideBoundaryDeathSeconds"),"source start validator rejects zero flight duration despite runtime disable semantics");
        check(BoundaryConfigNumbers.parse("respawnDelaySeconds","bad")==null,"old integer parsing stays native");
        var assault=org.mods.gd656conquest.server.loadout.domain.PlayerClassType.ASSAULT;
        var engineer=org.mods.gd656conquest.server.loadout.domain.PlayerClassType.ENGINEER;
        check(assault.getDisplayName().equals("医护兵")&&engineer.getDisplayName().equals("突击兵"),"migrated class labels");
        check(org.mods.gd656conquest.server.loadout.domain.PlayerClassType.fromInput("医护兵")==null,"source display rename does not change aliases");
        check(org.mods.gd656conquest.server.loadout.domain.PlayerClassType.fromInput("突击兵")==assault,"original Chinese assault alias retained");
        check(org.mods.gd656conquest.server.loadout.domain.PlayerClassType.fromInput("工程兵")==engineer,"old engineer alias retained");
        check(org.mods.gd656conquest.server.loadout.domain.PlayerClassType.fromInput("assault")==assault,"internal class key retained");

        for(int ticks:new int[]{1,5,19,20,40,600,72000})for(int kind=0;kind<3;kind++) {
            FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {
                RoomDeathFlowPacket.encode(RoomDeathFlowPacket.boundaryUpdate(ticks,kind),buffer);
                RoomDeathFlowPacket packet=RoomDeathFlowPacket.decode(buffer);
                check(field(packet,"deployCountdownTicks").equals(ticks)&&field(packet,"killerEntityId").equals(kind),"boundary snapshot packet roundtrip");
                check(buffer.readableBytes()==0,"boundary snapshot consumes payload");
            }finally{buffer.release();}
        }
        System.out.println("PASS boundary timing/geometry/migration/editor/reload/template/voice/fade/packet: "+checks);
    }
}
