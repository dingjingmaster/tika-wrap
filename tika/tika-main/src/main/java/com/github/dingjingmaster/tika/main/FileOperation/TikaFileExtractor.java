package com.github.dingjingmaster.tika.main.FileOperation;

import com.github.dingjingmaster.tika.main.ndimporter.fingerprint.exception.NoFeatureInformationException;
import com.github.dingjingmaster.tika.main.ndimporter.fingerprint.service.SimHashService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
//import org.apache.tika.parser.pkg.ContextExtraInfo;

public class TikaFileExtractor {
   HashMap<String, Metadata> fileList = new HashMap<>();
   FullParser parser = new FullParser();
   String fileNameEncoding = "gbk";
   String tmpFilePath = "";
   int MAX_SIMHASH = 1048576;

   public static void main(String[] args) throws IOException {
      double start = (double)System.currentTimeMillis();
      testGetFileContent();
      double end = (double)System.currentTimeMillis();
      System.out.println("eclapse: " + (end - start));
   }

   public static String readFileContent(String path) throws IOException {
      InputStream is = new FileInputStream(path);
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      byte[] data = new byte[4096];
      int count = -1;

      while ((count = is.read(data, 0, 4096)) != -1) {
         os.write(data, 0, count);
      }

      is.close();
      data = null;
      return new String(os.toByteArray(), "ISO-8859-1");
   }

   static void testGetFileType() throws IOException {
      TikaFileExtractor ex = new TikaFileExtractor();
      String fileName = "/work/test/DLPS498/file/3PP2.pptx";
      String data = null;
      String type = ex.getFileType(fileName, data);

      assert type == "";
   }

   static void testGetFileContent() throws IOException {
      TikaFileExtractor ex = new TikaFileExtractor();
      String data = "";
      String fileName = "F:\\2022-work\\Dev\\tika-202109\\file\\tt222-nohead.xls";
      String data１ = null;
      String type = ex.getFileType(fileName, data１);
      System.out.println("Ext type:" + type);
      ex.setTmpPath("/work/test/core_test/file/test_zip/");
      String content = ex.parseFile(fileName, data);
      if ("0".equals(content)) {
         ex.printFileList();
      } else {
         System.out.println("no succ");
      }

      String filenames = ex.getPackagedFileNames();
      String filetype = ex.getPackagedFileType(fileName);
   }

   static void testZip() throws IOException {
      String fileName = "I:\\ljh\\work\\testdata\\С���ֻ�.zip";
      ZipFile zf = new ZipFile(fileName, "gbk");
      Enumeration zes = zf.getEntries();

      while (zes.hasMoreElements()) {
         String name = ((ZipEntry)zes.nextElement()).getName();
         System.out.println(name);
      }
   }

   public void printFileList() {
      for (String file : this.fileList.keySet()) {
         Metadata metadata = this.fileList.get(file);
         System.out.println("file name:" + file);
         System.out.println("file type:" + metadata.get("Content-Type"));
         System.out.println("is encrypted:" + metadata.get(FullParser.IS_ENCRYPTED));
         System.out.println("file content:" + metadata.get(FullParser.CONTENT));
         System.out.println("save dir:" + this.getTmpFileFullPath(file));
         String hash = this.calcSimHash(metadata.get(FullParser.CONTENT));
         System.out.println("simhash:" + hash);
      }
   }

   public String getTmpFileFullPath(String fn) {
      Metadata metadata = this.fileList.get(fn);
      return metadata != null ? metadata.get(FullParser.SVAE_POSITON) : "";
   }

   public String getFileType(String fileName, String data) throws IOException {
      if (fileName != null && !fileName.isEmpty() || data != null && !data.isEmpty()) {
         String type = "";

         try {
            try {
               Tika tika = new Tika();
               type = tika.detect(fileName);
            } catch (Exception var8) {
               System.out.println("TikaFileExtractor exception----in getFileType" + var8);
               var8.printStackTrace();
            }

            return type;
         } finally {
            ;
         }
      } else {
         return "";
      }
   }

