import android.opengl.ETC1;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
public final class SenseEtc1Reference {
    public static void main(String[] args) throws Exception {
        byte[] raw = Files.readAllBytes(Path.of(args[0]));
        int width = Integer.parseInt(args[1]), height = Integer.parseInt(args[2]);
        byte[] result = new byte[width * height * 3 * 2];
        for (int plane = 0; plane < 2; plane++) {
            ByteBuffer input = ByteBuffer.allocateDirect(raw.length / 2);
            input.put(raw, plane * raw.length / 2, raw.length / 2).rewind();
            ByteBuffer output = ByteBuffer.allocateDirect(width * height * 3);
            ETC1.decodeImage(input, output, width, height, 3, width * 3);
            output.rewind();
            output.get(result, plane * width * height * 3, width * height * 3);
        }
        Files.write(Path.of(args[3]), result);
        System.out.println("Decoded both ETC1 planes with Android ETC1.decodeImage: " + result.length + " bytes");
    }
}
