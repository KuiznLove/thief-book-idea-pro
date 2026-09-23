import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 墨迹分布量测：把一张截图/预览图按"亮度阈值"二值化，再按列聚类成一个个"元素"，
 * 打印每个元素的 x 范围、y 范围（墨迹包围盒）、垂直中心线与墨迹高度。
 * <p>
 * 用法：InkProfile &lt;图片&gt; [亮度阈值=90] [列间隙=4] [说明前缀] [y0] [y1] [light|dark]
 * 例：  InkProfile 顶栏.png 90 4 顶栏
 * 例：  InkProfile 整页.png 90 4 顶栏 0 66     // 只看 y=0..66 这一条（顶栏带）
 * 例：  InkProfile 预览.png 140 4 预览-顶栏 0 70 light   // 亮色主题：墨迹是"暗像素"
 * <p>
 * <b>用途</b>：核对一排按钮/图标是否垂直对齐。肉眼在缩略图上判断"差 1~2px"极不可靠
 * （图标糊、字重不一都会干扰），而"包围盒中心线"是客观数字：同一行的图标中心线应当一致，
 * 或相差不超过 1px。也可用来对照文字与图标的基线/中心。
 * <p>
 * y0/y1 用来把量测限制在一条横向带里——整张预览图里既有顶栏又有正文，不分带的话
 * 列聚类会把上下两处的内容混成一列，中心线就没意义了。
 * <p>
 * 最后那个参数选"墨迹是亮像素"还是"暗像素"：IDE 暗色主题截图里墨迹比背景亮（默认 dark），
 * 离线预览渲染的是亮色主题，墨迹比背景暗（传 light）。
 */
public class InkProfile {

    public static void main(String[] args) throws Exception {
        String file = args.length > 0 ? args[0] : "input.png";
        int threshold = args.length > 1 ? Integer.parseInt(args[1]) : 90;
        int gap = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        String label = args.length > 3 ? args[3] : new File(file).getName();
        boolean inkIsDark = args.length > 6 && "light".equalsIgnoreCase(args[6]);

        BufferedImage image = ImageIO.read(new File(file));
        if (image == null) {
            throw new IllegalArgumentException("读不了图片：" + file);
        }
        int w = image.getWidth();
        int fullH = image.getHeight();
        int y0Band = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        int y1Band = args.length > 5 ? Integer.parseInt(args[5]) : fullH - 1;
        y0Band = Math.max(0, y0Band);
        y1Band = Math.min(fullH - 1, y1Band);
        int h = y1Band - y0Band + 1;
        System.out.println("  (量测带 y=" + y0Band + ".." + y1Band + "，图高 " + fullH
                + "，墨迹=" + (inkIsDark ? "暗像素" : "亮像素") + ")");

        // 每列是否有墨迹，以及该列墨迹的 y 范围
        boolean[] inked = new boolean[w];
        int[] top = new int[w];
        int[] bottom = new int[w];
        for (int x = 0; x < w; x++) {
            top[x] = Integer.MAX_VALUE;
            bottom[x] = -1;
            for (int y = y0Band; y <= y1Band; y++) {
                int luma = luma(image.getRGB(x, y));
                if (inkIsDark ? luma <= threshold : luma > threshold) {
                    inked[x] = true;
                    top[x] = Math.min(top[x], y);
                    bottom[x] = Math.max(bottom[x], y);
                }
            }
        }

        System.out.println("=== " + label + "  " + w + "x" + fullH + "  阈值=" + threshold + " 列间隙=" + gap + " ===");
        int index = 0;
        int x = 0;
        while (x < w) {
            if (!inked[x]) {
                x++;
                continue;
            }
            int start = x;
            int end = x;
            int blank = 0;
            for (int i = x + 1; i < w; i++) {
                if (inked[i]) {
                    end = i;
                    blank = 0;
                } else if (++blank >= gap) {
                    break;
                }
            }
            int y0 = Integer.MAX_VALUE;
            int y1 = -1;
            int pixels = 0;
            for (int i = start; i <= end; i++) {
                if (inked[i]) {
                    y0 = Math.min(y0, top[i]);
                    y1 = Math.max(y1, bottom[i]);
                    pixels += bottom[i] - top[i] + 1;
                }
            }
            System.out.println(String.format("  #%-2d x=%3d..%-3d (宽%2d)  y=%3d..%-3d (高%2d)  "
                            + "垂直中心=%.1f  墨迹像素=%d",
                    index++, start, end, end - start + 1, y0, y1, y1 - y0 + 1, (y0 + y1) / 2.0, pixels));
            x = end + 1;
        }
    }

    /**
     * 亮度（粗略的相对亮度，够用：背景暗、图标灰、文字白）
     **/
    private static int luma(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }
}
