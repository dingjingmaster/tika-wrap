package com.github.dingjingmaster.tika.main.FileOperation;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AutoParser {
    public AutoParser() {
        System.setSecurityManager(null);
        System.setProperty("org.apache.tika.debug", "true");
        System.setProperty("tika.mimetypes", "org/apache/tika/mime/tika-mimetypes.xml");
    }

    public boolean parserFile(String filePath, String tmpDir) throws Exception {
        boolean ret = true;

        String ctxFile = null;
        String metaFile = null;

        if (!tmpDir.isEmpty()) {
            File df = new File(tmpDir);
            df.mkdirs();
            if (df.exists()) {
                ctxFile = tmpDir + "/ctx.txt";
                metaFile = tmpDir + "/meta.txt";
            }
        }

        // 自动解析器
        Parser parser = new AutoDetectParser();
//        RecursiveParserWrapper recuParser = new RecursiveParserWrapper(parser);

        // 元数据对象
        Metadata md = new Metadata();

        // 带上下文相关信息的ParseContext实例
        ParseContext ctx = new ParseContext();

        try (FileInputStream fi = new FileInputStream(filePath)) {
            if (null != ctxFile) {
                Writer writer = new BufferedWriter(new FileWriter(ctxFile));
                WriteOutContentHandler writeHandler = new WriteOutContentHandler(writer);
                parser.parse(fi, writeHandler, md, ctx);
            }
            else {
                BodyContentHandler memHandler = new BodyContentHandler(-1);
                parser.parse(fi, memHandler, md, ctx);
//                System.out.println("File content: " + memHandler);
            }

            if (null != metaFile) {
                try (FileOutputStream fw = new FileOutputStream(metaFile)) {
                    String[] names = md.names();
                    for (String name : names) {
                        String lineBuf = name + "{]" + md.get(name) + "\n";
                        fw.write(lineBuf.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    ret = false;
                    System.out.println(e.toString());
                }
            }
        } catch (Exception e) {
            ret = false;
            System.out.println(e.toString());
        }

        return ret;
    }

    public static void main (String[] args) {
//        String file = "/home/dingjing/tk.csv";
//        String file = "/home/dingjing/andsec_3.2.14_amd64.deb";
//        String file = "/home/dingjing/TrayApp.zip";
//        String file = "/home/dingjing/aa.zip";
        String file = "/home/dingjing/aa.docx";
//        String file = "/home/dingjing/Pictures/vim.png";
        AutoParser ap = new AutoParser();

        try {
            if (!ap.parserFile(file, "/tmp/")) {
                System.out.println("parser file: '" + file + "' failed!");
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }
}
