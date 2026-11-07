package org.mods.gd656conquest.client.loadout.preview;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState.WeaponAttachmentPanelState;
import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;
import java.util.*;
import static org.mods.gd656conquest.client.loadout.preview.WeaponEditorLayout.*;

/** Client-only view. Its host owns the original server actions and the authoritative snapshot. */
public final class WeaponCustomizationView {
    public interface Actions {
        void back();
        void chooseWeapon();
        void switchSlot();
        void selectAttachment(int slotIndex,String weaponEntryId,String type,String id);
    }
    private final Actions actions;
    private final LoadoutItemPreview preview=new LoadoutItemPreview();
    private final WeaponEditorIcons icons=new WeaponEditorIcons();
    private final Map<String,ItemStack> items=new HashMap<>();
    private final Map<String,ItemStack> variants=new HashMap<>();
    private WeaponAttachmentPanelState panel;
    private ItemStack base=ItemStack.EMPTY,displayed=ItemStack.EMPTY;
    private TaczWeaponStats.Snapshot stats=TaczWeaponStats.Snapshot.unavailable();
    private String selectedType,hoveredChoice;
    private int offset;
    private Frame frame;
    private float scale=1,left,top;
    private boolean inputEnabled=true;
    private boolean keyboardFocus;
    private String focusedAction;
    private int previousMouseX,previousMouseY;
    private boolean embedded;

