import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        StreamTokenizer in = new StreamTokenizer(new BufferedInputStream(System.in));
        long best = Long.MIN_VALUE;
        for (int i = 0; i < 3; i++) {
            in.nextToken();
            best = Math.max(best, (long) in.nval);
        }
        System.out.println(best);
    }
}
