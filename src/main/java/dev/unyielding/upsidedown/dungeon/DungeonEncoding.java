package dev.unyielding.upsidedown.dungeon;
import java.io.IOException;
import java.util.Arrays;

/** Bounded run-length codec shared by authored templates and original-terrain backups. */
public final class DungeonEncoding {
    private DungeonEncoding(){}
    public static int[] decodeRuns(int[] runs,int volume,int paletteSize) throws IOException {
        if((runs.length&1)!=0)throw new IOException("Truncated block runs");
        int[] result=new int[volume];int cursor=0;
        for(int i=0;i<runs.length;i+=2) {
            int n=runs[i],state=runs[i+1];
            if(n<=0||state<0||state>=paletteSize||(long)cursor+n>volume)throw new IOException("Invalid block run");
            Arrays.fill(result,cursor,cursor+n,state);cursor+=n;
        }
        if(cursor!=volume)throw new IOException("Incomplete dungeon geometry");
        return result;
    }
    public static int[] encodeRuns(int[] data) {
        if(data.length==0)return new int[0];
        int runs=1;for(int i=1;i<data.length;i++)if(data[i]!=data[i-1])runs++;
        int[] out=new int[runs*2];int p=0,n=1;
        for(int i=1;i<=data.length;i++) {
            if(i<data.length&&data[i]==data[i-1])n++;
            else {out[p++]=n;out[p++]=data[i-1];n=1;}
        }
        return out;
    }
}
