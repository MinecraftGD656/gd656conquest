package org.mods.gd656conquest.client.loadout.preview;

import java.util.*;

/** The three reference compositions, in a 960 x 540 viewport. */
public final class WeaponEditorLayout {
    public enum Theme { BF6, BF2042, BF1 }
    public enum Face { TEXT, HEADING, LIGHT }
    public enum Align { LEFT, CENTER, RIGHT }
    public record Rect(float x,float y,float w,float h) {
        public boolean contains(double px,double py) { return px>=x && py>=y && px<x+w && py<y+h; }
    }
    public record Category(String id,String label,String selectedName,String itemKey,boolean available,int unlocked,int total) {}
    public record Choice(String id,String label,String itemKey,boolean unlocked,boolean equipped) {}
    public record Metric(String label,String value,Double fraction) {}
    public record Point(double distance,double damage) {}
    public record Data(String weaponName,String className,String slotLabel,List<Category> categories,List<Choice> choices,
            String selectedType,String previewChoice,List<Metric> metrics,List<Point> damageCurve,String fireMode,
            String damage,String rpm,String magazine,String velocity,String ads,String reload,String headshot) {}
    public record Hit(String action,Rect rect,boolean enabled) {}
    public record Frame(List<Hit> hits,Rect weapon,Rect choices,int visibleChoices) {
        public Hit hit(double x,double y) {
            for(int i=hits.size()-1;i>=0;i--) if(hits.get(i).rect.contains(x,y))return hits.get(i);
            return null;
        }
    }
    public interface Canvas {
        void rect(float x,float y,float w,float h,int color);
        void line(float x1,float y1,float x2,float y2,float thickness,int color);
        void text(String value,Face face,float size,float x,float y,int color,Align align);
        float textWidth(String value,Face face,float size);
        void item(String key,Rect bounds,int tint);
        void weapon(Rect bounds);
        default void environment(String name,Rect bounds) {}
        void clip(Rect bounds);
        void unclip();
    }
    private static final int WHITE=0xfff5f3ee, SILVER=0xffc1c9cb, DIM=0xff9ca7aa, GREEN=0xff64ae57, TEAL=0xff48f1d1;
    private WeaponEditorLayout() {}
    public static Frame render(Canvas c,Theme theme,Data d,double mx,double my,int choiceOffset) {
        List<Hit> hits=new ArrayList<>();
        return switch(theme) {
            case BF6 -> bf6(c,d,mx,my,choiceOffset,hits);
            case BF2042 -> renderEmbedded(c,theme,d,mx,my,choiceOffset,960,540);
            case BF1 -> bf1(c,d,mx,my,choiceOffset,hits);
        };
    }

