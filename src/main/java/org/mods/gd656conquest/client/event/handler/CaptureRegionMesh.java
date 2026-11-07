package org.mods.gd656conquest.client.event.handler;

import java.util.*;

/** Cached top envelope of the actual capture volume; holes and disconnected islands are retained. */
final class CaptureRegionMesh {
    record Cell(int x,int y,int z) {}
    record Point(double x,double y,double z) {}
    record Quad(Point a,Point b,Point c,Point d) {}
    record Edge(Point a,Point b) {}
    record Region(String code,int blockCount,int minY,int maxY,List<Quad> faces,List<Edge> edges) {}
    private record Column(int x,int z) {}
    private record Row(int y,int z) {}
    private CaptureRegionMesh() {}
    static List<Region> build(Map<Cell,String> blocks){
        Map<String,Map<Column,Integer>> columns=new TreeMap<>();Map<String,Integer> counts=new HashMap<>(),min=new HashMap<>(),max=new HashMap<>();
        blocks.forEach((p,code)->{columns.computeIfAbsent(code,k->new HashMap<>()).merge(new Column(p.x,p.z),p.y,Math::max);counts.merge(code,1,Integer::sum);min.merge(code,p.y,Math::min);max.merge(code,p.y,Math::max);});
        List<Region> result=new ArrayList<>();
        columns.forEach((code,map)->{
            Map<Row,TreeSet<Integer>> rows=new HashMap<>();List<Edge> edges=new ArrayList<>();
            map.forEach((c,y)->{
                rows.computeIfAbsent(new Row(y,c.z),k->new TreeSet<>()).add(c.x);double top=y+1.025;
                if(!map.containsKey(new Column(c.x-1,c.z)))edges.add(new Edge(new Point(c.x,top,c.z),new Point(c.x,top,c.z+1)));
                if(!map.containsKey(new Column(c.x+1,c.z)))edges.add(new Edge(new Point(c.x+1,top,c.z+1),new Point(c.x+1,top,c.z)));
                if(!map.containsKey(new Column(c.x,c.z-1)))edges.add(new Edge(new Point(c.x+1,top,c.z),new Point(c.x,top,c.z)));
                if(!map.containsKey(new Column(c.x,c.z+1)))edges.add(new Edge(new Point(c.x,top,c.z+1),new Point(c.x+1,top,c.z+1)));
            });
            List<Quad> faces=new ArrayList<>();
            rows.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<Row,TreeSet<Integer>> e)->e.getKey().y).thenComparingInt(e->e.getKey().z)).forEach(entry->{
                int start=Integer.MIN_VALUE,previous=Integer.MIN_VALUE;Row r=entry.getKey();
                for(int x:entry.getValue()){if(start==Integer.MIN_VALUE)start=x;else if(x!=previous+1){faces.add(face(start,previous+1,r));start=x;}previous=x;}
                if(start!=Integer.MIN_VALUE)faces.add(face(start,previous+1,r));
            });
            result.add(new Region(code,counts.get(code),min.get(code),max.get(code),List.copyOf(faces),List.copyOf(edges)));
        });return List.copyOf(result);
    }
    private static Quad face(int x1,int x2,Row r){double y=r.y+1.025;return new Quad(new Point(x1,y,r.z),new Point(x2,y,r.z),new Point(x2,y,r.z+1),new Point(x1,y,r.z+1));}
}
