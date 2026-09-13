import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 局部放大核对工具：从预览 PNG 里裁一块并放大，用来检查缩略图上看不清的排版细节
 * （文件名有没有被工具栏压住、角标与文字是否对齐、窄宽度下降级是否生效）。
 *
 * <p>用法：
 * {@code java -Dfile.encoding=UTF-8 .workbuddy/tools/PngCrop.java 源图.png 输出.png <x> <y> <宽> <高> [放大倍数]}
 *
 * <p>换算提醒：UiPreview 输出的 PNG 是 **2x 缩放**（窗口写 560 宽，PNG 是 1120px 宽）。
 * 分布统计里打印的卡片 y 坐标是**页面内**的，换算成像素要乘 2 再加上顶栏高度；
 * 左侧栏展开时占 190 逻辑像素（= 380 像素），正文区从 x=380 起。
 **/
public final class PngCrop {
    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.out.println("usage: PngCrop.java <src.png> <dst.png> <x> <y> <w> <h> [scale]");
            return;
        }
        String src = args[0];
        String dst = args[1];
        int x = Integer.parseInt(args[2]);
        int y = Integer.parseInt(args[3]);
        int w = Integer.parseInt(args[4]);
        int h = Integer.parseInt(args[5]);
        int scale = args.length > 6 ? Integer.parseInt(args[6]) : 1;

        BufferedImage in = ImageIO.read(new File(src));
        if (in == null) {
            System.out.println("cannot read " + src);
            return;
        }
        w = Math.min(w, in.getWidth() - x);
        h = Math.min(h, in.getHeight() - y);
        if (w <= 0 || h <= 0) {
            System.out.println("crop area outside image " + in.getWidth() + "x" + in.getHeight());
            return;
        }
        BufferedImage crop = in.getSubimage(x, y, w, h);

        BufferedImage out = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(crop, 0, 0, w * scale, h * scale, null);
        g.dispose();
        ImageIO.write(out, "png", new File(dst));
        System.out.println("cropped " + w + "x" + h + " -> " + (w * scale) + "x" + (h * scale) + " : " + dst);
    }
}
