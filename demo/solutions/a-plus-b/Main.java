import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        StreamTokenizer in = new StreamTokenizer(new BufferedInputStream(System.in));
        in.nextToken();
        long a = (long) in.nval;
        in.nextToken();
        long b = (long) in.nval;
        System.out.println(a + b);
    }
}
