package org.mods.gd656conquest.client.event.handler;

import java.util.*;

/** Triangulates one simple polygon; preserves the original vertex indices for camera updates. */
final class DeploymentPolygon {
    record Point(double x,double y) {}
    private DeploymentPolygon() {}
    static int[] triangles(List<Point> points){
        if(points.size()<3)return new int[0];
        List<Integer> remaining=new ArrayList<>();
        for(int i=0;i<points.size();i++)if(remaining.isEmpty()||distance(points.get(i),points.get(remaining.get(remaining.size()-1)))>1e-12)remaining.add(i);
        if(remaining.size()>2&&distance(points.get(remaining.get(0)),points.get(remaining.get(remaining.size()-1)))<=1e-12)remaining.remove(remaining.size()-1);
        double area=0;for(int i=0;i<remaining.size();i++){Point a=points.get(remaining.get(i)),b=points.get(remaining.get((i+1)%remaining.size()));area+=a.x*b.y-b.x*a.y;}
        if(Math.abs(area)<1e-10)return new int[0];double sign=Math.signum(area);
        List<Integer> result=new ArrayList<>();
        while(remaining.size()>3){
            boolean removed=false;
            for(int i=0;i<remaining.size();i++){
                int a=remaining.get((i+remaining.size()-1)%remaining.size()),b=remaining.get(i),c=remaining.get((i+1)%remaining.size());
                double turn=cross(points.get(a),points.get(b),points.get(c))*sign;
                if(Math.abs(turn)<1e-10){remaining.remove(i);removed=true;break;}
                if(turn<0)continue;
                boolean contains=false;
                for(int p:remaining)if(p!=a&&p!=b&&p!=c&&inside(points.get(p),points.get(a),points.get(b),points.get(c),sign)){contains=true;break;}
                if(contains)continue;
                result.add(a);result.add(b);result.add(c);remaining.remove(i);removed=true;break;
            }
            if(!removed)throw new IllegalArgumentException("Deployment boundary is not a simple polygon");
        }
        if(remaining.size()==3)result.addAll(remaining);
        return result.stream().mapToInt(Integer::intValue).toArray();
    }
    private static double distance(Point a,Point b){return (a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y);}
    private static double cross(Point a,Point b,Point c){return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);}
    private static boolean inside(Point p,Point a,Point b,Point c,double sign){return cross(a,b,p)*sign>=-1e-10&&cross(b,c,p)*sign>=-1e-10&&cross(c,a,p)*sign>=-1e-10;}
}
