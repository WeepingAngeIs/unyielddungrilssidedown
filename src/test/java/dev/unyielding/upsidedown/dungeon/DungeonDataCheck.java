package dev.unyielding.upsidedown.dungeon;
import java.util.*;
import java.io.IOException;

/** Exercises the production compressed geometry codec used by BOTH templates and rollback backups. */
public final class DungeonDataCheck {
    public static void main(String[] args)throws Exception{
        Random r=new Random(712271);
        for(int run=0;run<250;run++){
            int[] data=new int[1+r.nextInt(50000)];
            for(int i=0;i<data.length;i++)data[i]=(i>0&&r.nextInt(5)!=0)?data[i-1]:r.nextInt(32);
            int[] decoded=DungeonEncoding.decodeRuns(DungeonEncoding.encodeRuns(data),data.length,32);
            if(!Arrays.equals(data,decoded))throw new AssertionError("Backup geometry round-trip failed");
        }
        bad(new int[]{1},1,1);bad(new int[]{0,0},1,1);bad(new int[]{-1,0},1,1);
        bad(new int[]{1,-1},1,1);bad(new int[]{1,1},1,1);bad(new int[]{2,0},1,1);
        bad(new int[]{1,0},2,1);bad(new int[]{Integer.MAX_VALUE,0},2,1);
        System.out.println("PASS: 250 production template/backup RLE round-trips; eight malformed/overflow inputs rejected.");
    }
    private static void bad(int[] runs,int size,int palette)throws Exception{
        try{DungeonEncoding.decodeRuns(runs,size,palette);throw new AssertionError("Malformed geometry accepted");}
        catch(IOException expected){}
    }
}
