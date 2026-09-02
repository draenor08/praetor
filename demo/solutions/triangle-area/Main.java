import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        StreamTokenizer in = new StreamTokenizer(new BufferedInputStream(System.in));
        in.nextToken();
        double b = in.nval;
        in.nextToken();
        double h = in.nval;
        System.out.printf("%.8f%n", b * h / 2.0);
    }
}
