import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.thief.idea.ui.AssistantIcons;
import com.thief.idea.ui.AssistantTheme;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 全部图标总览：把 {@link AssistantIcons} 里每个图标按"名称 + 两种颜色 × 两种尺寸"画成一张
 * 放大 PNG，用来一眼核对形状是否统一、以及**在指定 DPI 下是否发糊**。
 * <p>
 * 与 {@code IconPreview} 的分工：那个只把工具栏三个图标（复制/下载/刷新）放大到 8 倍看细节，
 * 这个看全量。**换图标素材、改 tint 逻辑后都该跑一次**，而且 HiDPI 缩放出问题时只在 2x 下才
 * 看得出来，所以两种 scale 都要跑：
 * <pre>
 * java -Dfile.encoding=UTF-8 -cp "&lt;平台目录&gt;/lib/*;&lt;classes&gt;;src/main/resources" \
 *   .workbuddy/tools/IconGallery.java 输出.png 2 1     # 最后一个是 DPI 因子：1 或 2
 * </pre>
 **/
public class IconGallery {

    private static final int ROW_H = 46;
    private static final int LABEL_W = 170;
    private static final int COL_GAP = 60;
    private static final int PAD = 16;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "icons.png";
        int zoom = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        float scale = args.length > 2 ? Float.parseFloat(args[2]) : 1f;
        JBUIScale.setSystemScaleFactor(scale);

        int s20 = JBUI.scale(20);
        int s34 = JBUI.scale(34);

        List<Object[]> rows = new ArrayList<>();
        add(rows, "arrowUp 上箭头", AssistantIcons.arrowUp(s20, AssistantTheme.MUTED),
                AssistantIcons.arrowUp(s20, AssistantTheme.ACCENT),
                AssistantIcons.arrowUp(s34, AssistantTheme.TEXT),
                AssistantIcons.arrowUp(s34, AssistantTheme.MUTED));
        add(rows, "copy 复制", AssistantIcons.copy(s20, AssistantTheme.MUTED),
                AssistantIcons.copy(s20, AssistantTheme.ACCENT),
                AssistantIcons.copy(s34, AssistantTheme.TEXT),
                AssistantIcons.copy(s34, AssistantTheme.MUTED));
        add(rows, "download 导出", AssistantIcons.download(s20, AssistantTheme.MUTED),
                AssistantIcons.download(s20, AssistantTheme.ACCENT),
                AssistantIcons.download(s34, AssistantTheme.TEXT),
                AssistantIcons.download(s34, AssistantTheme.MUTED));
        add(rows, "refresh 重新生成", AssistantIcons.refresh(s20, AssistantTheme.MUTED),
                AssistantIcons.refresh(s20, AssistantTheme.ACCENT),
                AssistantIcons.refresh(s34, AssistantTheme.TEXT),
                AssistantIcons.refresh(s34, AssistantTheme.MUTED));
        add(rows, "check = Apply", AssistantIcons.check(s20, AssistantTheme.MUTED),
                AssistantIcons.check(s20, AssistantTheme.ACCENT),
                AssistantIcons.check(s34, AssistantTheme.ACCENT),
                AssistantIcons.check(s34, AssistantTheme.MUTED));
        add(rows, "play = Run", AssistantIcons.play(s20, AssistantTheme.MUTED),
                AssistantIcons.play(s20, AssistantTheme.ACCENT),
                AssistantIcons.play(s34, AssistantTheme.ACCENT),
                AssistantIcons.play(s34, AssistantTheme.MUTED));
        add(rows, "chevronDown", AssistantIcons.chevronDown(s20, AssistantTheme.MUTED),
                AssistantIcons.chevronDown(s20, AssistantTheme.ACCENT),
                AssistantIcons.chevronDown(s34, AssistantTheme.TEXT),
                AssistantIcons.chevronDown(s34, AssistantTheme.MUTED));
        add(rows, "chevronLeft", AssistantIcons.chevronLeft(s20, AssistantTheme.MUTED),
                AssistantIcons.chevronLeft(s20, AssistantTheme.ACCENT),
                AssistantIcons.chevronLeft(s34, AssistantTheme.TEXT),
                AssistantIcons.chevronLeft(s34, AssistantTheme.MUTED));
        add(rows, "chevronRight", AssistantIcons.chevronRight(s20, AssistantTheme.MUTED),
                AssistantIcons.chevronRight(s20, AssistantTheme.ACCENT),
                AssistantIcons.chevronRight(s34, AssistantTheme.TEXT),
                AssistantIcons.chevronRight(s34, AssistantTheme.MUTED));
        add(rows, "image 插图", AssistantIcons.image(s20, AssistantTheme.MUTED),
                AssistantIcons.image(s20, AssistantTheme.ACCENT),
                AssistantIcons.image(s34, AssistantTheme.TEXT),
                AssistantIcons.image(s34, AssistantTheme.MUTED));
        add(rows, "sidebar 展开中", AssistantIcons.sidebar(s20, AssistantTheme.MUTED, true),
                AssistantIcons.sidebar(s20, AssistantTheme.ACCENT, true),
                AssistantIcons.sidebar(s34, AssistantTheme.TEXT, true),
                AssistantIcons.sidebar(s34, AssistantTheme.MUTED, true));
        add(rows, "sidebar 已收起", AssistantIcons.sidebar(s20, AssistantTheme.MUTED, false),
                AssistantIcons.sidebar(s20, AssistantTheme.ACCENT, false),
                AssistantIcons.sidebar(s34, AssistantTheme.TEXT, false),
                AssistantIcons.sidebar(s34, AssistantTheme.MUTED, false));
        add(rows, "speaker 未朗读", AssistantIcons.speaker(s20, AssistantTheme.MUTED, false),
                AssistantIcons.speaker(s20, AssistantTheme.ACCENT, false),
                AssistantIcons.speaker(s34, AssistantTheme.TEXT, false),
                AssistantIcons.speaker(s34, AssistantTheme.MUTED, false));
        add(rows, "speaker 朗读中", AssistantIcons.speaker(s20, AssistantTheme.MUTED, true),
                AssistantIcons.speaker(s20, AssistantTheme.ACCENT, true),
                AssistantIcons.speaker(s34, AssistantTheme.ACCENT, true),
                AssistantIcons.speaker(s34, AssistantTheme.MUTED, true));
        add(rows, "brand（手绘）", AssistantIcons.brand(s20), AssistantIcons.brand(s20),
                AssistantIcons.brand(s34), AssistantIcons.brand(s34));
        add(rows, "fileBadge（手绘）",
                AssistantIcons.fileBadge("PY", AssistantTheme.CHIP_BG, AssistantTheme.CHIP_TEXT, s20),
                AssistantIcons.fileBadge("YAML", AssistantTheme.CHIP_BG, AssistantTheme.CHIP_TEXT, s20),
                AssistantIcons.fileBadge("JAVA", AssistantTheme.CHIP_BG, AssistantTheme.CHIP_TEXT, s34),
                AssistantIcons.fileBadge("SH", AssistantTheme.CHIP_BG, AssistantTheme.CHIP_TEXT, s34));

