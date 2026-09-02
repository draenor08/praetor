import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        StreamTokenizer in = new StreamTokenizer(new BufferedInputStream(System.in));
        in.nextToken();
        int n = (int) in.nval;
        in.nextToken();
        long t = (long) in.nval;

        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            in.nextToken();
            long x = (long) in.nval;
            if (seen.contains(t - x)) {
                System.out.println("YES");
                return;
            }
            seen.add(x);
        }
        System.out.println("NO");
    }
}
