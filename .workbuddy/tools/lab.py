#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""离线构建 + 渲染 + 量测的编排脚本（Windows 这台机器上 bash 缺 coreutils，rm/tail/ls 都不能用，
所以凡是要"删目录 / 串命令"的活儿都走这里）。

子命令：
  compile                     用平台 jar 编译 src/main/java 到 build/classes/java/main
  preview <out.png> [w] [h]   跑 UiPreview 生成 8 张预览图（2x 像素）
  ink <png> [thr] [gap] [label] [y0] [y1]
                              跑 InkProfile 量测墨迹（可限定 y 带）
  band <png> <y0> <y1> [label] [thr]
                              常见的"只看一条带"的快捷方式
  header <scale> [宽逻辑px=302] [输出.png] [字体倍率]
                              跑 HeaderProbe：打印顶栏/底栏各控件的垂直中心线
"""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PLAT_REL = ("C:/Users/16658/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/"
            "ideaIC/2023.3/6105b81c6142f62379ad6c5afb542c77350a71eb/ideaIC-2023.3")
JAVA = "D:/Program Files/java/jdk-17.0.7/bin/java.exe"
JAVAC = "D:/Program Files/java/jdk-17.0.7/bin/javac.exe"
OUT_CLASSES = os.path.join(ROOT, "build", "classes", "java", "main")
TOOLS = os.path.join(ROOT, ".workbuddy", "tools")

COMMON_FLAGS = [
    "-Dfile.encoding=UTF-8",
    "--add-exports=java.desktop/sun.font=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
]


def dep_jars():
    """从 gradle 缓存里捡第三方依赖（epublib / jsoup / slf4j），编译 EpubUtil 时需要。"""
    import glob
    base = "C:/Users/16658/.gradle/caches/modules-2/files-2.1"
    jars = []
    for pattern in ("**/epublib*.jar", "**/jsoup*.jar", "**/slf4j*.jar", "**/epub*.jar"):
        for path in glob.glob(os.path.join(base, pattern), recursive=True):
            if path not in jars and os.path.isfile(path):
                jars.append(path)
    return jars


def classpath():
    # src/main/resources 必须进 classpath，否则 /icons/lucide/*.svg 加载不到，
    # 预览图里的图标会整片空白（量测墨迹时就全是"没有墨迹"，看不出对齐问题）。
    return os.pathsep.join([PLAT_REL + "/lib/*", OUT_CLASSES,
                            os.path.join(ROOT, "src", "main", "resources")] + dep_jars())


def compile_main():
    sources = []
    base = os.path.join(ROOT, "src", "main", "java")
    for dirpath, _dirnames, filenames in os.walk(base):
        for name in filenames:
            if name.endswith(".java"):
                sources.append(os.path.join(dirpath, name))
    # 清空输出目录（等价于 rm -rf，但用 python 做）
    for dirpath, dirnames, filenames in os.walk(OUT_CLASSES, topdown=False):
        for name in filenames:
            os.remove(os.path.join(dirpath, name))
        for name in dirnames:
            try:
                os.rmdir(os.path.join(dirpath, name))
            except OSError:
                pass
    os.makedirs(OUT_CLASSES, exist_ok=True)
    cmd = [JAVAC, "-nowarn", "-encoding", "UTF-8", "-d", OUT_CLASSES, "-cp", classpath()] + sources
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    sys.stdout.write(result.stdout or "")
    sys.stderr.write(result.stderr or "")
    print("[compile] sources=%d rc=%d" % (len(sources), result.returncode))
    return result.returncode


def preview(out, width="560", height="900"):
    out = os.path.abspath(out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    cmd = [JAVA] + COMMON_FLAGS + ["-cp", classpath(),
                                  os.path.join(TOOLS, "UiPreview.java"), out, str(width), str(height)]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    sys.stdout.write(result.stdout or "")
    sys.stderr.write(result.stderr or "")
    print("[preview] rc=%d -> %s" % (result.returncode, out))
    return result.returncode


def ink(png, thr="90", gap="4", label=None, y0=None, y1=None, mode=None):
    args = [JAVA, "-Dfile.encoding=UTF-8", os.path.join(TOOLS, "InkProfile.java"),
            png, str(thr), str(gap)]
    if label is not None or y0 is not None or mode is not None:
        args.append(label if label is not None else os.path.basename(png))
    if y0 is not None:
        args.append(str(y0))
        args.append(str(y1 if y1 is not None else 10 ** 9))
    if mode is not None:
        args.append(str(mode))
    result = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    sys.stdout.write(result.stdout or "")
    sys.stderr.write(result.stderr or "")
    return result.returncode


def header(scale="1.5", width="302", out=None, font_scale=None):
    if out is None:
        out = os.path.join(ROOT, "build", "preview", "header-%s.png" % scale)
    out = os.path.abspath(out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    cmd = [JAVA]
    if font_scale:
        cmd.append("-Dprobe.fontScale=%s" % font_scale)
    cmd += COMMON_FLAGS + ["-cp", classpath(), os.path.join(TOOLS, "HeaderProbe.java"),
                           str(scale), str(width), out]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    sys.stdout.write(result.stdout or "")
    sys.stderr.write(result.stderr or "")
    print("[header] rc=%d -> %s" % (result.returncode, out))
    return result.returncode


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    cmd = sys.argv[1]
    rest = sys.argv[2:]
    if cmd == "compile":
        return compile_main()
    if cmd == "preview":
        return preview(*rest)
    if cmd == "ink":
        return ink(*rest[:7])
    if cmd == "band":
        png, y0, y1 = rest[0], rest[1], rest[2]
        label = rest[3] if len(rest) > 3 else None
        thr = rest[4] if len(rest) > 4 else "90"
        mode = rest[5] if len(rest) > 5 else None
        return ink(png, thr, "4", label, y0, y1, mode)
    if cmd == "header":
        return header(*rest[:4])
    print("未知子命令：" + cmd)
    return 1


if __name__ == "__main__":
    sys.exit(main())
