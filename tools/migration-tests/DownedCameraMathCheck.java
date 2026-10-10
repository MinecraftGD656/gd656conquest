package tests;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.client.room.ReviveTransition;
import static org.mods.gd656conquest.client.room.DownedCameraMath.*;
/** Runs source regression scenarios against the current production JAR, with actual MC vector/box types. */
public final class DownedCameraMathCheck {
    static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    static boolean sameFrame(Frame a,Frame b){return a.position().m_82557_(b.position())<1e-10
        &&Math.abs(wrap(a.yaw()-b.yaw()))<1e-5&&Math.abs(a.pitch()-b.pitch())<1e-5&&Math.abs(a.fov()-b.fov())<1e-5;}
    static void geometry(){
        Vec3 body=new Vec3(0,64.4,0);AABB bounds=new AABB(-.8,64,-1.1,.8,64.8,1.1);
        for(double aspect:new double[]{4.0/3,16.0/9,21.0/9})for(Vec3 target:List.of(new Vec3(0,65,40),new Vec3(2,64,1),new Vec3(0,74,-20))){
            Shot shot=solve(body,bounds,target,0,75,aspect,1,(from,to)->to);
            check(penalty(shot.frame(),bounds,target,aspect)<.01,"two-target framing in open space "+aspect+" "+target);
            check(shot.frame().fov()<=95&&length(sub(shot.frame().position(),body))<6.5,"bounded distance and FOV");
        }
        Shot blocked=solve(body,bounds,new Vec3(0,65,40),0,75,16.0/9,1,(from,to)->
            to.f_82479_<0?lerp(from,to,.1):to);
        check(blocked.side()==-1,"blocked preferred side changes only when materially worse");
        Frame a=new Frame(body,179,0,75),b=new Frame(body,-179,0,75);
        check(Math.abs(wrap(blend(a,b,.5).yaw()-179))<=2,"yaw interpolation takes short path across 180");
        Frame s30=a,s120=a;
        for(int i=0;i<30;i++)s30=approach(s30,b,1.0/30,5);
        for(int i=0;i<120;i++)s120=approach(s120,b,1.0/120,5);
        check(Math.abs(wrap(s30.yaw()-s120.yaw()))<.02,"tracking smoothing independent of frame rate");
        check(Double.isFinite(solve(body,bounds,body,0,75,1,1,(from,to)->to).score()),"coincident killer/body stays finite");
        for(double aspect:new double[]{4.0/3,16.0/9,21.0/9}) {
            Vec3 target=new Vec3(0,65.5,40);
            Frame focus=solve(body,bounds,target,0,75,aspect,1,(from,to)->to).frame();
            Projection enemy=project(focus,target,aspect),foreground=project(focus,body,aspect);
            check(Math.abs(enemy.x())<.2&&Math.abs(enemy.y())<.25,"killer is the visual subject "+aspect);
            check(foreground.y()<-.25,"body is lower foreground rather than frame center "+aspect);
        }
        Frame start=new Frame(new Vec3(0,65.6,0),0,0,75);
        Vec3 groundEye=new Vec3(0,64.7,-.18),away=new Vec3(0,0,-1);
        Frame contact=impact(start,groundEye,away,IMPACT_SECONDS);
        check(sameFrame(impact(start,groundEye,away,0),start),"impact starts exactly at existing eye/angles/FOV");
        Frame vertical=new Frame(start.position(),0,89,75);
        check(sameFrame(impact(vertical,groundEye,away,0),vertical),"steep entry pitch is also preserved at first frame");
        check(impact(start,groundEye,away,.09).pitch()<-4,"hit kicks view before downward fall");
        check(contact.position().f_82480_<start.position().f_82480_-.7&&contact.pitch()>15,"head falls before camera extracts");
        check(Math.abs(impactRoll(start,new Vec3(-1,0,0),.09)+impactRoll(start,new Vec3(1,0,0),.09))<.01,"opposite attack sides reverse impact roll");
        Frame destination=solve(body,bounds,new Vec3(0,65.5,40),0,75,16.0/9,1,(f,t)->t).frame();
        Frame revealed=reveal(contact,destination,away,REVEAL_SECONDS);
        check(length(sub(revealed.position(),destination.position()))<1e-6&&Math.abs(wrap(revealed.yaw()-destination.yaw()))<1e-4,"curved reveal ends exactly at composed view");
        check(sameFrame(reveal(contact,destination,away,0),contact),"reveal starts continuously from impact contact");
        Frame mid=reveal(contact,destination,away,REVEAL_SECONDS*.5);
        Vec3 line=unit(sub(destination.position(),contact.position())),delta=sub(mid.position(),contact.position());
        double curvature=length(sub(delta,scale(line,dot(delta,line))));
        check(curvature>.1,"reveal is a curved path rather than straight extraction: "+curvature+" "+destination+" "+mid);
        check(Math.abs(wrap(reveal(contact,destination,away,REVEAL_SECONDS*.05).yaw()-contact.yaw()))<.01,"attention stays with hit before turning to killer");
        safeArea();
    }
    static Vec3 screenPoint(Frame view,double x,double y,double depth,double aspect) {
        double yaw=Math.toRadians(view.yaw()),pitch=Math.toRadians(view.pitch()),tan=Math.tan(Math.toRadians(view.fov())*.5);
        Vec3 right=new Vec3(-Math.cos(yaw),0,-Math.sin(yaw));
        Vec3 up=new Vec3(-Math.sin(yaw)*Math.sin(pitch),Math.cos(pitch),Math.cos(yaw)*Math.sin(pitch));
        return add(view.position(),add(scale(forward(view.yaw(),view.pitch()),depth),
            add(scale(right,x*depth*tan*aspect),scale(up,y*depth*tan))));
    }
    static void safeArea() {
        for(double aspect:new double[]{4.0/3,16.0/9,21.0/9}) {
            Frame view=new Frame(new Vec3(0,65,0),0,0,75);
            Vec3 body=new Vec3(0,64.3,5);AABB bounds=new AABB(-.5,64,4.5,.5,64.6,5.5);
            Vec3 center=screenPoint(view,.10,.10,40,aspect),safe=screenPoint(view,.45,.10,40,aspect);
            Vec3 edge=screenPoint(view,.90,.10,40,aspect),vertical=screenPoint(view,.10,.80,40,aspect);
            Frame quiet=trackWithinScreen(view,compose(view.position(),body,bounds,center,75,aspect),bounds,center,aspect,.05,0);
            check(Math.abs(wrap(quiet.yaw()-view.yaw()))<.001&&Math.abs(quiet.pitch()-view.pitch())<.001,"comfortable zone holds aim "+aspect);
            check(edgeFollowStrength(view,safe,aspect)<.15&&edgeFollowStrength(view,edge,aspect)>.99,"horizontal edge is measured continuously in screen space "+aspect);
            check(edgeFollowStrength(view,vertical,aspect)>.99,"top/bottom edges also increase recovery "+aspect);
            Frame gentle=trackWithinScreen(view,compose(view.position(),body,bounds,safe,75,aspect),bounds,safe,aspect,.05,edgeFollowStrength(view,safe,aspect));
            Frame urgent=trackWithinScreen(view,compose(view.position(),body,bounds,edge,75,aspect),bounds,edge,aspect,.05,1);
            check(Math.abs(gentle.yaw())<.5&&Math.abs(urgent.yaw())>Math.abs(gentle.yaw())*5,"edge correction is substantially stronger than safe-area drift "+aspect);
            double early=edgeFollowStrength(view,screenPoint(view,.55,.10,40,aspect),aspect);
            double middle=edgeFollowStrength(view,screenPoint(view,.65,.10,40,aspect),aspect);
            double late=edgeFollowStrength(view,screenPoint(view,.78,.10,40,aspect),aspect);
            check(early>.2&&early<middle&&middle<late&&late>.9,"acceleration begins well before leaving the screen "+aspect);
            check(Math.abs(edgeFollowStrength(view,screenPoint(view,.7801,.10,40,aspect),aspect)
                -edgeFollowStrength(view,screenPoint(view,.7799,.10,40,aspect),aspect))<.002,"old switch boundary has no strength discontinuity "+aspect);
            Frame following=view;
            double strength=0;
            for(int i=0;i<120;i++) {
                strength+=(edgeFollowStrength(following,edge,aspect)-strength)*(1-Math.exp(-14.0/60));
                following=trackWithinScreen(following,compose(view.position(),body,bounds,edge,75,aspect),bounds,edge,aspect,1.0/60,strength);
            }
            Projection recovered=project(following,edge,aspect);
            check(recovered.depth()>0&&Math.abs(recovered.x())<.60&&Math.abs(recovered.x())>.30,"edge recovery leaves subject off-center inside safe area "+aspect);
            check(strength<.4,"strong following fades continuously after returning safely "+aspect);
            check(length(sub(following.position(),view.position()))<1e-6,"screen correction never moves the camera around the body "+aspect);
        }
        streamingMotion();
        foregroundThresholds();
    }
    static void foregroundThresholds() {
        Frame view=new Frame(new Vec3(0,65,0),0,0,75);
        for(double aspect:new double[]{4.0/3,16.0/9,21.0/9}) {
            Vec3 low=screenPoint(view,.59,.10,10,aspect),middle=screenPoint(view,.74,.10,10,aspect),edge=screenPoint(view,.87,.10,10,aspect);
            double quiet=foregroundPressure(view,new AABB(low.f_82479_,low.f_82480_,low.f_82481_,low.f_82479_,low.f_82480_,low.f_82481_),aspect);
            double warning=foregroundPressure(view,new AABB(middle.f_82479_,middle.f_82480_,middle.f_82481_,middle.f_82479_,middle.f_82480_,middle.f_82481_),aspect);
            double danger=foregroundPressure(view,new AABB(edge.f_82479_,edge.f_82480_,edge.f_82481_,edge.f_82479_,edge.f_82480_,edge.f_82481_),aspect);
            check(quiet==0&&warning>.45&&danger>.99,"foreground response ramps up before body leaves viewport "+aspect);
        }
    }
    static void streamingMotion() {
        // Uniform walking delivered in 200 ms packets, including a change of direction and stop.
        Vec3 origin=new Vec3(0,65,20);PositionSpring motion=new PositionSpring(origin);
        Vec3 previousSample=origin,latest=origin,velocity=Vec3.f_82478_;
        double oldVelocity=0,maxAcceleration=0,ripple=0,oldRipple=0;Vec3 legacy=origin;
        int count=0;boolean preserved=true,finite=true;
        for(int i=0;i<480;i++) {
            double t=i/120.0;
            if(i%24==0) {
                Vec3 next=new Vec3(Math.min(t,3)*4,65,20);
                if(i>0)velocity=lerp(velocity,scale(sub(next,previousSample),5),.6);
                Vec3 carried=motion.velocity();latest=next;previousSample=next;
                motion.advance(latest,0);
                preserved&=length(sub(motion.velocity(),carried))<1e-8;
            }
            double age=(i%24)/120.0;
            Vec3 next=motion.advance(predictSubject(latest,velocity,age),1.0/120);
            Vec3 before=legacy;legacy=lerp(legacy,latest,1-Math.exp(-8.0/120));
            double legacySpeed=(legacy.f_82479_-before.f_82479_)*120;
            if(i>=120&&i<336){ripple+=Math.pow(motion.velocity().f_82479_-4,2);oldRipple+=Math.pow(legacySpeed-4,2);count++;}
            finite&=Double.isFinite(next.f_82479_)&&Double.isFinite(motion.velocity().f_82479_);
            double requested= i<180?20:i<300?75:0;
            double newVelocity=followVelocity(oldVelocity,requested,1.0/120,260);
            maxAcceleration=Math.max(maxAcceleration,Math.abs(newVelocity-oldVelocity)*120);
            oldVelocity=newVelocity;
        }
        check(preserved,"all streamed position updates preserve velocity at packet boundaries");
        check(finite,"entire walking/stopping stream stays finite");
        check(ripple/count<oldRipple/count*.55,"velocity-preserving reconstruction reduces 200 ms packet speed pulses: "+ripple/oldRipple);
        System.out.printf(java.util.Locale.ROOT,"PASS 200 ms walking sample speed-ripple ratio: %.3f%n",ripple/oldRipple);
        check(maxAcceleration<=260.0001,"camera angular acceleration is bounded across target changes");
        check(Math.abs(oldVelocity)<.01,"camera slows to rest without starting another animation");
        check(length(sub(predictSubject(origin,new Vec3(20,0,0),.2),origin))<=.65001,"short prediction stays bounded");
        check(length(sub(predictSubject(origin,new Vec3(20,0,0),.6),origin))<1e-6,"stale position does not keep extrapolating");
        PositionSpring slow=new PositionSpring(origin),fast=new PositionSpring(origin);Vec3 goal=new Vec3(4,65,20);
        for(int i=0;i<30;i++)slow.advance(goal,1.0/30);
        for(int i=0;i<120;i++)fast.advance(goal,1.0/120);
        check(length(sub(slow.position(),fast.position()))<1e-7&&length(sub(slow.velocity(),fast.velocity()))<1e-7,"position and velocity reconstruction agrees at 30 and 120 FPS");
    }
    public static void main(String[] args) {
        geometry();
        for(int fps:new int[]{20,30,60,144,240}) {
            double previous=0,step=Double.POSITIVE_INFINITY;
            for(int frame=1;frame<=Math.ceil(fps*ReviveTransition.CAMERA_SECONDS);frame++) {
                double t=Math.min(frame/(double)fps,ReviveTransition.CAMERA_SECONDS);
                double progress=ReviveTransition.cameraProgress(t),delta=progress-previous;
                check(progress>=previous && progress<=1 && delta<=step+1e-9,"Revive return slows monotonically across frame rates");
                previous=progress;step=delta;
            }
            check(previous==1,"Return ends at exact native eye");
        }
        check(ReviveTransition.CAMERA_SECONDS==.28,"Preserve source camera return duration");
        check(ReviveTransition.cameraProgress(.14)==.875,"Shortest-angle return uses the source curve");
        check(ReviveTransition.cameraProgress(-1)==0 && ReviveTransition.cameraProgress(Double.NaN)==0,"Invalid time remains inert");
        System.out.println("PASS production camera composition/motion/return: "+checks);
    }
}
