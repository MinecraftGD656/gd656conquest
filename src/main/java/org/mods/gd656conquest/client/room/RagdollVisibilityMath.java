package org.mods.gd656conquest.client.room;

/** Side-plane test with no fixed far-distance cutoff. Uses a conservative bounding sphere. */
public final class RagdollVisibilityMath {
    private RagdollVisibilityMath() {}
    public static boolean inView(double x,double y,double z,double radius,
                                 double fx,double fy,double fz,double ux,double uy,double uz,
                                 double lx,double ly,double lz,double tanY,double aspect) {
        double depth=x*fx+y*fy+z*fz, vertical=x*ux+y*uy+z*uz, horizontal=x*lx+y*ly+z*lz;
        double tanX=tanY*aspect;
        return depth+radius>0 && Math.abs(vertical)<=depth*tanY+radius*Math.sqrt(1+tanY*tanY)
            && Math.abs(horizontal)<=depth*tanX+radius*Math.sqrt(1+tanX*tanX);
    }
}
