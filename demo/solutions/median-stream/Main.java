import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        StreamTokenizer in = new StreamTokenizer(new BufferedInputStream(System.in));
        in.nextToken();
        int n = (int) in.nval;

        PriorityQueue<Long> low = new PriorityQueue<>(Comparator.reverseOrder());
        PriorityQueue<Long> high = new PriorityQueue<>();

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) {
            in.nextToken();
            long x = (long) in.nval;

            if (low.isEmpty() || x <= low.peek()) low.add(x);
            else high.add(x);

            if (low.size() > high.size() + 1) high.add(low.poll());
            else if (high.size() > low.size()) low.add(high.poll());

            out.append(low.peek()).append('\n');
        }
        System.out.print(out);
    }
}
