package org.mods.gd656conquest.client.loadout.screen;

import com.google.gson.Gson;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.common.match.BriefingTimeline;
import org.mods.gd656conquest.common.match.BriefingTimeline.*;
import org.mods.gd656conquest.common.match.BattalionSequence.Phase;
import org.mods.gd656conquest.server.room.model.RoomBattalionConfig;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.*;
import org.mods.gd656conquest.client.room.BriefingTrackPlayer;
import org.mods.gd656conquest.client.gui.elements.ChoiceListDialog;
import org.mods.gd656conquest.client.gui.elements.entries.FixedChoiceConfigEntry.Choice;

/** Stage-colored editing, two independent team tracks, and shared runtime audio/subtitle preview. */
public final class BriefingTimelineScreen extends Screen {
    private static final Gson GSON=new Gson();
    private final Screen parent;
    private final String roomId;
    private RoomBattalionConfig config;
    private String revision;
    private final Map<String,Scene> scenes=new LinkedHashMap<>();
    private List<String> keys;
    private int sceneIndex, selected=-1, lane, dragMode;
    private boolean attacker=true, playing, dirty;
    private double playhead, viewStart, visibleTicks, dragStartMouse;
    private Clip dragOriginal;
    private String message="滚轮缩放 · Shift+滚轮平移 · 拖动素材及其边缘";
    private EditBox content, start, duration, volume;
    private Button sceneButton,teamButton,playButton;
    private ChoiceListDialog soundsDialog;
    private final BriefingTrackPlayer preview=new BriefingTrackPlayer();

