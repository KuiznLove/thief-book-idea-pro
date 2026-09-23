package com.thief.idea.book;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件分页引擎：按行分页读取 {@link BookSource} 提供的文本，维护页码（= 已读过的行数）
 * 与文件指针缓存（原 MainUi.readBook / countLine / countSeek / readLines）。
 * <p>
 * <b>线程模型</b>：翻页不是单步操作，而是"（可能重定位）+ 读取一页 + 推进页码"的复合操作。
 * 所有公开操作都在内部同一把锁上整体串行——手动翻页（后台线程池）与朗读取页（TTS 线程）
 * 交错调用时，页码/指针要么整体推进一次、要么排队执行，不会出现"两个线程各推进半步"
 * 导致跳页。旧实现只给单个方法加 synchronized，方法间隙仍可交错，这里修正。
 * <p>
 * <b>页码约定</b>（与旧实现一致，勿改）：{@code currentPage} 表示"已读过的行数"，
 * 即当前页最后一行之后的行号；0 表示还没读过任何行。displayPage = currentPage / 每页行数。
 * <p>
 * <b>读取方式</b>：按 8KB 字节块读取切行（处理跨块残行、\r\n、末尾无换行行），
 * 不要退回 RandomAccessFile.readLine() 逐行读（慢且带 ISO-8859-1 往返）。
 **/
public final class BookPager {

    /**
     * 指针缓存间隔：每这么多行缓存一个文件指针。
     * 该值越小跳页越快，但内存占用增大
     **/
    private static final int CACHE_INTERVAL = 200;

    private final BookSource source;

    /**
     * 页码 -> 文件指针缓存，避免跳页时从头扫描。
     * 多线程访问（IO 线程 / TTS 线程 / EDT 清理），用 ConcurrentHashMap；
     * 只做点查询与整体清空，不依赖迭代顺序
     **/
    private final Map<Integer, Long> seekDictionary = new ConcurrentHashMap<>();

    /**
     * 复合操作锁：翻页/跳页/统计的整个临界区（定位 + 读取 + 推进页码）都在这把锁里
     **/
    private final Object lock = new Object();

    /**
     * 每页行数 / 行间距（设置页可改）。EDT 写、IO 线程读，volatile 保证可见性；
     * 不参与任何复合不变量，不加锁——否则设置页 Apply 时 EDT 会被正在进行的
     * 全量行数扫描 / epub 解包阻塞数秒
     **/
    private volatile int linesPerPage = 1;
    private volatile int lineSpacing = 0;

    /**
     * 当前文件指针（仅持锁访问）
     **/
    private long seek = 0;

    /**
     * 已读过的行数（页码约定见类注释；EDT/TTS 线程也要读，volatile 保证可见性）
     **/
    private volatile int currentPage = 0;

    /**
     * 当前文件总行数（可能尚未统计，为 0）
     **/
    private volatile int totalLines = 0;

    public BookPager(BookSource source) {
        this.source = source;
    }

    public int currentPage() {
        return currentPage;
    }

    public int totalLines() {
        return totalLines;
    }

    /**
     * 恢复阅读进度（行数），值来自按书独立保存的配置
     **/
    public void setCurrentPage(int line) {
        currentPage = Math.max(0, line);
    }

    public void setLinesPerPage(int count) {
        linesPerPage = Math.max(1, count);
    }

    public void setLineSpacing(int spacing) {
        lineSpacing = Math.max(0, spacing);
    }

    /**
     * 统计文件总行数并填充指针缓存（全量扫描，仅切书/首次加载时调用）
     **/
    public int countLines() throws IOException {
        synchronized (lock) {
            try (RandomAccessFile ra = new RandomAccessFile(resolvedPath(), "r")) {
                int i = 0;
                seekDictionary.put(0, ra.getFilePointer());
                byte[] buf = new byte[8192];
                int n;
                boolean any = false;
                boolean lastByteNewline = false;
                while ((n = ra.read(buf)) != -1) {
                    any = true;
                    lastByteNewline = buf[n - 1] == '\n';
                    long blockStart = ra.getFilePointer() - n;
                    for (int j = 0; j < n; j++) {
                        if (buf[j] == '\n') {
                            i++;
                            if (i % CACHE_INTERVAL == 0) {
                                seekDictionary.put(i, blockStart + j + 1);
                            }
                        }
                    }
                }
                // 末行无换行结尾时补计一行，与 readLine 语义一致
                if (any && !lastByteNewline) {
                    i++;
                }
                totalLines = i;
                return i;
            }
        }
    }