        int width = LABEL_W + COL_GAP * 4 + PAD * 2;
        int height = rows.size() * ROW_H + PAD * 2 + 24;

        BufferedImage image = new BufferedImage(width * zoom, height * zoom, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.scale(zoom, zoom);
        g.setColor(new Color(0xFAFBFC));
        g.fillRect(0, 0, width, height);

        Font label = AssistantTheme.uiFont(12);
        Font head = AssistantTheme.uiFont(11);
        g.setColor(new Color(0x9AA3AD));
        g.setFont(head);
        g.drawString("DPI×" + scale + "  " + s20 + "px 次要", LABEL_W, 18);
        g.drawString(s20 + "px 强调", LABEL_W + COL_GAP, 18);
        g.drawString(s34 + "px 正文", LABEL_W + COL_GAP * 2, 18);
        g.drawString(s34 + "px 次要", LABEL_W + COL_GAP * 3, 18);

        int y = PAD + 24;
        for (Object[] row : rows) {
            boolean sep = ((String) row[0]).contains("手绘");
            if (sep) {
                g.setColor(new Color(0xE3E6EA));
                g.drawLine(PAD, y - 6, width - PAD, y - 6);
            }
            g.setColor(new Color(0x3A4048));
            g.setFont(label);
            FontMetrics fm = g.getFontMetrics();
            g.drawString((String) row[0], PAD, y + ROW_H / 2 + fm.getAscent() / 2 - 3);
            for (int col = 1; col <= 4; col++) {
                Icon icon = (Icon) row[col];
                int cx = LABEL_W + COL_GAP * (col - 1) + (COL_GAP - icon.getIconWidth()) / 2;
                int cy = y + (ROW_H - icon.getIconHeight()) / 2;
                icon.paintIcon(null, g, cx, cy);
            }
            y += ROW_H;
        }
        g.dispose();

        ImageIO.write(image, "png", new File(out));
        System.out.println("written: " + new File(out).getAbsolutePath() + "  DPI=" + scale
                + "  逻辑尺寸 " + s20 + "/" + s34 + "px  →  文件 " + width * zoom + "x" + height * zoom);
    }

    private static void add(List<Object[]> rows, String name, Icon a, Icon b, Icon c, Icon d) {
        rows.add(new Object[]{name, a, b, c, d});
    }
}
