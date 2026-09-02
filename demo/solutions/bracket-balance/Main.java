import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String s = in.readLine().trim();

        Deque<Character> open = new ArrayDeque<>();
        boolean ok = true;
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                open.push(c);
            } else {
                char want = c == ')' ? '(' : (c == ']' ? '[' : '{');
                if (open.isEmpty() || open.pop() != want) { ok = false; break; }
            }
        }
        System.out.println(ok && open.isEmpty() ? "YES" : "NO");
    }
}
