package com.github.dingjingmaster.tika.main.FileOperation;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

import java.io.File;
import java.io.FileInputStream;

public class AutoParser {
    public boolean parserFile(String filePath) throws Exception {
        boolean ret = true;

        // 自动解析器
        Parser parser = new AutoDetectParser();

        // 捕获文档内容
        BodyContentHandler bh = new BodyContentHandler(-1);

        // 元数据对象
        Metadata md = new Metadata();

        // 带上下文相关信息的ParseContext实例
        ParseContext pc = new ParseContext();

        File f = new File(filePath);
        if (!f.isFile() || !f.exists()) {
            return false;
        }

        try (FileInputStream fi = new FileInputStream(f)) {
            parser.parse(fi, bh, md, pc);
        } catch (Exception e) {
            ret = false;
        }

        if (ret) {
            System.out.println("File content: " + bh);
            String[] names = md.names();
            for (String name: names) {
                System.out.println(name);
            }
        }

        return ret;
    }

    public static void main (String[] args) {
//        String file = "/home/dingjing/tk.csv";
//        String file = "/home/dingjing/andsec_3.2.14_amd64.deb";
//        String file = "/home/dingjing/TrayApp.zip";
        String file = "/home/dingjing/aa.zip";
        AutoParser ap = new AutoParser();

        try {
            if (!ap.parserFile(file)) {
                System.out.println("parser file: '" + file + "' failed!");
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }
}
