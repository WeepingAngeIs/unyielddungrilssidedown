package dev.unyielding.upsidedown.dungeon.block;

/** Pure right-handed quarter-turn geometry, in Minecraft model pixels (0..16). */
public final class MountGeometry {
    private MountGeometry() {}
    public static double[] point(double x, double y, double z, String mount) {
        return switch (mount) {
            case "up" -> new double[]{x,y,z};
            case "down" -> new double[]{x,16-y,16-z};
            case "north" -> new double[]{x,z,16-y};
            case "south" -> new double[]{x,16-z,y};
            case "east" -> new double[]{y,16-x,z};
            case "west" -> new double[]{16-y,x,z};
            default -> throw new IllegalArgumentException("Unknown mount: " + mount);
        };
    }
    public static double[] box(double[] box, String mount) {
        double[] a = point(box[0],box[1],box[2],mount);
        double[] b = point(box[3],box[4],box[5],mount);
        return new double[]{Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.min(a[2],b[2]),
                Math.max(a[0],b[0]),Math.max(a[1],b[1]),Math.max(a[2],b[2])};
    }
}
