package com.thief.idea.book;

import com.thief.idea.util.EpubUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 一本书的读取来源：负责"实际可读路径"的解析（epub 解包 / 编码检测），
 * 把这类重活从翻页逻辑里隔离出来（原 MainUi.resolveReadPath / ensureCharset / detectCharset）。
 * <p>
 * epub 不是直接可读的文本：首次访问（以及源文件变化）时先解包成 UTF-8 临时 txt，
 * 同时拿到图片临时目录与目录（含正文行号）；普通文本文件直接读原路径。
 * 翻页 / 统计 / 朗读线程都会经由 {@link #ensureResolved()} 触发解包，
 * 因此所有方法在同一把锁上串行执行（解包耗时且会换临时文件，不能与读取并发交错）。
 * <p>
 * 线程模型：方法级 synchronized。解包只发生在 IO 线程（经 BookPager 调入），
 * EDT 只读 {@link #toc()} / {@link #imageDir()} 这类轻量查询。
 **/
public final class BookSource {

    private final String bookFile;

    /**
     * epub 解包出的临时文本文件（UTF-8），非 epub 书籍恒为 null
     **/
    private File epubTextFile;

    /**
     * epub 源文件解包时的最后修改时间，源文件变化时自动重新解包
     **/
    private long epubLastModified;

    /**
     * 当前 epub 的目录（含正文行号），非 epub 书籍为 null。
     * volatile：IO 线程在锁内解包后写入，EDT 免锁读取（加锁读取会被解包阻塞）
     **/
    private volatile List<EpubUtil.TocEntry> epubToc;

    /**
     * epub 解包出的图片临时目录（非 epub 或无图片时为 null），
     * 正文中的 [[IMG:文件名]] 占位以此目录解析。volatile 原因同上
     **/
    private volatile File epubImageDir;

    /**
     * 检测到的文件编码（UTF-8 或 GB18030），null 表示尚未检测；每本书只检测一次
     **/
    private Charset fileCharset;

    public BookSource(String bookFile) {
        this.bookFile = bookFile == null ? "" : bookFile;
    }

    /**
     * 书本路径（设置页配置的原始路径，epub 时是 .epub 文件本身）
     **/
    public String bookFile() {
        return bookFile;
    }

    /**
     * 确保实际可读路径就绪：epub 在首次访问或源文件变化时（重新）解包。
     * <p>
     * 返回是否发生了重新解包——换了临时文件后调用方（BookPager）的指针缓存全部失效，
     * 需要清空。解包成功之后才清理上一版的正文临时文件与图片目录，
     * 失败时保留旧资源可继续读（旧实现先删后解，失败后图片就断链了）。
     **/
    public synchronized boolean ensureResolved() throws IOException {
        if (bookFile.isEmpty()) {
            throw new IOException("未设置书本文件路径");
        }
        if (!isEpub()) {
            // 非 epub：清掉 epub 专属目录与图片目录，避免上次 epub 残留目录条目/图片
            epubToc = null;
            epubImageDir = null;
            return false;
        }
        File epub = new File(bookFile);
        long modified = epub.lastModified();
        if (epubTextFile != null && epubTextFile.exists() && epubLastModified == modified) {
            return false;
        }
        // 一次性拿到正文临时文件、图片目录与目录（含正文行号），后面翻页/跳页都直接读临时 txt
        EpubUtil.EpubBook pkg = EpubUtil.extract(epub);
        File newTextFile = EpubUtil.writeTempFile(pkg);
        if (epubImageDir != null) {
            deleteRecursively(epubImageDir);
        }
        if (epubTextFile != null) {
            // 旧实现只清理图片目录，正文临时 txt 全靠 deleteOnExit，IDE 长期不重启会积累
            epubTextFile.delete();
        }
        epubImageDir = pkg.imageDir;
        epubTextFile = newTextFile;
        epubToc = pkg.toc;
        epubLastModified = modified;
        return true;
    }

    /**
     * 实际读取的文件路径：epub 为解包出的临时 txt，其余为原文件。
     * 需先调用 {@link #ensureResolved()}。
     **/
    public synchronized String path() throws IOException {
        if (bookFile.isEmpty()) {
            throw new IOException("未设置书本文件路径");
        }
        if (isEpub()) {
            if (epubTextFile == null) {
                throw new IOException("EPUB 尚未解包");
            }
            return epubTextFile.getAbsolutePath();
        }
        return bookFile;
    }

    /**
     * 检测并缓存文件编码。
     * UTF-8（含 BOM）直接识别；非 UTF-8 的中文文本回退 GB18030（兼容 GBK/ANSI）。
     * 检测失败（如 UTF-16）时抛出异常，由调用方展示给用户。
     **/
    public synchronized Charset charset() throws IOException {
        ensureResolved();
        if (fileCharset == null) {
            fileCharset = detectCharset(new File(path()));
        }
        return fileCharset;
    }

    /**
     * epub 目录（含正文行号），非 epub 或尚未解包时为 null。
     * 免锁读：见字段注释（epubToc 为 volatile）
     **/
    @Nullable
    public List<EpubUtil.TocEntry> toc() {
        return epubToc;
    }

    /**
     * epub 解包出的图片临时目录，非 epub 或无图片时为 null
     **/
    @Nullable
    public File imageDir() {
        return epubImageDir;
    }

    private boolean isEpub() {
        return bookFile.toLowerCase(Locale.ROOT).endsWith(".epub");
    }

    /**
     * 递归删除临时目录（重新解包 epub 时清理上一版的图片目录）
     **/
    private static void deleteRecursively(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteRecursively(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }

    /**
     * 编码自动检测：BOM → UTF-8 严格解码样本（尾部最多回退 4 字节重试）→ 回退 GB18030。
     * 从 MainUi 原样搬来，改动读取逻辑时不要绕过该检测，也不要写死 UTF-8。
     **/
    private static Charset detectCharset(File file) throws IOException {
        try (RandomAccessFile ra = new RandomAccessFile(file, "r")) {
            byte[] sample = new byte[4096];
            int total = 0;
            int n;
            while (total < sample.length && (n = ra.read(sample, total, sample.length - total)) != -1) {
                total += n;
            }
            if (total >= 3 && (sample[0] & 0xFF) == 0xEF && (sample[1] & 0xFF) == 0xBB && (sample[2] & 0xFF) == 0xBF) {
                return StandardCharsets.UTF_8;
            }
            if (total >= 2 && (sample[0] & 0xFF) == 0xFF && (sample[1] & 0xFF) == 0xFE) {
                throw new IOException("暂不支持 UTF-16 LE 编码的文本文件");
            }
            if (total >= 2 && (sample[0] & 0xFF) == 0xFE && (sample[1] & 0xFF) == 0xFF) {
                throw new IOException("暂不支持 UTF-16 BE 编码的文本文件");
            }
            // 从尾部最多回退 4 字节，避免多字节字符被截断导致误判
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            for (int cut = 0; cut < total && cut <= 4; cut++) {
                try {
                    decoder.reset().decode(ByteBuffer.wrap(sample, 0, total - cut));
                    return StandardCharsets.UTF_8;
                } catch (CharacterCodingException ignored) {
                }
            }
            return Charset.forName("GB18030");
        }
    }
}
