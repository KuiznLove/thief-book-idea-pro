import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 两张预览 PNG 的像素级差异报告：不同像素数、差异包围盒、以及差异集中的纵向区间。
 * <p>
 * 改排版后用它确认"改动只影响了预期的区域"——肉眼扫缩略图看不出几个像素的位移，
 * 但位移累计起来会让人以为卡片位置变了。
 * <p>
 * 用法：
 * <pre>
 * java -Dfile.encoding=UTF-8 .workbuddy/tools/PngDiff.java a.png b.png
 * </pre>
 * 退出码 0 = 完全一致。
 **/
public class PngDiff {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("用法: PngDiff.java a.png b.png");
            System.exit(2);
        }
        BufferedImage a = ImageIO.read(new File(args[0]));
        BufferedImage b = ImageIO.read(new File(args[1]));
        if (a == null || b == null) {
            System.out.println("[diff] 读图失败");
            System.exit(2);
        }
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            System.out.printf("[diff] 尺寸不同: %dx%d vs %dx%d%n",
                    a.getWidth(), a.getHeight(), b.getWidth(), b.getHeight());
            System.exit(1);
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        long diff = 0;
        // 差异像素按 y 行聚类，方便看出"差异集中在哪一条带里"
        Map<Integer, Integer> rows = new TreeMap<>();
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    diff++;
                    rows.merge(y, 1, Integer::sum);
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (diff == 0) {
            System.out.println("[diff] 完全一致");
            System.exit(0);
        }

        System.out.printf("[diff] 不同像素 = %d / %d (%.3f%%)%n",
                diff, (long) a.getWidth() * a.getHeight(),
                diff * 100.0 / ((long) a.getWidth() * a.getHeight()));
        System.out.printf("[diff] 包围盒 x=%d..%d y=%d..%d（图 %dx%d，2x 缩放，除以 2 得逻辑坐标）%n",
                minX, maxX, minY, maxY, a.getWidth(), a.getHeight());

        // 把连续行合并成"差异带"
        List<int[]> bands = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : rows.entrySet()) {
            int y = entry.getKey();
            if (!bands.isEmpty() && y == bands.get(bands.size() - 1)[1] + 1) {
                bands.get(bands.size() - 1)[1] = y;
                bands.get(bands.size() - 1)[2] += entry.getValue();
            } else {
                bands.add(new int[]{y, y, entry.getValue()});
            }
        }
        System.out.println("[diff] 差异带（y0~y1 / 像素数）:");
        for (int[] band : bands) {
            System.out.printf("         y=%4d~%4d  像素=%d%n", band[0], band[1], band[2]);
        }
        System.exit(1);
    }
}
