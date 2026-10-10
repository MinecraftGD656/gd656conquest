package tests;

import org.mods.gd656conquest.client.room.DeathFilterTransition;
import org.mods.gd656conquest.client.room.ReviveTransition;

public final class ReviveTransitionTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) {
        for(int fps:new int[]{20,30,60,144,240}) {
            DeathFilterTransition color=new DeathFilterTransition();
            color.advance(true,2);color.beginReviveRecovery();color.recoverRevive(0);
            check(color.strength()==.96F,"Revive color begins from the actual death strength");
            double previousMove=0,previousStep=Double.POSITIVE_INFINITY;
            float previousPulse=1,previousColor=color.strength();
            for(int frame=1;frame<=Math.ceil(fps*ReviveTransition.CAMERA_SECONDS);frame++) {
                double time=Math.min(frame/(double)fps,ReviveTransition.CAMERA_SECONDS);
                double move=ReviveTransition.cameraProgress(time),step=move-previousMove;
                float pulse=ReviveTransition.pulse(time);color.recoverRevive(time);
                check(move>=previousMove && move<=1 && step<=previousStep+1e-9,"Camera slows monotonically at "+fps+" FPS");
                check(pulse>=0 && pulse<=previousPulse,"Pulse fades monotonically without a second flash");
                check(color.strength()>=0 && color.strength()<=previousColor,"Color recovery is monotonic");
                if(time>=.08)check(color.strength()==0,"Desaturation gone by 80 ms");
                if(time>=ReviveTransition.CAMERA_SECONDS)check(pulse>0,"Exposure continues after camera return");
                previousMove=move;previousStep=step;previousPulse=pulse;previousColor=color.strength();
            }
            check(previousMove==1,"Camera finishes at the player eye");
            previousPulse=1;
            for(int frame=1;frame<=Math.ceil(fps*ReviveTransition.PULSE_SECONDS);frame++) {
                double time=Math.min(frame/(double)fps,ReviveTransition.PULSE_SECONDS);
                float pulse=ReviveTransition.pulse(time);
                check(pulse>=0 && pulse<=previousPulse,"Pulse stays bounded and monotonic through the full 1400 ms timeline");
                if(time<=.10)check(pulse==1,"Peak remains visible for at least two frames at 20 FPS");
                if(time>=ReviveTransition.PULSE_SECONDS)check(pulse==0,"Exposure ends at 1400 ms");
                else check(pulse>0,"Exposure remains active until its own endpoint");
                previousPulse=pulse;
            }
            check(color.advance(false,.01F)==0,"No color rebound after handoff");
            color.advance(true,.15F);float interrupted=color.strength();
            color.beginReviveRecovery();color.recoverRevive(0);
            check(color.strength()==interrupted,"Early revive preserves continuity");
            color.recoverRevive(.025);float current=color.strength();
            check(color.advance(false,0)==current,"Canceled revive continues from the displayed strength");
            check(color.advance(true,0)==current,"Re-downing continues from the displayed strength");
        }
        check(ReviveTransition.pulse(0)==1 && ReviveTransition.pulse(.10)==1,"Immediate peak held for 100 ms");
        check(ReviveTransition.PULSE_SECONDS==1.4 && ReviveTransition.CAMERA_SECONDS==.28,"1400 ms filter and unchanged 280 ms camera");
        check(ReviveTransition.pulse(.28)>.94 && ReviveTransition.pulse(.5)>.77,"Strong exposure survives camera handoff and first half second");
        check(Math.abs(ReviveTransition.pulse(.75)-.5)<1e-6,"Gradual mid-tail instead of rapid initial decay");
        check(ReviveTransition.pulse(1.1)>.13 && ReviveTransition.pulse(1.399)>0 && ReviveTransition.pulse(1.4)==0,"Longer fading tail and finite end");
        check(ReviveTransition.pulse(-.1)==0 && ReviveTransition.pulse(Double.POSITIVE_INFINITY)==0,"Out-of-range times stay inert");
        check(ReviveTransition.pulse(Double.NaN)==0,"Invalid pulse time stays inert");
        check(ReviveTransition.cameraProgress(.14)==.875,"Most camera motion occurs under the flash");
        System.out.println("PASS revival timing, low-FPS peak and continuity: "+checks);
    }
}
