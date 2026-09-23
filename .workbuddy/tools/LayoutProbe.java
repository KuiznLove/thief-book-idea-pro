import javax.swing.*;
import java.awt.*;

/**
 * 布局探针：复刻 MainUi.initHeaderBar() / ChatInputBar.createToolbar() 的两栏结构，
 * 把每个控件的实际 bounds（以及垂直中心线）打印出来。
 * <p>
 * <b>为什么需要它</b>：FlowLayout 在"两栏容器等高、但栏内控件高度不一"时的垂直定位规则
 * 不好靠记忆确认（不同 JDK 版本/是否 baseline 对齐，结果不同）。肉眼看截图判断 1~4px 的
 * 错位极不可靠，而 bounds 是确定数字。
 * <p>
 * 用法：LayoutProbe [缩放倍率=1]
 * 例：  LayoutProbe 2     // 模拟 HiDPI 200%
 */
public class LayoutProbe {

    private static int S = 1;

    static class DummyIcon implements Icon {
        private final int size;

        DummyIcon(int logical) {
            this.size = logical * S;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
        }

        public int getIconWidth() {
            return size;
        }

        public int getIconHeight() {
            return size;
        }
    }

    static JButton glyphButton(Icon icon, boolean fixed) {
        JButton b = new JButton(icon);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBorder(BorderFactory.createEmptyBorder(2 * S, 2 * S, 2 * S, 2 * S));
        if (fixed) {
            b.setPreferredSize(new Dimension(24 * S, 22 * S));
        }
        return b;
    }

    static void layoutAll(Container c) {
        c.doLayout();
        for (Component k : c.getComponents()) {
            if (k instanceof Container) {
                layoutAll((Container) k);
            }
        }
    }

    /** 打印一个控件在 bar 坐标系里的位置与垂直中心线 */
    static void dump(String name, Component comp, Component origin) {
        Point p = SwingUtilities.convertPoint(comp.getParent(), comp.getLocation(), origin);
        System.out.printf("    %-10s y=%3d..%-3d (高%3d)  垂直中心=%6.1f   高(原生)=%d%n",
                name, p.y, p.y + comp.getHeight() - 1, comp.getHeight(),
                p.y + comp.getHeight() / 2.0, comp.getPreferredSize().height);
    }

    static void rule(String title) {
        System.out.println();
        System.out.println("=== " + title + "  (缩放 " + S + "x) ===");
    }

    public static void main(String[] args) {
        S = args.length > 0 ? Integer.parseInt(args[0]) : 1;

        rule("顶栏：左边 brand/侧栏/会话，右边 页码/上一条/下一条/喇叭");

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(7 * S, 14 * S, 4 * S, 10 * S));

        JLabel brand = new JLabel("CodePilot");
        brand.setIcon(new DummyIcon(17));
        brand.setIconTextGap(7 * S);
        brand.setFont(new Font("Dialog", Font.BOLD, 12 * S));
        brand.setForeground(Color.LIGHT_GRAY);

        JButton sidebar = glyphButton(new DummyIcon(15), true);

        JButton session = glyphButton(new DummyIcon(13), false);
        session.setBorder(BorderFactory.createEmptyBorder(2 * S, 6 * S, 2 * S, 4 * S));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8 * S, 0));
        left.setOpaque(false);
        left.add(brand);
        left.add(sidebar);
        left.add(session);

        JLabel page = new JLabel("2 / 9");
        page.setFont(new Font("Dialog", Font.PLAIN, 11 * S));
        page.setForeground(Color.GRAY);
        page.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6 * S));

        JButton boss = new JButton(" ");
        boss.setPreferredSize(new Dimension(5 * S, 5 * S));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2 * S, 0));
        right.setOpaque(false);
        right.add(page);
        right.add(glyphButton(new DummyIcon(15), true));
        right.add(glyphButton(new DummyIcon(15), true));
        right.add(glyphButton(new DummyIcon(15), true));
        right.add(boss);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);

        int width = 480 * S;
        bar.setSize(width, bar.getPreferredSize().height);
        layoutAll(bar);
        System.out.println("  bar 高度=" + bar.getHeight() + " (pref=" + bar.getPreferredSize().height + ")"
                + "  左栏高=" + left.getHeight() + "  右栏高=" + right.getHeight());

        dump("brand", brand, bar);
        dump("sidebar", sidebar, bar);
        dump("session", session, bar);
        dump("page", page, bar);
        dump("prev", right.getComponent(1), bar);
        dump("tts", right.getComponent(3), bar);

        rule("底栏工具行：左边 @/#/图片，右边 模型/发送");

        JPanel row = new JPanel(new BorderLayout());
        row.setBorder(BorderFactory.createEmptyBorder(0, 6 * S, 5 * S, 6 * S));

        JPanel rowLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 2 * S, 0));
        rowLeft.setOpaque(false);
        JButton at = glyphButton(null, true);
        at.setText("@");
        at.setFont(new Font("Dialog", Font.BOLD, 14 * S));
        JButton hash = glyphButton(null, true);
        hash.setText("#");
        hash.setFont(new Font("Dialog", Font.BOLD, 14 * S));
        JButton picture = glyphButton(new DummyIcon(15), true);
        rowLeft.add(at);
        rowLeft.add(hash);
        rowLeft.add(picture);

        JButton model = glyphButton(new DummyIcon(14), false);
        model.setText("Seed-Code");
        model.setFont(new Font("Dialog", Font.PLAIN, 11 * S));
        model.setBorder(BorderFactory.createEmptyBorder(2 * S, 6 * S, 2 * S, 4 * S));

        JButton send = new JButton();
        send.setPreferredSize(new Dimension(30 * S, 24 * S));

        JPanel rowRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6 * S, 0));
        rowRight.setOpaque(false);
        rowRight.add(model);
        rowRight.add(send);

        row.add(rowLeft, BorderLayout.WEST);
        row.add(rowRight, BorderLayout.EAST);

        JPanel box = new JPanel(new BorderLayout());
        box.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        box.add(row, BorderLayout.SOUTH);
        box.setSize(width, 200 * S);
        layoutAll(box);

        System.out.println("  row 高度=" + row.getHeight() + " (pref=" + row.getPreferredSize().height + ")"
                + "  左栏高=" + rowLeft.getHeight() + "  右栏高=" + rowRight.getHeight());

        dump("at", at, row);
        dump("hash", hash, row);
        dump("picture", picture, row);
        dump("model", model, row);
        dump("send", send, row);
    }
}