    public WeaponCustomizationView(Actions actions){this.actions=Objects.requireNonNull(actions);}
    public void render(GuiGraphics graphics,int mouseX,int mouseY,int width,int height,float partialTick,
            WeaponAttachmentPanelState nextPanel,ItemStack nextBase,String weaponName,String className,String slotLabel,boolean enabled) {
        renderArea(graphics,mouseX,mouseY,0,0,width,height,1,false,partialTick,nextPanel,nextBase,weaponName,className,slotLabel,enabled);
    }
    public void renderEmbedded(GuiGraphics graphics,int mouseX,int mouseY,int x,int y,int width,int height,float uiScale,float partialTick,
            WeaponAttachmentPanelState nextPanel,ItemStack nextBase,String weaponName,String className,String slotLabel,boolean enabled) {
        renderArea(graphics,mouseX,mouseY,x,y,width,height,uiScale,true,partialTick,nextPanel,nextBase,weaponName,className,slotLabel,enabled);
    }
    private void renderArea(GuiGraphics graphics,int mouseX,int mouseY,int x,int y,int width,int height,float uiScale,boolean embedded,float partialTick,
            WeaponAttachmentPanelState nextPanel,ItemStack nextBase,String weaponName,String className,String slotLabel,boolean enabled) {
        this.embedded=embedded;
        this.inputEnabled=enabled;
        if(!enabled)preview.clearInteraction();
        this.scale=embedded?uiScale:Math.min(width/960f,height/540f);
        this.left=embedded?x:(width-960*scale)/2;this.top=embedded?y:(height-540*scale)/2;
        if(this.panel!=nextPanel || !ItemStack.matches(this.base,nextBase)) {
            boolean changedWeapon=this.panel==null||nextPanel==null||this.panel.slotIndex()!=nextPanel.slotIndex()
                ||!Objects.equals(this.panel.loadoutEntryId(),nextPanel.loadoutEntryId());
            this.panel=nextPanel;this.base=nextBase.copy();items.clear();variants.clear();hoveredChoice=null;
            if(changedWeapon){selectedType=null;offset=0;}
            if(selectedType!=null&&(panel==null||!panel.attachmentTypes().containsKey(selectedType)||!panel.attachmentTypes().get(selectedType).available())){selectedType=null;offset=0;}
        }
        double mx=enabled?(mouseX-left)/scale:-10000,my=enabled?(mouseY-top)/scale:-10000;
        if(Math.abs(mouseX-previousMouseX)>1||Math.abs(mouseY-previousMouseY)>1){keyboardFocus=false;focusedAction=null;}
        previousMouseX=mouseX;previousMouseY=mouseY;
        if(enabled&&keyboardFocus&&frame!=null&&focusedAction!=null) {
            for(Hit hit:frame.hits())if(hit.enabled()&&hit.action().equals(focusedAction)){mx=hit.rect().x()+hit.rect().w()/2;my=hit.rect().y()+hit.rect().h()/2;break;}
        }
        Hit over=enabled&&frame!=null?frame.hit(mx,my):null;
        hoveredChoice=over!=null&&over.action().startsWith("equip:")?over.action().substring(6):null;
        List<Category> categories=new ArrayList<>();List<Choice> choices=new ArrayList<>();
        String chosen=null;
        for(String type:List.of("scope","muzzle","stock","grip","laser","extended_mag")) {
            var state=panel==null?null:panel.attachmentTypes().get(type);
            String key="",selected="—";int unlocked=0,total=0;
            if(state!=null) {
                total=state.choices().size();unlocked=(int)state.choices().values().stream().filter(v->v.unlocked()).count();
                var selectedChoice=state.choices().get(state.selectedAttachmentId());
                if(selectedChoice!=null) {key="category:"+type;items.computeIfAbsent(key,ignored->parse(selectedChoice.itemSnbt()));selected=label(selectedChoice.displayName(),selectedChoice.id());}
                else if(state.available())selected=state.hasStoredSelection()?"无配件":"默认";
                if(type.equals(selectedType)) {
                    chosen=hoveredChoice!=null?hoveredChoice:state.hasStoredSelection()?state.selectedAttachmentId():null;
                    choices.add(new Choice("","无配件","",true,state.hasStoredSelection()&&state.selectedAttachmentId().isBlank()));
                    for(var choice:state.sortedChoices()) {
                        String itemKey="choice:"+choice.id();items.computeIfAbsent(itemKey,ignored->parse(choice.itemSnbt()));
                        choices.add(new Choice(choice.id(),label(choice.displayName(),choice.id()),itemKey,choice.unlocked(),state.hasStoredSelection()&&choice.id().equals(state.selectedAttachmentId())));
                    }
                }
            }
            categories.add(new Category(type,typeLabel(type),selected,key,state!=null&&state.available()&&panel.available()&&panel.taczGun(),unlocked,total));
        }
        if(selectedType!=null)offset=Math.min(offset,Math.max(0,choices.size()-(frame==null?1:Math.max(1,frame.visibleChoices()))));
        ItemStack shown=base;
        if(selectedType!=null&&hoveredChoice!=null&&panel!=null) {
            var state=panel.attachmentTypes().get(selectedType);
            if(state!=null) {
                var choice=state.choices().get(hoveredChoice);
                if(hoveredChoice.isBlank()||choice!=null) {
                    String key=selectedType+":"+hoveredChoice;
                    shown=variants.computeIfAbsent(key,ignored->LoadoutWeaponPreview.apply(base,List.of(
                        new LoadoutWeaponPreview.Selection(selectedType,choice==null?"":choice.itemSnbt(),hoveredChoice.isBlank()))));
                }
            }
        }
        if(!ItemStack.matches(displayed,shown)) { displayed=shown.copy();stats=TaczWeaponStats.read(displayed); }
        preview.setItem(displayed);items.put("weapon",displayed);
        List<Metric> metrics=new ArrayList<>();
        // Exact TACZ diagram semantics supply the bars; field names retain their original meaning.
        for(var m:stats.modifiers()) {
            if(metrics.size()==4)break;
            String title=Component.translatable(m.titleKey()).getString();
            metrics.add(new Metric(title,m.valueText(),m.basePercent()==null?null:m.basePercent()+(m.modifierPercent()==null?0:m.modifierPercent())));
        }
        while(metrics.size()<4)metrics.add(new Metric(new String[]{"DAMAGE","ACCURACY","HIP FIRE","CONTROL"}[metrics.size()],"—",null));
        List<Point> points=stats.damageByDistance().stream().map(v->new Point(v.distance(),v.damage())).toList();
        Data data=new Data(weaponName,className,slotLabel,List.copyOf(categories),List.copyOf(choices),selectedType,chosen,
            List.copyOf(metrics),points,stats.fireMode()==null?"—":stats.fireMode(),number(stats.damage()),number(stats.rpm()),Boolean.TRUE.equals(stats.inventoryAmmo())?"INV":number(stats.magazine()),
            unit(stats.muzzleVelocity(),"m/s"),stats.adsSeconds()==null?"—":number(stats.adsSeconds()*1000)+"ms",unit(stats.reloadTacticalSeconds(),"s"),number(stats.headshotMultiplier()));
        if(embedded) {
            org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(graphics,x,y,x+width,y+height);
            this.frame=WeaponEditorLayout.renderEmbedded(new GameCanvas(graphics,mouseX,mouseY,partialTick),Theme.valueOf(BattlefieldUiTheme.current().name()),data,mx,my,offset,width/scale,height/scale);
            graphics.disableScissor();
        } else {
            graphics.fill(0,0,width,height,0xff071013);
            this.frame=WeaponEditorLayout.render(new GameCanvas(graphics,mouseX,mouseY,partialTick),Theme.valueOf(BattlefieldUiTheme.current().name()),data,mx,my,offset);
        }
    }
    public boolean mouseClicked(double x,double y,int button) {
        if(!inputEnabled||frame==null)return false;
        keyboardFocus=false;focusedAction=null;
        Hit hit=frame.hit((x-left)/scale,(y-top)/scale);
        if(button==0&&hit!=null) {
            if(!hit.enabled())return true;
            String action=hit.action();
            if(action.startsWith("type:")){selectedType=action.substring(5);hoveredChoice=null;offset=0;preview.clearInteraction();return true;}
            if(action.startsWith("equip:")){if(panel!=null)actions.selectAttachment(panel.slotIndex(),panel.loadoutEntryId(),selectedType,action.substring(6));return true;}
            switch(action) {
                case "back" -> back();
                case "weapons" -> {preview.clearInteraction();actions.chooseWeapon();}
                case "slot" -> {selectedType=null;offset=0;preview.clearInteraction();actions.switchSlot();}
                case "reset" -> preview.resetView();
                case "theme" -> {preview.clearInteraction();BattlefieldUiTheme.cycle();}
                case "prev" -> offset=Math.max(0,offset-frame.visibleChoices());
                case "next" -> offset+=frame.visibleChoices();
                default -> {return false;}
            }
            return true;
        }
        return preview.mouseClicked(x,y,button);
    }
    public boolean mouseScrolled(double x,double y,double delta) {
        if(!inputEnabled||frame==null)return false;
        keyboardFocus=false;focusedAction=null;
        if(frame.choices()!=null&&frame.choices().contains((x-left)/scale,(y-top)/scale)&&panel!=null&&selectedType!=null) {
            int total=panel.attachmentTypes().get(selectedType).choices().size()+1;
            offset=Math.max(0,Math.min(Math.max(0,total-frame.visibleChoices()),offset+(delta<0?1:-1)));return true;
        }
        return preview.mouseScrolled(x,y,delta);
    }
    public boolean mouseDragged(double x,double y,int button,double dx,double dy){return inputEnabled&&preview.mouseDragged(x,y,button,dx/scale,dy/scale);}
    public void mouseReleased(double x,double y,int button){preview.mouseReleased(x,y,button);}
    public void clearInteraction(){preview.clearInteraction();}
    public void close(){preview.clearInteraction();icons.close();}
    public boolean keyPressed(int key,int modifiers){
        if(key==256){if(embedded&&selectedType==null)return false;back();return true;}
        if(key==82){preview.resetView();return true;}
        if(frame==null||!inputEnabled)return false;
        Map<String,Hit> options=new LinkedHashMap<>();
        for(Hit hit:frame.hits())if(hit.enabled())options.putIfAbsent(hit.action(),hit);
        List<String> order=new ArrayList<>(options.keySet());if(order.isEmpty())return false;
        if(key==258||key==262||key==263||key==264||key==265){
            int direction=key==263||key==265||key==258&&(modifiers&1)!=0?-1:1;
            int index=order.indexOf(focusedAction);
            focusedAction=order.get(Math.floorMod(index<0?(direction>0?0:order.size()-1):index+direction,order.size()));
            keyboardFocus=true;preview.clearInteraction();return true;
        }
        if((key==257||key==335||key==32)&&keyboardFocus&&focusedAction!=null&&options.containsKey(focusedAction)){
            Rect r=options.get(focusedAction).rect();return mouseClicked(left+(r.x()+r.w()/2)*scale,top+(r.y()+r.h()/2)*scale,0);
        }
        return false;
    }
    private void back(){preview.clearInteraction();focusedAction=null;keyboardFocus=false;if(selectedType!=null){selectedType=null;hoveredChoice=null;offset=0;}else actions.back();}
    private static String typeLabel(String type){return switch(type){case "scope"->"瞄准镜";case "muzzle"->"枪口";case "stock"->"枪托";case "grip"->"前握把";case "laser"->"激光";case "extended_mag"->"弹匣";default->type;};}
    private static String label(String name,String fallback){return name==null||name.isBlank()?fallback:Component.translatable(name).getString();}
    private static String number(Number value){if(value==null||!Double.isFinite(value.doubleValue()))return "—";double d=value.doubleValue();return Math.abs(d-Math.rint(d))<0.01?String.format(Locale.ROOT,"%.0f",d):String.format(Locale.ROOT,"%.2f",d);}
    private static String unit(Double value,String unit){return value==null?"—":number(value)+unit;}
    private static ItemStack parse(String snbt){if(snbt==null||snbt.isBlank())return ItemStack.EMPTY;try{return ItemStack.of(TagParser.parseTag(snbt));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){com.mojang.logging.LogUtils.getLogger().warn("Invalid weapon editor item SNBT",e);return ItemStack.EMPTY;}}