    /**
     * 下一页：前两页（指针可能尚未就位）先重新定位，再读取一页并推进页码。
     * 与旧 nextPage 的 IO 路径一致
     **/
    public String turnNext() throws IOException {
        synchronized (lock) {
            if (currentPage / linesPerPage <= 1) {
                countSeekLocked();
            }
            return readForwardLocked();
        }
    }

    /**
     * 上一页：页码回退一页后重新定位并读取（回退算法与旧 previousPage 一致：
     * 恰好整页时回退两页——readForward 会再推进一页；非整页先回退到整页边界再减一页）。
     * <p>
     * 回退结果夹到 0：页码是不能为负的——负值除了让 {@code displayPage()} 显示 "-1 / N"，
     * 更糟的是 readForward 事后会把它加回正数并写进 {@code seekDictionary[0]}，
     * 把"第 0 行的位置"写成"读完第 N 行之后"，之后所有从该缓存起跳的 jumpTo 都会整体错页。
     * 调用方（MainUi 的"已经是第一条"守卫）本来就拦着用户路径，这里再兜一层
     **/
    public String turnBack() throws IOException {
        synchronized (lock) {
            if (currentPage % linesPerPage == 0) {
                currentPage = Math.max(0, currentPage - linesPerPage * 2);
            } else {
                while (currentPage % linesPerPage != 0) {
                    currentPage--;
                }
                currentPage = Math.max(0, currentPage - linesPerPage);
            }
            countSeekLocked();
            return readForwardLocked();
        }
    }

    /**
     * 跳到指定起始行（该行作为本页第一行）并读取一页。
     * 跳页 / epub 目录跳转共用
     **/
    public String jumpTo(int startLine) throws IOException {
        synchronized (lock) {
            currentPage = Math.max(0, startLine);
            countSeekLocked();
            return readForwardLocked();
        }
    }

    /**
     * 重新定位到当前页起点并重读该页，保持阅读进度（currentPage）不变。
     * 设置页 Apply / 切书后刷新用；首次打开（currentPage 为 0）时让 readForward
     * 自然推进到第 1 页末尾，与手动翻页语义一致
     **/
    public String reloadCurrent() throws IOException {
        synchronized (lock) {
            int pageEnd = currentPage;
            if (pageEnd > 0) {
                currentPage = Math.max(0, pageEnd - linesPerPage);
            }
            countSeekLocked();
            String content = readForwardLocked();
            if (pageEnd > 0) {
                currentPage = pageEnd;
            }
            return content;
        }
    }

    /**
     * 找到 currentPage 对应的文件指针（仅持锁调用）。
     * 缓存命中直接用；否则从最近的缓存点顺序扫到目标行
     **/
    private void countSeekLocked() throws IOException {
        if (seekDictionary.containsKey(currentPage)) {
            this.seek = seekDictionary.get(currentPage);
            return;
        }
        try (RandomAccessFile ra = new RandomAccessFile(resolvedPath(), "r")) {
            int line = 0;
            for (int i = 0; CACHE_INTERVAL * i < currentPage; i++) {
                line = CACHE_INTERVAL * i;
                Long cached = seekDictionary.get(line);
                if (cached != null) {
                    ra.seek(cached);
                } else {
                    // 缓存缺失（如首次跳页尚未统计），回退从头读
                    ra.seek(0);
                    line = 0;
                    break;
                }
            }
            StringBuilder dummy = new StringBuilder();
            readLines(ra, dummy, currentPage - line, "\n", null);
            this.seek = ra.getFilePointer();
        }
    }