    /** The editor shares its parent's class and equipment navigation. Coordinates follow its available region. */
    public static Frame renderEmbedded(Canvas c,Theme theme,Data d,double mx,double my,int offset,float w,float height) {
        List<Hit> hits=new ArrayList<>();
        int ink=theme==Theme.BF2042?TEAL:theme==Theme.BF1?WHITE:SILVER;
        int panel=theme==Theme.BF2042?0xa006262a:0x7710161a;
        c.rect(0,0,w,height,panel);
        if(theme==Theme.BF1){c.environment("bf1_sinai.jpg",new Rect(0,0,w,height));c.rect(0,0,w,height,0x99202a2c);}
        clipped(c,d.weaponName+"  /  "+d.slotLabel,theme==Theme.BF6?Face.HEADING:Face.TEXT,16,12,5,w-125,ink);
        c.line(10,29,w-10,29,0.8f,ink);
        button(c,hits,"weapons","更换武器",new Rect(w-99,2,89,23),mx,my,ink);
        if(theme==Theme.BF2042)return bf2042Star(c,d,mx,my,offset,w,height,hits);
        float bottom=height-23;
        boolean expanded=d.selectedType!=null;
        Rect choices=null;
        int visible=0;
        Rect model;
        if(theme==Theme.BF1) {
            float listW=Math.min(125,w*0.21f),statsW=w>=590?162:0;
            float rowH=Math.min(50,(bottom-36)/6);
            model=new Rect(listW+12,56,Math.max(70,w-listW-statsW-27),bottom-69);
            c.weapon(model);
            for(int i=0;i<d.categories.size();i++) {
                Category cat=d.categories.get(i);Rect r=new Rect(8,36+i*rowH,listW-8,rowH-2);
                boolean active=cat.id.equals(d.selectedType)||r.contains(mx,my);
                c.rect(r.x,r.y,r.w,r.h,active?WHITE:0x7d1b2022);
                c.line(r.x,r.y,r.x,r.y+r.h,0.8f,WHITE);
                c.text(cat.label,Face.TEXT,10,r.x+r.w/2,r.y+4,active?0xff171d1c:WHITE,Align.CENTER);
                categoryIcon(c,cat,new Rect(r.x+25,r.y+19,r.w-50,Math.max(9,rowH-24)),active?0xff171d1c:WHITE);
                hits.add(new Hit("type:"+cat.id,r,cat.available));
            }
            if(statsW>0) {
                float x=w-statsW+7;c.rect(x,35,statsW-14,bottom-43,0x86232a2c);
                clipped(c,d.weaponName,Face.TEXT,14,x+9,47,statsW-30,WHITE);
                text(c,"DAMAGE  "+d.damage,Face.TEXT,11,x+9,77,WHITE);
                text(c,"RPM  "+d.rpm,Face.TEXT,11,x+9,100,WHITE);
                text(c,"AMMO  "+d.magazine,Face.TEXT,11,x+9,123,WHITE);
                clipped(c,d.fireMode,Face.TEXT,10,x+9,146,statsW-30,WHITE);
                damageGraph(c,d.damageCurve,new Rect(x+10,181,statsW-34,Math.max(20,Math.min(64,bottom-221))),WHITE);
            }
            if(expanded) {
                int index=0;for(int i=0;i<d.categories.size();i++)if(d.categories.get(i).id.equals(d.selectedType))index=i;
                float y=Math.min(36+index*rowH,Math.max(36,bottom-155));
                choices=new Rect(listW+4,y,Math.min(222,w-listW-statsW-15),Math.min(150,bottom-y));
                visible=choiceList(c,hits,d,choices,offset,mx,my);
            }
        } else {
            float categoryW=0;
            float choiceH=expanded?Math.min(106,height*0.27f):0;
            float stageBottom=bottom-choiceH-(expanded?23:0);
            float modelX=categoryW+12,modelW=w-modelX-12;
            model=new Rect(modelX+modelW*0.08f,56,modelW*0.86f,Math.max(70,stageBottom-67));
            c.weapon(model);
            float nodeSize=Math.max(39,Math.min(51,modelW/8));
            float cx=modelX+modelW*0.56f,cy=52+(stageBottom-52)*0.50f;
            float spreadX=modelW*0.32f,spreadY=Math.max(36,(stageBottom-70)*0.37f);
            float[][] positions={{0,-1},{-1,0},{1,0},{-0.1f,1},{-0.65f,-0.68f},{0.72f,0.76f}};
            for(int i=0;i<d.categories.size();i++) {
                Category cat=d.categories.get(i);float[] p=positions[i%6];
                Rect r=new Rect(cx+p[0]*spreadX-nodeSize/2,cy+p[1]*spreadY-nodeSize/2,nodeSize,nodeSize+10);
                boolean over=r.contains(mx,my)||cat.id.equals(d.selectedType);
                c.line(cx,cy,r.x+r.w/2,r.y+r.h/2,over?1.2f:0.6f,over?ink:0x667e9b9d);
                c.rect(r.x,r.y,r.w,r.h,theme==Theme.BF2042?(over?TEAL:0xc5165e5a):0xd00b1216);
                if(theme==Theme.BF6)outline(c,r,over?ink:0x63818d94);
                int color=theme==Theme.BF2042&&over?0xff052b29:ink;
                categoryIcon(c,cat,new Rect(r.x+6,r.y+4,r.w-12,r.h-23),color);
                c.text(cat.label,Face.TEXT,9,r.x+r.w/2,r.y+r.h-17,color,Align.CENTER);
                hits.add(new Hit("type:"+cat.id,r,cat.available));
            }
            if(expanded) {
                choices=new Rect(10,bottom-choiceH,w-20,choiceH);
                int cols=Math.max(3,Math.min(theme==Theme.BF2042?9:6,(int)(choices.w/74)));
                visible=choiceGrid(c,hits,d,choices,offset,cols,theme==Theme.BF6?2:1,mx,my,theme);
                clipped(c,previewLabel(d),Face.TEXT,11,12,choices.y-21,w-150,ink);
            } else {
                text(c,"DMG "+d.damage+"   RPM "+d.rpm+"   MAG "+d.magazine,Face.LIGHT,11,12,bottom-16,ink);
            }
        }
        c.line(10,bottom+3,w-10,bottom+3,0.6f,0x55768a8e);
        if(expanded)button(c,hits,"back","收起配件",new Rect(9,bottom,82,20),mx,my,ink);
        else text(c,"拖动旋转 · 滚轮缩放 · 中键复位",Face.TEXT,9,12,bottom+7,ink);
        return new Frame(List.copyOf(hits),model,choices,visible);
    }
    /** Six-way Plus: a vertical axis and two diagonal axes; each category owns one stable spoke. */
    private static Frame bf2042Star(Canvas c,Data d,double mx,double my,int offset,float w,float height,List<Hit> hits){
        boolean expanded=d.selectedType!=null,left=expanded&&Set.of("muzzle","grip").contains(d.selectedType);
        float panelW=expanded?Math.min(226,w*.34f):0,stageX=left?panelW+12:0,stageW=w-panelW-(expanded?12:0);
        float bottom=height-26,cx=stageX+stageW*.53f,cy=(42+bottom)*.5f;
        float rx=Math.min(205,stageW*.30f),ry=Math.min(122,(bottom-42)*.34f),tileW=expanded?66:78,tileH=52;
        String[] order={"scope","stock","laser","extended_mag","grip","muzzle"};
        var cells=new LinkedHashMap<String,Rect>();
        for(int i=0;i<order.length;i++){
            double angle=Math.toRadians(-90+i*60);float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
            float x=cx+dx*rx,y=cy+dy*ry;cells.put(order[i],new Rect(x-tileW/2,y-tileH/2,tileW,tileH));
            c.line(cx+dx*35,cy+dy*25,x,y,order[i].equals(d.selectedType)?1.5f:.8f,order[i].equals(d.selectedType)?TEAL:0x7067b0aa);
        }
        Rect model=new Rect(stageX+stageW*.09f,cy-50,stageW*.84f,114);c.weapon(model);
        for(String id:order){
            Category cat=d.categories.stream().filter(v->v.id.equals(id)).findFirst().orElse(null);if(cat==null)continue;
            Rect r=cells.get(id);boolean selected=id.equals(d.selectedType),over=r.contains(mx,my),lit=selected||over;
            c.rect(r.x,r.y,r.w,r.h,lit?TEAL:0xeb073e40);outline(c,r,lit?TEAL:0x9c49b6ad);
            int color=lit?0xff032b2c:cat.available?TEAL:DIM;
            categoryIcon(c,cat,new Rect(r.x+12,r.y+4,r.w-24,28),color);
            clipped(c,cat.label,Face.TEXT,9,r.x+4,r.y+37,r.w-8,color);
            if(!cat.available)lock(c,r.x+r.w-12,r.y+5,color);
            if(selected)c.rect(r.x+r.w/2-3,r.y+r.h+3,6,2,TEAL);
            hits.add(new Hit("type:"+id,r,cat.available));
        }
        Rect choices=null;int visible=0;
        if(expanded){
            float x=left?8:w-panelW+2;c.rect(x,37,panelW-10,bottom-43,0xe0041d23);
            clipped(c,category(d).label,Face.HEADING,15,x+9,47,panelW-28,TEAL);
            clipped(c,previewLabel(d),Face.TEXT,10,x+9,72,panelW-28,0xffc0dfda);
            choices=new Rect(x+5,97,panelW-20,bottom-107);visible=choiceList(c,hits,d,choices,offset,mx,my,Theme.BF2042);
            button(c,hits,"back","收起配件",new Rect(x,bottom+3,88,21),mx,my,TEAL);
        }
        text(c,"DMG "+d.damage+"   RPM "+d.rpm+"   MAG "+d.magazine,Face.LIGHT,11,stageX+12,bottom-17,TEAL);
        c.line(10,bottom+1,w-10,bottom+1,.7f,0x8061aaa4);
        if(!expanded)text(c,"拖动旋转 · 滚轮缩放 · 中键复位",Face.TEXT,9,12,bottom+8,TEAL);
        else clipped(c,"选择后保存到 "+d.className+" · 部署生效",Face.TEXT,9,stageX+12,bottom+8,stageW-24,TEAL);
        return new Frame(List.copyOf(hits),model,choices,visible);
    }
    private static Frame bf6(Canvas c,Data d,double mx,double my,int offset,List<Hit> h) {
        c.rect(0,0,960,540,0xf0091014);
        // Silver vertical light falls behind the model, as on the reference armory screen.
        for(int i=0;i<100;i++) { int a=(int)(18*Math.sin(i*Math.PI/100)); c.rect(285+i*4,18,4,480,(a<<24)|0x91a5ae); }
        c.rect(0,0,960,27,0x7f000000);
        text(c,d.selectedType==null?"CUSTOMIZE "+d.weaponName:"SELECT "+category(d).label,Face.TEXT,15,78,9,SILVER);
        back(c,h,12,7,mx,my,SILVER);
        if(d.selectedType==null) {
            text(c,d.weaponName,Face.HEADING,29,32,61,SILVER);
            text(c,"— / —",Face.TEXT,13,34,96,SILVER);
        }
        c.line(16,45,16,492,0.6f,0x447a858a);
        Rect model=new Rect(260,99,526,276);
        if(d.selectedType==null) {
            c.weapon(model);
            float[][] nodes={{610,30,575,171},{130,219,392,204},{789,179,720,235},{422,281,532,252},{357,81,466,193},{487,361,557,289}};
            for(int i=0;i<d.categories.size();i++) {
                Category cat=d.categories.get(i);float[] n=nodes[i%nodes.length];
                Rect r=new Rect(n[0],n[1],58,62);boolean over=r.contains(mx,my);
                c.line(r.x+r.w/2,r.y+r.h/2,n[2],n[3],over?1.5f:0.5f,over?SILVER:0x506e7d83);
                node(c,h,cat,r,over,SILVER,0xc0060b0e);
            }
            status(c,"—".equals(d.weaponName)?"未配置":"已装备",33,140,"—".equals(d.weaponName)?DIM:GREEN);
            c.rect(29,419,195,4,0xffa8782b);text(c,d.className+"  /  "+d.slotLabel,Face.TEXT,12,37,398,SILVER);
            for(int i=0;i<3;i++) {
                Rect r=new Rect(28+i*104,424,98,70);outline(c,r,0x55667379);
                text(c,new String[]{"CAMOS /","CHARMS /","STICKERS /"}[i],Face.HEADING,12,r.x+6,r.y+48,DIM);
                text(c,"—",Face.TEXT,13,r.x+6,r.y+15,DIM);
            }
        } else {
            c.weapon(new Rect(282,98,620,238));
            clipped(c,previewLabel(d),Face.HEADING,22,36,214,290,SILVER);
            text(c,category(d).label,Face.TEXT,12,36,246,SILVER);
            status(c,previewEquipped(d)?"已装备":"预览",36,279,previewEquipped(d)?GREEN:DIM);
        }
        bf6Stats(c,d,675,350);
        Rect choices=d.selectedType==null?null:new Rect(34,350,536,150);
        int count=0;
        if(choices!=null)count=choiceGrid(c,h,d,choices,offset,6,2,mx,my,Theme.BF6);
        footer(c,h,d,Theme.BF6,mx,my);
        return new Frame(List.copyOf(h),d.selectedType==null?model:new Rect(282,98,620,238),choices,count);
    }
    private static Frame bf1(Canvas c,Data d,double mx,double my,int offset,List<Hit> h) {
        c.environment("bf1_sinai.jpg",new Rect(0,0,960,540));
        c.rect(0,0,960,540,0x4f242b2b);
        c.rect(0,0,960,67,0x322b3033);
        c.text("MODIFY",Face.LIGHT,37,480,18,WHITE,Align.CENTER);
        c.line(0,66,960,66,0.4f,0x559da5a8);
        Rect model=new Rect(202,123,507,285);
        c.weapon(model);
        for(int i=0;i<d.categories.size();i++) {
            Category cat=d.categories.get(i);Rect r=new Rect(49,101+i*54,143,52);
            boolean selected=cat.id.equals(d.selectedType),over=r.contains(mx,my);
            c.rect(r.x,r.y,r.w,r.h,selected||over?WHITE:0x80333838);
            c.rect(r.x,r.y,0.7f,r.h,WHITE);
            int color=selected||over?0xff151a1b:WHITE;
            c.text(cat.label,Face.TEXT,12,r.x+r.w/2,r.y+4,color,Align.CENTER);
            categoryIcon(c,cat,new Rect(r.x+43,r.y+19,57,27),color);
            h.add(new Hit("type:"+cat.id,r,cat.available));
        }
        bf1Stats(c,d,718,100);
        Rect choices=null;int count=0;
        if(d.selectedType!=null) {
            int index=0;for(int i=0;i<d.categories.size();i++)if(d.categories.get(i).id.equals(d.selectedType))index=i;
            int y=Math.min(399,101+index*54);
            choices=new Rect(198,y,226,Math.min(134,494-y));
            count=choiceList(c,h,d,choices,offset,mx,my);
        }
        footer(c,h,d,Theme.BF1,mx,my);
        return new Frame(List.copyOf(h),model,choices,count);
    }
    private static void node(Canvas c,List<Hit> h,Category cat,Rect r,boolean hover,int color,int bg) {
        c.rect(r.x,r.y,r.w,r.h,bg);outline(c,r,hover?color:0x526b787e);
        categoryIcon(c,cat,new Rect(r.x+9,r.y+6,r.w-18,31),cat.available?color:DIM);
        clipped(c,cat.label,Face.HEADING,9,r.x+5,r.y+43,r.w-10,cat.available?color:DIM);
        h.add(new Hit("type:"+cat.id,r,cat.available));
    }
    private static int choiceGrid(Canvas c,List<Hit> h,Data d,Rect area,int offset,int cols,int rows,double mx,double my,Theme theme) {
        float gap=4,w=(area.w-gap*(cols-1))/cols,ht=(area.h-gap*(rows-1))/rows;
        int capacity=cols*rows;int accent=theme==Theme.BF2042?TEAL:SILVER;
        for(int n=0;n<capacity && offset+n<d.choices.size();n++) {
            Choice item=d.choices.get(offset+n);Rect r=new Rect(area.x+(n%cols)*(w+gap),area.y+(n/cols)*(ht+gap),w,ht);
            boolean selected=item.id.equals(d.previewChoice),over=r.contains(mx,my);
            int color=(over||selected)&&theme==Theme.BF2042?0xff032523:accent;
            c.rect(r.x,r.y,r.w,r.h,theme==Theme.BF2042&&(over||selected)?TEAL:0xba0d171b);
            outline(c,r,over||selected?accent:0x50647377);
            c.item(item.itemKey,new Rect(r.x+13,r.y+5,r.w-26,r.h-25),item.unlocked?color:DIM);
            clipped(c,item.label,Face.TEXT,9,r.x+4,r.y+r.h-16,r.w-8,item.unlocked?color:DIM);
            if(item.equipped)c.rect(r.x+2,r.y+r.h-3,r.w-4,2,theme==Theme.BF2042?TEAL:GREEN);
            if(!item.unlocked)lock(c,r.x+r.w-11,r.y+5,DIM);
            h.add(new Hit("equip:"+item.id,r,item.unlocked));
        }
        page(c,h,d,area,offset,capacity,accent);
        return capacity;
    }
    private static int choiceList(Canvas c,List<Hit> h,Data d,Rect area,int offset,double mx,double my) {
        return choiceList(c,h,d,area,offset,mx,my,Theme.BF1);
    }
    private static int choiceList(Canvas c,List<Hit> h,Data d,Rect area,int offset,double mx,double my,Theme theme) {
        int accent=theme==Theme.BF2042?TEAL:WHITE;
        int count=Math.max(1,(int)(area.h/23));
        for(int i=0;i<count&&offset+i<d.choices.size();i++) {
            Choice item=d.choices.get(offset+i);Rect r=new Rect(area.x,area.y+i*23,area.w,21);
            boolean selected=item.id.equals(d.previewChoice)||r.contains(mx,my);
            c.rect(r.x,r.y,r.w,r.h,selected?accent:theme==Theme.BF2042?0xc0083539:0xb02b3030);
            c.item(item.itemKey,new Rect(r.x+5,r.y+3,34,15),selected?0xff161c1d:accent);
            clipped(c,item.label,Face.TEXT,11,r.x+46,r.y+4,r.w-59,selected?0xff171c1d:accent);
            if(!item.unlocked)lock(c,r.x+r.w-10,r.y+5,DIM);
            h.add(new Hit("equip:"+item.id,r,item.unlocked));
        }
        page(c,h,d,area,offset,count,accent);return count;
    }
    private static void page(Canvas c,List<Hit> h,Data d,Rect area,int offset,int count,int color) {
        if(d.choices.size()<=count)return;
        Rect up=new Rect(area.x+area.w-45,area.y-18,20,15),down=new Rect(area.x+area.w-22,area.y-18,20,15);
        outline(c,up,color);outline(c,down,color);text(c,"<",Face.TEXT,11,up.x+6,up.y+1,color);text(c,">",Face.TEXT,11,down.x+6,down.y+1,color);
        h.add(new Hit("prev",up,offset>0));h.add(new Hit("next",down,offset+count<d.choices.size()));
    }
    private static void bf6Stats(Canvas c,Data d,float x,float y) {
        String[] labels={"DMG","ROF","MAG"},values={d.damage,d.rpm,d.magazine};
        for(int i=0;i<3;i++) { text(c,labels[i],Face.TEXT,11,x,y+i*41,DIM);text(c,values[i],Face.LIGHT,25,x,y+11+i*41,SILVER); }
        metricBars(c,d,x+64,y,161,19,SILVER);
        text(c,d.fireMode,Face.TEXT,10,x,y+129,SILVER);
        text(c,"初速 "+d.velocity+"    ADS "+d.ads,Face.TEXT,10,x,y+144,DIM);
    }
    private static void bf1Stats(Canvas c,Data d,float x,float y) {
        c.rect(x,y,193,129,0x66313635);
        c.item("weapon",new Rect(x+42,y+7,112,28),WHITE);
        clipped(c,d.weaponName,Face.TEXT,16,x+12,y+43,171,WHITE);
        text(c,d.className+" / "+d.slotLabel,Face.TEXT,11,x+12,y+69,WHITE);
        text(c,"已装配 "+equippedCount(d)+" 项配件",Face.TEXT,11,x+12,y+91,DIM);
        c.rect(x,y+140,193,131,0x66272d2e);
        metricBars(c,d,x+14,y+148,162,18,WHITE);
        text(c,"RATE OF FIRE",Face.TEXT,10,x+14,y+224,WHITE);c.text(d.rpm,Face.TEXT,12,x+176,y+222,WHITE,Align.RIGHT);
        text(c,"FIRE MODES",Face.TEXT,10,x+14,y+240,WHITE);c.text(d.fireMode,Face.TEXT,11,x+176,y+239,WHITE,Align.RIGHT);
        text(c,"AMMUNITION",Face.TEXT,10,x+14,y+256,WHITE);c.text(d.magazine,Face.TEXT,12,x+176,y+254,WHITE,Align.RIGHT);
        c.rect(x,y+276,193,90,0x66272d2e);text(c,"DAMAGE DROP (m)",Face.TEXT,10,x+14,y+283,WHITE);
        damageGraph(c,d.damageCurve,new Rect(x+16,y+299,160,54),WHITE);
    }
    private static void metricBars(Canvas c,Data d,float x,float y,float w,float rowH,int color) {
        for(int i=0;i<Math.min(4,d.metrics.size());i++) {
            Metric m=d.metrics.get(i);float yy=y+i*rowH;
            clipped(c,m.label,Face.TEXT,9,x,yy,w*0.48f,color);
            float bx=x+w*0.48f,bw=w*0.30f;
            c.rect(bx,yy+3,bw,4,0x80000609);
            if(m.fraction!=null)c.rect(bx,yy+3,bw*(float)Math.max(0,Math.min(1,m.fraction)),4,color);
            else c.text("—",Face.TEXT,9,bx+bw/2,yy-1,color,Align.CENTER);
            if(m.value!=null&&!m.value.equals("—"))c.text(m.value,Face.TEXT,8,x+w,yy-1,color,Align.RIGHT);
        }
    }
    private static void damageGraph(Canvas c,List<Point> values,Rect r,int color) {
        c.line(r.x,r.y,r.x,r.y+r.h,1.1f,color);c.line(r.x,r.y+r.h,r.x+r.w,r.y+r.h,1.1f,color);
        for(int i=1;i<4;i++){c.line(r.x+r.w*i/4,r.y,r.x+r.w*i/4,r.y+r.h,.6f,0x507b8c91);c.line(r.x,r.y+r.h*i/4,r.x+r.w,r.y+r.h*i/4,.6f,0x507b8c91);}
        for(int i=1;i<=4;i++)c.text(Integer.toString(i*25),Face.TEXT,8,r.x+r.w*i/4,r.y+r.h+2,DIM,Align.CENTER);
        if(values.isEmpty()){c.text("—",Face.TEXT,15,r.x+r.w/2,r.y+r.h/2-6,color,Align.CENTER);return;}
        double max=values.stream().mapToDouble(Point::damage).max().orElse(1);if(max<=0)return;
        float px=r.x,py=r.y+r.h-(float)(values.get(0).damage/max*r.h);
        for(int i=0;i<values.size();i++) {
            Point p=values.get(i);
            float xx=r.x+(float)Math.min(100,p.distance)/100*r.w;
            c.line(px,py,xx,py,1.5f,color);px=xx;
            if(xx>=r.x+r.w)break;
            if(i+1<values.size()) {
                float nextY=r.y+r.h-(float)(values.get(i+1).damage/max*r.h);
                c.line(px,py,px,nextY,1.5f,color);py=nextY;
            }
        }
        c.line(px,py,r.x+r.w,py,1.5f,color);
    }
    private static void footer(Canvas c,List<Hit> h,Data d,Theme t,double mx,double my) {
        int color=t==Theme.BF2042?TEAL:t==Theme.BF1?WHITE:SILVER;
        c.rect(0,510,960,30,t==Theme.BF1?0x522a3032:0xe5061216);
        button(c,h,"back",d.selectedType==null?"返回配装":"返回",new Rect(25,514,83,21),mx,my,color);
        button(c,h,"weapons","选择武器",new Rect(121,514,98,21),mx,my,color);
        button(c,h,"slot",d.slotLabel,new Rect(232,514,94,21),mx,my,color);
        button(c,h,"reset","中键  复位",new Rect(340,514,104,21),mx,my,color);
        text(c,"拖动旋转  ·  滚轮缩放",Face.TEXT,10,464,520,color);
        button(c,h,"theme","F6  "+t.name(),new Rect(833,514,105,21),mx,my,color);
    }
    private static void button(Canvas c,List<Hit> h,String action,String label,Rect r,double mx,double my,int color) {
        if(r.contains(mx,my))c.rect(r.x,r.y,r.w,r.h,0x385d777d);
        c.text(label,Face.TEXT,11,r.x+r.w/2,r.y+5,color,Align.CENTER);h.add(new Hit(action,r,true));
    }
    private static void back(Canvas c,List<Hit> h,float x,float y,double mx,double my,int color) {
        Rect r=new Rect(x,y,34,20);c.line(x+6,y+7,x+11,y+2,1.3f,color);c.line(x+6,y+7,x+11,y+12,1.3f,color);h.add(new Hit("back",r,true));
    }
    private static void status(Canvas c,String label,float x,float y,int color) {c.rect(x,y,76,19,0x50405c3a);outline(c,new Rect(x,y,76,19),color);text(c,label,Face.TEXT,12,x+16,y+3,color);}
    private static void lock(Canvas c,float x,float y,int color){c.rect(x,y+4,7,6,color);c.line(x+1,y+4,x+1,y,1,color);c.line(x+1,y,x+6,y,1,color);c.line(x+6,y,x+6,y+4,1,color);}
    private static void categoryIcon(Canvas c,Category cat,Rect r,int color){
        if(cat.itemKey!=null&&!cat.itemKey.isBlank()){c.item(cat.itemKey,r,color);return;}
        float cx=r.x+r.w/2,cy=r.y+r.h/2;
        if(!cat.available){lock(c,cx-3,cy-5,color);return;}
        c.rect(cx-6,cy-1,12,2,color);c.rect(cx-1,cy-6,2,12,color);
    }
    private static int equippedCount(Data d){return (int)d.categories.stream().filter(v->v.itemKey!=null&&!v.itemKey.isEmpty()).count();}
    private static Category category(Data d){return d.categories.stream().filter(v->v.id.equals(d.selectedType)).findFirst().orElse(new Category("","—","—","",false,0,0));}
    private static String previewLabel(Data d){return d.choices.stream().filter(v->v.id.equals(d.previewChoice)).map(Choice::label).findFirst().orElse(category(d).selectedName);}
    private static boolean previewEquipped(Data d){return d.choices.stream().filter(v->v.id.equals(d.previewChoice)).anyMatch(Choice::equipped);}
    private static void outline(Canvas c,Rect r,int color){c.rect(r.x,r.y,r.w,0.6f,color);c.rect(r.x,r.y+r.h-0.6f,r.w,0.6f,color);c.rect(r.x,r.y,0.6f,r.h,color);c.rect(r.x+r.w-0.6f,r.y,0.6f,r.h,color);}
    private static void text(Canvas c,String value,Face face,float size,float x,float y,int color){c.text(value==null?"—":value,face,size,x,y,color,Align.LEFT);}
    private static void clipped(Canvas c,String value,Face face,float size,float x,float y,float width,int color) {
        if(value==null)value="—";
        while(value.length()>1&&c.textWidth(value,face,size)>width)value=value.substring(0,value.offsetByCodePoints(value.length(),-1));
        text(c,value,face,size,x,y,color);
    }
}
