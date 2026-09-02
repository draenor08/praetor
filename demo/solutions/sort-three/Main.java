import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        StreamTokenizer in = new StreamTokenizer(new BufferedInputStream(System.in));
        long[] v = new long[3];
        for (int i = 0; i < 3; i++) {
            in.nextToken();
            v[i] = (long) in.nval;
        }
        Arrays.sort(v);
        System.out.println(v[0] + " " + v[1] + " " + v[2]);
    }
}
