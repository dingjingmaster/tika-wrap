package com.github.dingjingmaster.tika.main.FileOperation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
//import org.apache.pdfbox.exceptions.WrappedIOException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
//import org.apache.tika.parser.pkg.ContextExtraInfo;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class FullParser implements Parser {
   private static final long serialVersionUID = 1L;
   private Parser internalParser = null;
   private HashMap<String, Metadata> metaContainer = new HashMap<>();
   private String fileName = "";
   private boolean isNeedLength = true;
   private static long largeFileThreshold = 536870912L;
   private static int ContextThreshold = 8388608;
   private String tmpFilePath = "";
   public static String IS_ENCRYPTED = "isEncrypted";
   public static String CONTENT = "content";
   public static String ORG_LENGTH = "orgLength";
   public static String IS_SUCC = "isSucc";
   public static String IS_TOO_LARGE = "isTooLarge";
   public static String FAIL_REASON = "failReason";
   public static String SVAE_POSITON = "saveParh";

   @Override
   public Set<MediaType> getSupportedTypes(ParseContext arg0) {
      ParseContext context = new ParseContext();
      return new AutoDetectParser().getSupportedTypes(context);
   }

   private String getActualFileName(String resourceName, String type) throws UnsupportedEncodingException {
      if (this.fileName != null && !this.fileName.equalsIgnoreCase(resourceName)) {
         String actualFileName = resourceName;
         if (("application/x-gtar".equalsIgnoreCase(type) || "application/x-tar".equalsIgnoreCase(type)) && this.fileName.startsWith(resourceName)) {
            int loc1 = resourceName.lastIndexOf(47);
            int loc2 = resourceName.lastIndexOf(92);
            int loc = loc1 > loc2 ? loc1 : loc2;
            actualFileName = resourceName.substring(loc + 1);
         }

         if (actualFileName == null) {
            UUID uuid = UUID.randomUUID();
            actualFileName = uuid.toString();
         }

         return actualFileName;
      } else {
         return resourceName;
      }
   }

   @Override
   public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException {
      TikaInputStream stream1 = null;

      try {
         if (this.internalParser == null) {
            this.internalParser = new AutoDetectParser();
         }

         stream1 = TikaInputStream.get(stream);
         long length = (long)stream1.available();
         if (this.isNeedLength) {
            if (!stream1.hasLength()) {
               length = stream1.getLength();
               if (this.tmpFilePath.length() > 0) {
                  Path orgPath = stream1.getPath();
                  Path newFile = Paths.get(this.tmpFilePath + "/" + orgPath.getFileName().toString());
                  Files.copy(orgPath, newFile, StandardCopyOption.REPLACE_EXISTING);
                  metadata.set(SVAE_POSITON, newFile.toString());
               }
            } else {
               length = stream1.getLength();
            }
         }

         metadata.set(ORG_LENGTH, Long.toString(length));
         if (length < largeFileThreshold || largeFileThreshold == -1L) {
            FullParser parserSub = new FullParser();
            parserSub.setInnerParser(this.internalParser);
            parserSub.setFileName(this.fileName);
            parserSub.setTmpFilePath(this.tmpFilePath);
            Parser orgParser = context.get(Parser.class);
            context.set(Parser.class, parserSub);
            ContentHandler handler1 = new BodyContentHandler(-1);

            try {
               this.internalParser.parse(stream1, handler1, metadata, context);
            } catch (Exception var21) {
               Throwable cause = var21.getCause();
               metadata.set(IS_SUCC, "no");
               String reason = "exception occur,the exception:" + var21;
               if (cause != null) {
                  reason = reason + ",the cause is:" + cause;
               }

               metadata.set(FAIL_REASON, reason);
               if (cause != null && EncryptedDocumentException.class.isInstance(cause)) {
                  metadata.set(IS_ENCRYPTED, "yes");
               } else if (org.apache.tika.exception.EncryptedDocumentException.class.isInstance(var21)) {
                  metadata.set(IS_ENCRYPTED, "yes");
               } else if (UnsupportedZipFeatureException.class.isInstance(cause)) {
                  String msg = cause.getMessage();
                  if (msg.contains("encryption")) {
                     metadata.set(IS_ENCRYPTED, "yes");
                  }
//               } else if (WrappedIOException.class.isInstance(cause)) {
//                  String msg = cause.getMessage();
//                  if (msg.contains("decrypt")) {
//                     metadata.set(IS_ENCRYPTED, "yes");
//                  }
               }
            }

            if ("application/x-tika-ooxml-protected".equalsIgnoreCase(metadata.get("Content-Type"))) {
               metadata.set(IS_SUCC, "no");
               metadata.set(IS_ENCRYPTED, "yes");
            }

            String path = this.getActualFileName(metadata.get("resourceName"), metadata.get("Content-Type"));
            this.metaContainer.put(path, metadata);
            String filetype = metadata.get("Content-Type").toLowerCase();
            if ("application/x-rar-compressed".equals(filetype)) {
               this.parseRarFile(path, stream1, metadata, context);
            } else {
               String content = handler1.toString();
               int len = content.length();
               if (len > ContextThreshold) {
                  len = ContextThreshold;
               }

               metadata.set(CONTENT, content.substring(0, len));
               if (filetype.startsWith("text/plain")) {
                  Tika tika = new Tika();
                  String filetype_ext = tika.detect(metadata.get("resourceName"));
                  if (!"text/plain".equalsIgnoreCase(filetype_ext)) {
                     filetype = filetype.replaceFirst("text/plain", filetype_ext);
                     metadata.set("Content-Type", filetype);
                  }
               }
            }

            for (String key : parserSub.metaContainer.keySet()) {
               this.metaContainer.put(path + "/" + key, parserSub.metaContainer.get(key));
            }

            context.set(Parser.class, orgParser);
            if (this.isPackagedFile(metadata.get("Content-Type"))) {
               String saveFileName = metadata.get(SVAE_POSITON);
               if (saveFileName != null && saveFileName.length() > 0) {
                  Path tmpFile = Paths.get(saveFileName);
                  Files.delete(tmpFile);
                  return;
               }
            }

            return;
         }

         metadata.set(IS_SUCC, "no");
         metadata.set(IS_TOO_LARGE, "yes");
         String failReason = "file is too large,the limit is:" + largeFileThreshold;
         metadata.set(FAIL_REASON, failReason);
         String pathx = this.getActualFileName(metadata.get("resourceName"), metadata.get("Content-Type"));
         this.metaContainer.put(pathx, metadata);
      } catch (Exception var22) {
         metadata.set(IS_SUCC, "no");
         String reasonx = "exception occur,the exception:" + var22;
         Throwable causex = var22.getCause();
         if (causex != null) {
            reasonx = reasonx + causex;
         }

         metadata.set(FAIL_REASON, reasonx);
         return;
      } finally {
         if (stream1 != null) {
            stream1.close();
         }
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

   private void parseRarFile(String path, InputStream stream, Metadata metadata, ParseContext context) throws IOException {
      TikaInputStream tmp = null;
      Rar5Parser parser = null;

      try {
         String content = "";
         if (!(stream instanceof TikaInputStream)) {
            return;
         }

         tmp = (TikaInputStream)stream;
         parser = new Rar5Parser(tmp);
//         String encoding = "";
//         ContextExtraInfo extraInfo = context.get(ContextExtraInfo.class);
//         if (extraInfo != null) {
//            encoding = extraInfo.getFileNameEncoding();
//            if (encoding != null && !encoding.isEmpty()) {
//               parser.setFileNameEncoding(encoding);
//            }
//         }

         if (!parser.isEncrypted()) {
            while (parser.getNextEntry()) {
               if (!parser.isDir()) {
                  Metadata metadata1 = new Metadata();
                  metadata1.set("resourceName", path + "/" + parser.getEntryName());
                  InputStream is = parser.getEntryAsInputStream();

                  try {
                     this.parse(is, null, metadata1, context);
                  } finally {
                     is.close();
                  }

                  content = content + parser.getEntryName() + "\n";
               }
            }

            int len = content.length();
            if (len > ContextThreshold) {
               len = ContextThreshold;
            }

            metadata.set(CONTENT, content.substring(0, len));
            this.metaContainer.put(path, metadata);
            parser.closeStream();
            return;
         }

         metadata.set(IS_ENCRYPTED, "yes");
         metadata.set(CONTENT, content);
         metadata.set(IS_SUCC, "no");
         metadata.set(FAIL_REASON, "this is a encrypted rar archive file.");
         parser.closeStream();
      } catch (Exception var21) {
         metadata.set(IS_SUCC, "no");
         String reason = "exception occur,the exception:" + var21;
         Throwable cause = var21.getCause();
         if (cause != null) {
            reason = reason + cause;
         }

         metadata.set(FAIL_REASON, reason);
         return;
      } finally {
         if (parser != null) {
            parser.closeStream();
         }

         if (tmp != null) {
            tmp.close();
         }
      }
   }

   public void setFileName(String fileName) {
      this.fileName = fileName;
   }

   public void setInnerParser(Parser parser) {
      this.internalParser = parser;
   }

   HashMap<String, Metadata> getFileInfo() {
      return this.metaContainer;
   }

   void setNeedLength(boolean isNeedLength) {
      this.isNeedLength = isNeedLength;
   }

   void setFileLengthThreshold(long threshold) {
      largeFileThreshold = threshold;
   }

   public void setTmpFilePath(String path) {
      this.tmpFilePath = path;
   }
}