    /**
     * 从当前文件指针向下读取一页（仅持锁调用）。
     * 读取失败直接抛出，由调用方决定如何展示——旧实现把异常消息当正文返回，
     * 异常文本会混进"助手回复"里渲染，伪装直接露馅
     **/
    private String readForwardLocked() throws IOException {
        RandomAccessFile ra = null;
        StringBuilder str = new StringBuilder();
        try {
            Charset charset = source.charset();
            ra = new RandomAccessFile(resolvedPath(), "r");
            ra.seek(seek);
            StringBuilder nStr = new StringBuilder();
            for (int j = 0; j < lineSpacing + 1; j++) {
                nStr.append("\n");
            }
            int got = readLines(ra, str, linesPerPage, nStr.toString(), charset);
            currentPage += got;
            seek = ra.getFilePointer();
            // currentPage 为 0 时不写缓存：0 号缓存项的语义是"文件开头"，
            // 只由 countLines() 在确认位置为 0 时写入；这里的 seek 是"读完一页之后"的位置，
            // 若页码因异常回退恰好落回 0，写进去会让后续 countSeekLocked 从错误偏移起跳（整体错页）
            if (currentPage > 0 && currentPage % CACHE_INTERVAL == 0) {
                seekDictionary.put(currentPage, seek);
            }
            // 去掉 UTF-8 BOM 字符
            if (str.length() > 0 && str.charAt(0) == '\uFEFF') {
                str.deleteCharAt(0);
            }
        } finally {
            if (ra != null) {
                ra.close();
            }
        }
        return str.toString();
    }

    /**
     * 实际读取路径，并处理"epub 重新解包导致指针缓存失效"。
     * （仅持锁调用；重解包由 BookSource.ensureResolved 报告）
     **/
    private String resolvedPath() throws IOException {
        if (source.ensureResolved()) {
            seekDictionary.clear();
        }
        return source.path();
    }

    /**
     * 从当前文件指针批量读取最多 maxLines 行，按 sep 分隔追加到 out。
     * charset 为 null 时只统计行数不生成文本（供定位指针使用）。
     * 返回实际读取的行数。
     **/
    private static int readLines(RandomAccessFile ra, StringBuilder out, int maxLines, String sep, Charset charset) throws IOException {
        byte[] buf = new byte[8192];
        byte[] tail = new byte[8192];
        int tailLen = 0;
        int count = 0;
        int n;
        // 最后一条被统计行的换行符之后的绝对字节位置（即下一条待读行的起点）
        long lineEnd = ra.getFilePointer();
        // 最后一条被统计的行是否无换行结尾（已读到文件尾），此时文件指针应保持在 EOF
        boolean lastLineNoNewline = false;
        while (count < maxLines && (n = ra.read(buf)) != -1) {
            long blockStart = ra.getFilePointer() - n;
            int start = 0;
            for (int j = 0; j < n && count < maxLines; j++) {
                if (buf[j] == '\n') {
                    appendLine(out, buf, start, j, tail, tailLen, sep, charset);
                    count++;
                    start = j + 1;
                    tailLen = 0;
                    lineEnd = blockStart + j + 1;
                    lastLineNoNewline = false;
                }
            }
            if (count < maxLines && start < n) {
                int need = tailLen + (n - start);
                if (need > tail.length) {
                    tail = Arrays.copyOf(tail, Math.max(need, tail.length * 2));
                }
                System.arraycopy(buf, start, tail, tailLen, n - start);
                tailLen = need;
            }
        }
        if (count < maxLines && tailLen > 0) {
            appendLine(out, buf, 0, 0, tail, tailLen, sep, charset);
            count++;
            lastLineNoNewline = true;
        }
        // 批量块读取会把指针推进到最后一个块的末尾，这里回退到最后一条被统计行的结尾，
        // 保证 seek 始终落在"下一行起点"，翻页/跳页不会跳过行或从某行中间开始读
        if (count > 0 && !lastLineNoNewline) {
            ra.seek(lineEnd);
        }
        return count;
    }

    private static void appendLine(StringBuilder out, byte[] buf, int bufStart, int bufEnd, byte[] tail, int tailLen, String sep, Charset charset) {
        int rawLen = tailLen + (bufEnd - bufStart);
        byte[] lineBytes = new byte[rawLen];
        if (tailLen > 0) {
            System.arraycopy(tail, 0, lineBytes, 0, tailLen);
        }
        if (bufEnd > bufStart) {
            System.arraycopy(buf, bufStart, lineBytes, tailLen, bufEnd - bufStart);
        }
        if (rawLen > 0 && lineBytes[rawLen - 1] == '\r') {
            lineBytes = Arrays.copyOf(lineBytes, rawLen - 1);
        }
        if (charset != null) {
            out.append(new String(lineBytes, charset)).append(sep);
        }
    }
}
