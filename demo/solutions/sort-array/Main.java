import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        StreamTokenizer in = new StreamTokenizer(new BufferedInputStream(System.in));
        in.nextToken();
        int n = (int) in.nval;
        long[] v = new long[n];
        for (int i = 0; i < n; i++) {
            in.nextToken();
            v[i] = (long) in.nval;
        }
        Arrays.sort(v);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) {
            out.append(v[i]).append(i + 1 < n ? ' ' : '\n');
        }
        System.out.print(out);
    }
}