   private boolean isPackagedFile(String type) {
      return type.equalsIgnoreCase("application/zip")
         || type.equalsIgnoreCase("application/x-gtar")
         || type.equalsIgnoreCase("application/gzip")
         || type.equalsIgnoreCase("application/x-bzip2")
         || type.equalsIgnoreCase("application/x-rar-compressed")
         || type.equalsIgnoreCase("application/x-7z-compressed");
   }

   public String getFileContent(String fileName, String data) throws IOException {
      return this.getFileContent(fileName, data, "");
   }

   public String getFileContent(String fileName, String data, String innerFileDir) throws IOException {
      if (fileName != null && !fileName.isEmpty() || data != null && !data.isEmpty()) {
         if (!this.parseFileInner(fileName, data, innerFileDir)) {
            return "";
         } else {
            String content = "";

            for (String file : this.fileList.keySet()) {
               String type = this.getPackagedFileType(file);
               String tmp = file + "=" + this.getPackagedFileContent(file);
               int length = tmp.getBytes("ISO-8859-1").length;
               String len = String.format("%010d", length);
               content = content + len + tmp;
            }

            return content;
         }
      } else {
         return "";
      }
   }

   public String parseFile(String fileName, String data) throws IOException {
      String ret = "1";
      if (this.parseFileInner(fileName, data, "")) {
         ret = "0";
      }

      return ret;
   }

   public String parseFile(String fileName, String data, String innerFileDir) throws IOException {
      String ret = "1";
      if (this.parseFileInner(fileName, data, innerFileDir)) {
         HashMap<String, Metadata> tmpfileList_Loop = (HashMap<String, Metadata>)this.fileList.clone();
         HashMap<String, Metadata> tmpfileList_Result = (HashMap<String, Metadata>)this.fileList.clone();

         for (String file : tmpfileList_Loop.keySet()) {
            Metadata metadata = this.fileList.get(file);
            if ((
                  "application/x-tika-msoffice".equalsIgnoreCase(metadata.get("Content-Type")) && metadata.get(FullParser.CONTENT).length() == 0
                     || "application/x-tika-msoffice-embedded; format=comp_obj".equalsIgnoreCase(metadata.get("Content-Type"))
               )
               && this.parseFileInner(this.getTmpFileFullPath(file), data, innerFileDir)) {
               tmpfileList_Result.remove(file);
               tmpfileList_Result.putAll(this.fileList);
            }
         }

         this.fileList = (HashMap<String, Metadata>)tmpfileList_Result.clone();
         ret = "0";
      }

      return ret;
   }

   public boolean parseFileInner(String fileName, String data, String innerFileDir) throws IOException {
      boolean ok = true;
      InputStream stream = null;

      try {
         if (innerFileDir != null && !innerFileDir.isEmpty()) {
            this.setTmpPath(innerFileDir);
         }

         this.parser.setTmpFilePath(this.tmpFilePath);
         this.parser.setFileName(fileName);
         if (data != null && !data.isEmpty()) {
            stream = new ByteArrayInputStream(data.getBytes(StandardCharsets.ISO_8859_1));
         } else {
            stream = TikaInputStream.get(new File(fileName));
         }

         Metadata metadata = new Metadata();
         metadata.set("resourceName", fileName);
         ParseContext context = new ParseContext();
//         ContextExtraInfo extraInfo = new ContextExtraInfo();
//         extraInfo.setFileNameEncoding(this.fileNameEncoding);
//         context.set(ContextExtraInfo.class, extraInfo);
         this.parser.parse(stream, null, metadata, context);
         this.fileList = this.parser.getFileInfo();
      } catch (Exception var12) {
         ok = false;
         System.out.println("TikaFileExtractor exception----in parserFileInner : " + var12);
      } finally {
         if (stream != null) {
            stream.close();
         }
      }

      return ok && !this.fileList.isEmpty();
   }

