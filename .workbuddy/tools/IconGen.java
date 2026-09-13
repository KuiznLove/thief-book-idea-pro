import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 生成工具窗口用的"AI 助手"图标：绿色圆角方块 + 白色四角星。
 * 一次生成 icons/assistant.png（16x16）与 icons/assistant@2x.png（32x32）。
 * 运行：E:/Java/jdk-17/bin/java.exe IconGen.java
 **/
public class IconGen {

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "src/main/resources/icons";
        write(new File(outDir, "assistant.png"), 16);
        write(new File(outDir, "assistant@2x.png"), 32);
        System.out.println("icons written to " + new File(outDir).getAbsolutePath());
    }

    private static void write(File file, int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        float u = size / 16f;
        // 圆角方块底色（顶部稍亮的竖向渐变）
        g.setPaint(new GradientPaint(0, 0, new Color(0x3FB57C), 0, size, new Color(0x248A5B)));
        g.fill(new RoundRectangle2D.Float(0.5f * u, 0.5f * u, 15 * u, 15 * u, 4.5f * u, 4.5f * u));

        // 白色四角星
        g.setColor(Color.WHITE);
        Path2D star = new Path2D.Float();
        float cx = 8 * u;
        float cy = 8 * u;
        float outer = 5.1f * u;
        float inner = 1.6f * u;
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(-90 + i * 45);
            float r = i % 2 == 0 ? outer : inner;
            float x = (float) (cx + r * Math.cos(angle));
            float y = (float) (cy + r * Math.sin(angle));
            if (i == 0) {
                star.moveTo(x, y);
            } else {
                star.lineTo(x, y);
            }
        }
        star.closePath();
        g.fill(star);
        g.dispose();
        ImageIO.write(image, "png", file);
    }
}
