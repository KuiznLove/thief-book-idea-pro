import com.thief.idea.ui.AssistantIcons;
import com.thief.idea.ui.AssistantTheme;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 只渲染卡片工具栏的三个图标（复制 / 下载 / 刷新）到一张放大 PNG，用于快速核对图标样式，
 * 不用跑完整的 UiPreview（它最近几次在本机上挂起）。
 * <p>
 * java -Dfile.encoding=UTF-8 -cp "<platform>/lib/*;build/classes/java/main" IconPreview.java 输出.png [放大倍数]
 **/
public class IconPreview {

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "icons.png";
        int zoom = args.length > 1 ? Integer.parseInt(args[1]) : 8;

        Icon[] icons = {
                AssistantIcons.copy(15, AssistantTheme.MUTED),
                AssistantIcons.download(15, AssistantTheme.MUTED),
                AssistantIcons.refresh(15, AssistantTheme.MUTED),
        };
        int cell = 30;
        BufferedImage image = new BufferedImage(icons.length * cell * zoom, cell * zoom,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.scale(zoom, zoom);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i < icons.length; i++) {
            int x = i * cell + (cell - icons[i].getIconWidth()) / 2;
            int y = (cell - icons[i].getIconHeight()) / 2;
            icons[i].paintIcon(null, g, x, y);
        }
        g.dispose();
        ImageIO.write(image, "png", new File(out));
        System.out.println("written: " + new File(out).getAbsolutePath());
        System.exit(0);
    }
}