   void printHex(String filename) {
      byte[] by = filename.getBytes();

      for (byte b : by) {
         System.out.printf("%x ", b);
      }

      System.out.println("----------");
   }

   void printFileList(HashMap<String, Metadata> fileList) {
      for (String filename : fileList.keySet()) {
         System.out.println(filename + ":");
         this.printHex(filename);
         Metadata metadata = fileList.get(filename);
         System.out.println("original length:" + metadata.get(FullParser.ORG_LENGTH));
         System.out.println("is encrypted:" + metadata.get(FullParser.IS_ENCRYPTED));
         System.out.println("mimetype:" + metadata.get("Content-Type"));
         System.out.println("content:" + metadata.get(FullParser.CONTENT));
         System.out.println("save dir:" + metadata.get(FullParser.SVAE_POSITON));
         System.out.println("------------------------------------------------");
      }
   }

   public String getPackagedFileNames() {
      String files = "";

      for (String fileName : this.fileList.keySet()) {
         if (files.isEmpty()) {
            files = files + fileName;
         } else {
            files = files + "|" + fileName;
         }
      }

      return files;
   }

   TikaFileExtractor(String encoding) {
      this.fileNameEncoding = encoding;
   }

   TikaFileExtractor() {
      this.fileNameEncoding = "gbk";
   }

   public String getPackagedFileType(String fileName) {
      String type = this.fileList.get(fileName).get("Content-Type");
      MediaType mediaType = MediaType.parse(type);
      return mediaType == null ? "" : mediaType.getType() + "/" + mediaType.getSubtype();
   }

   public String getPackagedFileContent(String fileName) {
      return this.fileList.get(fileName).get(FullParser.CONTENT);
   }

   public String getPackagedFileLength(String fileName) {
      return this.fileList.get(fileName).get(FullParser.ORG_LENGTH);
   }

   public String setFileNameEncoding(String encoding) {
      this.fileNameEncoding = encoding;
      return "0";
   }

   public String isPackagedFileParseSucc(String fileName) {
      String succ = this.fileList.get(fileName).get(FullParser.IS_SUCC);
      return "no".equals(succ) ? "1" : "0";
   }

   public String getPackagedFileFailReason(String fileName) {
      Metadata mt = this.fileList.get(fileName);
      return mt != null ? mt.get(FullParser.FAIL_REASON) : "";
   }

   public String getPackagedFileSaveDir(String fileName) {
      Metadata mt = this.fileList.get(fileName);
      return mt != null ? mt.get(FullParser.SVAE_POSITON) : "";
   }

   public String getFileNameEncoding() {
      return this.fileNameEncoding;
   }

   public void reset() {
      this.fileList.clear();
      this.fileNameEncoding = "gbk";
      this.parser = new FullParser();
   }

   public String isPackagedFileEncrypted(String fileName) {
      String ret = this.fileList.get(fileName).get(FullParser.IS_ENCRYPTED);
      return ret != null && "yes".equals(ret) ? "0" : "1";
   }

   public void setTmpPath(String path) {
      File file = new File(path);
      if (!file.exists() || !file.isDirectory()) {
         file.mkdirs();
      }

      this.tmpFilePath = path;
   }

   public boolean deleteTmpFiles() {
      try {
         for (String file : this.fileList.keySet()) {
            Metadata metadata = this.fileList.get(file);
            String strTmpFileName = metadata.get(FullParser.SVAE_POSITON);
            if (strTmpFileName != null && strTmpFileName.length() > 0) {
               Path tmpFile = Paths.get(strTmpFileName);
               Files.delete(tmpFile);
            }
         }

         return true;
      } catch (IOException var6) {
         return false;
      }
   }

   public String calcSimHash(String context) {
      SimHashService sim = new SimHashService(4, 20);

      try {
         int len = context.length();
         if (len > this.MAX_SIMHASH) {
            len = this.MAX_SIMHASH;
         }

         return sim.simHash(context.substring(0, len)) + "";
      } catch (NoFeatureInformationException var4) {
         return "";
      }
   }
}