    public BriefingTimelineScreen(Screen parent,BriefingTimelineSnapshotPacket snapshot) {
        super(Component.literal("战况演出时间轴"));this.parent=parent;this.roomId=snapshot.roomId();accept(snapshot);
    }
    public static void receive(BriefingTimelineSnapshotPacket packet) {
        var mc=Minecraft.getInstance();
        if(packet.open()) mc.setScreen(new BriefingTimelineScreen(mc.screen,packet));
        else if(mc.screen instanceof BriefingTimelineScreen screen && screen.roomId.equals(packet.roomId())) screen.accept(packet);
        else if(!packet.message().isEmpty() && mc.player!=null) mc.player.sendSystemMessage(Component.literal(packet.message()));
    }
    private void accept(BriefingTimelineSnapshotPacket packet) {
        message=packet.message();
        if(packet.json().isEmpty()) return;
        config=GSON.fromJson(packet.json(),RoomBattalionConfig.class);revision=packet.revision();
        scenes.clear();for(var scene:config.timelines())scenes.put(scene.key(),scene);
        keys=BriefingTimeline.sceneKeys(config.count());sceneIndex=Math.min(sceneIndex,keys.size()-1);dirty=false;
        visibleTicks=length();viewStart=0;selected=-1;pause();
        if(minecraft!=null && content!=null) updateFields();
    }
    private String key(){return keys.get(sceneIndex);}
    private int seconds(){return config.briefingSeconds();}
    private int length(){return BriefingTimeline.length(key(),seconds());}
    private Track track(){Scene scene=scenes.get(key());return scene==null?Track.empty():attacker?scene.attacker():scene.defender();}
    private List<Clip> clips(){return lane==0?track().subtitles():track().sounds();}
    private void store(List<Clip> changed){
        Track old=track();Track next=lane==0?new Track(changed,old.sounds()):new Track(old.subtitles(),changed);
        BriefingTimeline.validateTrack(next,key(),seconds());
        if(next.equals(old))return;
        Scene scene=scenes.getOrDefault(key(),new Scene(key(),Track.empty(),Track.empty()));
        scenes.put(key(),new Scene(key(),attacker?next:scene.attacker(),attacker?scene.defender():next));dirty=true;refreshPreview(false);
    }
    @Override protected void init(){
        sceneButton=button(12,24,125,"场景",()->{
            if(!applyFields())return;pause();sceneIndex=(sceneIndex+1)%keys.size();selected=-1;playhead=0;viewStart=0;visibleTicks=length();updateFields();
        });
        teamButton=button(143,24,65,"阵营",()->{if(!applyFields())return;pause();attacker=!attacker;selected=-1;updateFields();});
        playButton=button(214,24,45,"播放",()->{
            if(playing)pause();else{if(!applyFields())return;if(playhead>=length())playhead=0;refreshPreview(true);playing=true;}
        });
        button(265,24,43,"停止",()->{pause();playhead=0;refreshPreview(false);});
        int formY=height-89;
        content=field(12,formY,width-222,1024);start=field(width-204,formY,57,12);
        duration=field(width-141,formY,57,12);volume=field(width-78,formY,66,8);
        button(12,height-65,65,"应用修改",this::applyFields);
        button(83,height-65,46,"删除",()->{if(selected>=0){pause();var list=new ArrayList<>(clips());list.remove(selected);store(list);selected=-1;updateFields();}});
        button(135,height-65,73,"插入字幕",()->insert(0));
        button(214,height-65,94,"选择声音",this::chooseSound);
        button(12,height-24,64,"返回",this::onClose);
        button(width-88,height-24,76,"保存全部",()->{
            try {
                if(!applyFields())return;pause(); var result=new RoomBattalionConfig(config.count(),config.briefingViewPosition(),seconds(),config.preparationSeconds(),new ArrayList<>(scenes.values()),config.settlementTiming());
                String json=GSON.toJson(result);if(json.length()>262144)throw new IllegalArgumentException("时间轴配置过大");
                NetworkManager.getChannel().sendToServer(new BriefingTimelineEditPacket(roomId,true,revision,json));message="正在保存…";
            }catch(RuntimeException error){message=error.getMessage();}
        });
        soundsDialog=new ChoiceListDialog(minecraft);updateFields();
    }
    private Button button(int x,int y,int width,String text,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,width,18).build());}
    private EditBox field(int x,int y,int width,int max){var field=new EditBox(font,x,y,Math.max(30,width),18,Component.empty());field.setMaxLength(max);return addRenderableWidget(field);}
    private void updateFields(){
        if(content==null)return;
        sceneButton.setMessage(Component.literal(BriefingTimeline.sceneLabel(key())));teamButton.setMessage(Component.literal(attacker?"进攻方":"防守方"));
        Clip clip=selected>=0&&selected<clips().size()?clips().get(selected):null;
        content.setValue(clip==null?"":clip.value().replace("\n","\\n"));start.setValue(clip==null?"":format(clip.start(seconds())));
        duration.setValue(clip==null?"":format(clip.durationTicks()));volume.setValue(clip==null?"1":Float.toString(clip.volume()));
        refreshPreview(false);
    }
    private static String format(double ticks){return String.format(java.util.Locale.ROOT,"%.2f",ticks/20);}
    private Clip at(int from,int duration,String value,float volume){
        Phase phase=BriefingTimeline.phaseAt(from,seconds());return new Clip(phase,from-BriefingTimeline.stageStart(phase,seconds()),duration,value,volume);
    }
    private boolean applyFields(){
        if(selected<0)return true;
        try{
            double from=Double.parseDouble(start.getValue()), span=Double.parseDouble(duration.getValue());
            if(!Double.isFinite(from)||!Double.isFinite(span)||from<0||span<=0||from>length()/20.0||span>length()/20.0)throw new IllegalArgumentException("时间必须位于时间轴内");
            var clip=at((int)Math.round(from*20),(int)Math.round(span*20),content.getValue().replace("\\n","\n"),Float.parseFloat(volume.getValue()));
            var list=new ArrayList<>(clips());list.set(selected,clip);pause();store(list);updateFields();message="已应用，点击保存全部写入房间";return true;
        }catch(RuntimeException error){message=error.getMessage();return false;}
    }
    private void insert(int targetLane){insert(targetLane,targetLane==0?"新字幕":"minecraft:entity.experience_orb.pickup");}
    private void insert(int targetLane,String value){
        if(!applyFields())return;pause();lane=targetLane;selected=-1;
        int from=(int)Math.floor(playhead),span=Math.min(60,length()-from);
        if(lane==0){
            while(from<length() && span>0){
                final int f=from; var overlapping=track().subtitles().stream().filter(c->c.start(seconds())<=f&&c.end(seconds())>f).findFirst().orElse(null);
                if(overlapping!=null){from=overlapping.end(seconds());span=Math.min(60,length()-from);continue;}
                int next=length();for(var c:track().subtitles())if(c.start(seconds())>from)next=Math.min(next,c.start(seconds()));span=Math.min(span,next-from);break;
            }
        }
        if(span<=0){message="当前位置没有可用空间";return;}
        try{var list=new ArrayList<>(clips());list.add(at(from,span,value,1));store(list);selected=list.size()-1;updateFields();}
        catch(RuntimeException error){message=error.getMessage();}
    }
    private void chooseSound(){
        if(!applyFields())return;pause();var choices=minecraft.getSoundManager().getAvailableSounds().stream().sorted(Comparator.comparing(Object::toString))
            .map(id->new Choice(id.toString(),id.toString())).toList();
        soundsDialog.show("","选择资源包声音",choices,id->insert(1,id),()->{});
    }
    private void pause(){playing=false;preview.stopSounds();}
    private void refreshPreview(boolean run){int remaining=key().startsWith("remaining_")?Integer.parseInt(key().substring(10)):key().equals("last")?1:0;
        var sample=Map.of("当前营数",Integer.toString(Math.max(1,config.count()-remaining)),"总营数",Integer.toString(config.count()),
            "剩余营数",Integer.toString(remaining),"本营攻占区域数","1","累计攻占区域数","2","本营作战秒数","120","结束原因","预览战报");
        preview.load(track().resolve(sample),seconds(),playhead);preview.position(playhead,run);}
    @Override public void tick(){
        for(var box:List.of(content,start,duration,volume))box.tick();
        if(playing){playhead=Math.min(length(),playhead+1);preview.position(playhead,playhead<length());if(playhead>=length())pause();}
        playButton.setMessage(Component.literal(playing?"暂停":"播放"));
    }
    private double tickAt(double x){return Math.max(0,Math.min(length(),viewStart+(x-12)/(width-24)*visibleTicks));}
    private int xAt(double tick){return (int)Math.round(12+(tick-viewStart)/visibleTicks*(width-24));}
    @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial){
        renderBackground(gui);gui.drawCenteredString(font,"战况时间轴 · "+roomId+(dirty?" *":""),width/2,7,0xFFFFFF);
        int[] colors={0xFF526076,0xFF455E8E,0xFF3A766B,0xFF765E83,0xFF846A3E};
        gui.enableScissor(12,52,width-12,140);
        int index=0;
        for(Phase phase:BriefingTimeline.STAGES){
            int from=BriefingTimeline.stageStart(phase,seconds()),to=Math.min(length(),from+BriefingTimeline.stageDuration(phase,seconds()));
            if(from>=length())break;
            int left=xAt(from),right=xAt(to);gui.fill(left,52,right,69,colors[index++]);
            if(right-left>font.width(BriefingTimeline.stageLabel(phase,key()))+4)gui.drawCenteredString(font,BriefingTimeline.stageLabel(phase,key()),(left+right)/2,56,0xFFFFFF);
            gui.fill(left,70,left+1,140,0xFF82909C);
        }
        double step=Math.max(20,Math.ceil(visibleTicks/140.0)*20);
        for(double t=Math.ceil(viewStart/step)*step;t<=Math.min(length(),viewStart+visibleTicks);t+=step)gui.drawString(font,format(t),xAt(t),73,0xBBBBBB);
        renderLane(gui,track().subtitles(),0,90,0xFF427EA1);renderLane(gui,track().sounds(),1,117,0xFF8F6745);
        int cursor=xAt(playhead);gui.fill(cursor,52,cursor+1,140,0xFFFFDD55);gui.disableScissor();
        gui.drawString(font,"字幕",15,92,0xFFFFFF);gui.drawString(font,"声音",15,119,0xFFFFFF);
        gui.drawString(font,"正文 / 声音 ID（换行使用 \\n）",12,height-102,0xBBBBBB);
        gui.drawString(font,"开始秒",width-204,height-102,0xBBBBBB);gui.drawString(font,"持续秒",width-141,height-102,0xBBBBBB);gui.drawString(font,"音量 0–1",width-78,height-102,0xBBBBBB);
        super.render(gui,mouseX,mouseY,partial);
        gui.enableScissor(12,height-45,width-12,height-27);preview.render(gui,width/2,height-44,width-30);gui.disableScissor();
        String status=format(playhead)+" / "+format(length())+" 秒 · "+message;
        gui.drawString(font,font.plainSubstrByWidth(status,Math.max(40,width-168)),82,height-19,0xFFE1CB89);
        if(soundsDialog.isVisible())soundsDialog.render(gui,mouseX,mouseY,partial);
        else if(mouseX>=82 && mouseX<width-90 && mouseY>=height-24 && !message.isEmpty())
            gui.renderTooltip(font,font.split(Component.literal(message),Math.max(80,width-40)),mouseX,mouseY);
    }
    private void renderLane(GuiGraphics gui,List<Clip> clips,int which,int y,int color){
        gui.fill(12,y,width-12,y+22,0xFF202A34);
        for(int i=0;i<clips.size();i++){
            Clip clip=clips.get(i);int left=xAt(clip.start(seconds())),right=xAt(clip.end(seconds()));
            gui.fill(left,y+2,Math.max(left+3,right),y+20,which==lane&&selected==i?0xFFBDA754:color);
            gui.drawString(font,font.plainSubstrByWidth(clip.value().replace("\n"," "),Math.max(0,right-left-6)),left+3,y+7,0xFFFFFF);
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(soundsDialog.isVisible())return soundsDialog.mouseClicked(x,y,button);
        if(super.mouseClicked(x,y,button))return true;
        if(button==0 && x>=12 && x<=width-12 && y>=52 && y<=140){
            if(!applyFields())return true;pause();if(y>=90){
                lane=y<115?0:1;List<Clip> list=clips();
                for(int i=list.size()-1;i>=0;i--){Clip clip=list.get(i);int left=xAt(clip.start(seconds())),right=xAt(clip.end(seconds()));
                    if(x>=left&&x<=right){selected=i;dragOriginal=clip;dragStartMouse=tickAt(x);dragMode=Math.abs(x-left)<=5?1:Math.abs(x-right)<=5?2:3;updateFields();return true;}
                }
            }
            selected=-1;dragMode=4;playhead=tickAt(x);preview.seek(playhead,false);updateFields();return true;
        }return false;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(soundsDialog.isVisible())return true;
        if(button==0 && dragMode==4){
            playhead=tickAt(x);preview.seek(playhead,false);return true;
        }
        if(button==0&&dragMode!=0&&selected>=0){
            int originalStart=dragOriginal.start(seconds()),originalEnd=dragOriginal.end(seconds());
            int delta=(int)Math.round((tickAt(x)-dragStartMouse)/2)*2;
            int from=originalStart,to=originalEnd;
            if(dragMode==1)from=Math.max(0,Math.min(originalEnd-2,originalStart+delta));
            if(dragMode==2)to=Math.min(length(),Math.max(originalStart+2,originalEnd+delta));
            if(dragMode==3){from=Math.max(0,Math.min(length()-dragOriginal.durationTicks(),originalStart+delta));to=from+dragOriginal.durationTicks();}
            try{var list=new ArrayList<>(clips());list.set(selected,at(from,to-from,dragOriginal.value(),dragOriginal.volume()));store(list);updateFields();message="";}
            catch(RuntimeException error){message=error.getMessage();}
            return true;
        }return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){dragMode=0;dragOriginal=null;if(soundsDialog.isVisible())return soundsDialog.mouseReleased(x,y,button);return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(soundsDialog.isVisible())return soundsDialog.mouseScrolled(x,y,delta);
        if(y>=52&&y<=140){
            if(hasShiftDown())viewStart=Math.max(0,Math.min(length()-visibleTicks,viewStart-delta*visibleTicks*.1));
            else{double anchor=tickAt(x),fraction=(x-12)/(width-24);visibleTicks=Math.max(40,Math.min(length(),visibleTicks*Math.pow(.8,delta)));viewStart=Math.max(0,Math.min(length()-visibleTicks,anchor-fraction*visibleTicks));}
            return true;
        }return super.mouseScrolled(x,y,delta);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){if(soundsDialog.isVisible())return soundsDialog.keyPressed(key,scan,mods);return super.keyPressed(key,scan,mods);}
    @Override public boolean charTyped(char letter,int mods){if(soundsDialog.isVisible())return soundsDialog.charTyped(letter,mods);return super.charTyped(letter,mods);}
    @Override public void onClose(){
        if(!applyFields())return;pause();
        if(dirty){minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok->{if(ok)minecraft.setScreen(parent);else minecraft.setScreen(this);},Component.literal("放弃未保存的时间轴？"),Component.literal("返回会丢弃本次尚未保存的修改")));}
        else minecraft.setScreen(parent);
    }
    @Override public void removed(){preview.stop();}
    @Override public boolean isPauseScreen(){return false;}
}