    private final class GameCanvas implements Canvas {
        private final GuiGraphics g;private final int mx,my;private final float tick;private int layer;
        GameCanvas(GuiGraphics g,int mx,int my,float tick){this.g=g;this.mx=mx;this.my=my;this.tick=tick;}
        public void rect(float x,float y,float w,float h,int color){g.fill(Math.round(left+x*scale),Math.round(top+y*scale),Math.round(left+(x+w)*scale),Math.round(top+(y+h)*scale),layer,color);}
        public void line(float x,float y,float xx,float yy,float thickness,int color){
            float dx=xx-x,dy=yy-y,len=(float)Math.hypot(dx,dy);if(len==0)return;
            float nx=-dy/len*thickness/2,ny=dx/len*thickness/2;
            VertexConsumer v=g.bufferSource().getBuffer(RenderType.gui());var m=g.pose().last().pose();
            float[][] pts={{x-nx,y-ny},{x+nx,y+ny},{xx+nx,yy+ny},{xx-nx,yy-ny}};
            for(float[] p:pts)v.vertex(m,left+p[0]*scale,top+p[1]*scale,layer).color(color>>16&255,color>>8&255,color&255,color>>>24).endVertex();
        }
        private org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Face fontFace(Face face){
            if(face==Face.LIGHT&&BattlefieldUiTheme.current().name().equals("BF6"))return org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Face.NUMBER;
            return org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Face.valueOf(face.name());
        }
        public float textWidth(String value,Face face,float size){
            g.pose().pushPose();g.pose().scale(scale,scale,1);
            float result=org.mods.gd656killicon.client.gui.theme.BattlefieldTextRenderer.width(g,BattlefieldUiTheme.current(),fontFace(face),size,value);
            g.pose().popPose();return result;
        }
        public void text(String value,Face face,float size,float x,float y,int color,Align align){
            if(value==null||value.isEmpty())return;
            g.pose().pushPose();g.pose().translate(left,top,layer);g.pose().scale(scale,scale,1);
            org.mods.gd656killicon.client.gui.theme.BattlefieldTextRenderer.draw(g,BattlefieldUiTheme.current(),fontFace(face),size,value,x,y,color,
                org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Align.valueOf(align.name()));
            g.pose().popPose();
        }
        public void item(String key,Rect r,int tint){
            ItemStack stack=items.getOrDefault(key,ItemStack.EMPTY);
            if(stack.isEmpty()){line(r.x()+r.w()/2-5,r.y()+r.h()/2,r.x()+r.w()/2+5,r.y()+r.h()/2,1,tint);return;}
            var icon=icons.get(stack,tint,BattlefieldUiTheme.current().name().equals("BF1"));
            if(icon!=null){
                float ratio=Math.min(r.w()/icon.width(),r.h()/icon.height());
                g.pose().pushPose();g.pose().translate(left+(r.x()+(r.w()-icon.width()*ratio)/2)*scale,top+(r.y()+(r.h()-icon.height()*ratio)/2)*scale,layer);
                g.pose().scale(scale*ratio,scale*ratio,1);g.blit(icon.texture(),0,0,0,0,icon.width(),icon.height(),icon.width(),icon.height());g.pose().popPose();return;
            }
            float size=Math.min(r.w(),r.h());g.pose().pushPose();
            g.pose().translate(left+(r.x()+(r.w()-size)/2)*scale,top+(r.y()+(r.h()-size)/2)*scale,layer);
            g.pose().scale(scale*size/16f,scale*size/16f,1);g.renderItem(stack,0,0);g.pose().popPose();
        }
        public void weapon(Rect r){preview.setBounds(Math.round(left+r.x()*scale),Math.round(top+r.y()*scale),Math.round(r.w()*scale),Math.round(r.h()*scale));preview.render(g,mx,my,tick);layer=300;}
        public void environment(String name,Rect r){
            g.pose().pushPose();g.pose().translate(left+r.x()*scale,top+r.y()*scale,0);g.pose().scale(scale*r.w()/1023f,scale*r.h()/575f,1);
            g.blit(ResourceLocation.fromNamespaceAndPath("gd656conquest","textures/gui/weapon_editor/"+name),0,0,0,0,1023,575,1023,575);g.pose().popPose();
        }
        public void clip(Rect r){org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(g,Math.round(left+r.x()*scale),Math.round(top+r.y()*scale),Math.round(left+(r.x()+r.w())*scale),Math.round(top+(r.y()+r.h())*scale));}
        public void unclip(){g.disableScissor();}
    }
}
