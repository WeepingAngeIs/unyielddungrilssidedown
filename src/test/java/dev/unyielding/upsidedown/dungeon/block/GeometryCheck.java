package dev.unyielding.upsidedown.dungeon.block;

/** Dependency-free executable checks of the production rotation implementation. */
public final class GeometryCheck {
    private static final String[] MOUNTS = {"up","down","north","south","east","west"};
    private static final String[] INVERSES = {"up","down","south","north","west","east"};
    private static final double[][] NORMALS = {{0,1,0},{0,-1,0},{0,0,-1},{0,0,1},{1,0,0},{-1,0,0}};
    public static void main(String[] args) {
        int checks = 0;
        for (int index=0; index<MOUNTS.length; index++) {
            String mount = MOUNTS[index];
            double[] center=MountGeometry.point(8,8,8,mount), tip=MountGeometry.point(8,9,8,mount);
            for (int axis=0; axis<3; axis++) equal(tip[axis]-center[axis],NORMALS[index][axis]);
            // Check an asymmetric point and cuboid so swaps/reflections cannot pass accidentally.
            double[] p=MountGeometry.point(2,5,11,mount);
            double[] back=MountGeometry.point(p[0],p[1],p[2],INVERSES[index]);
            equal(back[0],2);equal(back[1],5);equal(back[2],11);
            double[][][] furniture={DungeonGeometry.TEA_TABLE,DungeonGeometry.FLOWER_PLANTER};
            for (double[][] geometry:furniture) for (double[] box:geometry) {
                double[] rotated=MountGeometry.box(box,mount);
                double[] restored=MountGeometry.box(rotated,INVERSES[index]);
                double before=1,after=1;
                for (int axis=0; axis<3; axis++) {
                    require(rotated[axis]>=0 && rotated[axis+3]<=16,"Out-of-block collision");
                    require(rotated[axis+3]>rotated[axis],"Degenerate collision");
                    before*=box[axis+3]-box[axis]; after*=rotated[axis+3]-rotated[axis];
                    equal(restored[axis],box[axis]);equal(restored[axis+3],box[axis+3]);
                }
                equal(before,after);checks++;
            }
        }
        boolean rejected=false;
        try { MountGeometry.point(0,0,0,"invalid"); } catch(IllegalArgumentException expected) { rejected=true; }
        require(rejected,"Invalid orientation accepted");
        System.out.println("PASS: "+checks+" oriented furniture cuboids; all six outward normals, inverse rotations, bounds and volumes.");
    }
    private static void equal(double actual,double expected) {require(Math.abs(actual-expected)<1e-9,"Expected "+expected+", got "+actual);}
    private static void require(boolean test,String message) {if(!test)throw new AssertionError(message);}
}
