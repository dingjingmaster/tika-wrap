package com.github.dingjingmaster.tika.main.FileOperation;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;
import net.sf.sevenzipjbinding.simple.impl.SimpleInArchiveImpl;
import net.sf.sevenzipjbinding.util.ByteArrayStream;
import org.apache.tika.io.TikaInputStream;

public class Rar5Parser {
   private String zipFile = null;
   private IInArchive archive = null;
   private RandomAccessFile randomAccessFile = null;
   private int curren_idx = 0;
   private String curEntryName = "";
   private ByteArrayStream curEntryOutputStream = null;
   private String encoding = "gbk";
   private static int largeFileThreshold = 536870912;
   private SimpleInArchiveImpl siArc = null;
   private ISimpleInArchiveItem siArcItem = null;

   public Rar5Parser(String filename) throws FileNotFoundException, SevenZipException {
      this.zipFile = filename;
      this.randomAccessFile = new RandomAccessFile(this.zipFile, "rw");
      this.archive = SevenZip.openInArchive(null, new RandomAccessFileInStream(this.randomAccessFile));
      this.siArc = new SimpleInArchiveImpl(this.archive);
   }

   public Rar5Parser(TikaInputStream stream) throws IOException {
      this.randomAccessFile = new RandomAccessFile(stream.getFile(), "rw");
      this.archive = SevenZip.openInArchive(null, new RandomAccessFileInStream(this.randomAccessFile));
      this.siArc = new SimpleInArchiveImpl(this.archive);
   }

   public void closeStream() {
      if (this.randomAccessFile != null) {
         try {
            this.randomAccessFile.close();
         } catch (IOException var2) {
            var2.printStackTrace();
         }
      }
   }

   public boolean getNextEntry() {
      try {
         if (this.siArc == null) {
            this.curEntryName = "";
            this.curEntryOutputStream = null;
            this.siArc.close();
            this.siArc = null;
            return false;
         }

         while (this.curren_idx < this.siArc.getNumberOfItems()) {
            this.siArcItem = this.siArc.getArchiveItem(this.curren_idx);
            this.curren_idx++;
            if (!this.siArcItem.isFolder()) {
               this.curEntryName = this.siArcItem.getPath();
               this.curEntryOutputStream = new ByteArrayStream(largeFileThreshold);
               this.siArcItem.extractSlow(this.curEntryOutputStream);
               return true;
            }
         }
      } catch (SevenZipException var2) {
         var2.printStackTrace();
      }

      return false;
   }

   public String getEntryName() {
      return this.curEntryName;
   }

   public ByteArrayStream getEntryOutputStream() {
      return this.curEntryOutputStream;
   }

   public InputStream getEntryAsInputStream() {
      return this.curEntryOutputStream != null ? new ByteArrayInputStream(this.curEntryOutputStream.getBytes()) : null;
   }

   public boolean isEncrypted() throws SevenZipException {
      return this.siArcItem != null ? this.siArcItem.isEncrypted() : false;
   }

   public boolean isDir() throws SevenZipException {
      return this.siArcItem != null ? this.siArcItem.isFolder() : false;
   }

   String getFileNameString(byte[] fileNameByteArray) throws UnsupportedEncodingException {
      if (fileNameByteArray.length == 0) {
         return null;
      } else {
         int index = 0;

         for (byte by : fileNameByteArray) {
            if (by == 0) {
               break;
            }

            index++;
         }

         return index == 0 ? null : new String(fileNameByteArray, 0, index, this.encoding);
      }
   }

   public void setFileNameEncoding(String encoding) {
      this.encoding = encoding;
   }

   public static void main(String[] args) throws IOException {
      String fileName = "G:\\2022-work\\Dev\\tika-202109\\file\\25683\\提取子文件过多，检测时间过长.rar";
      Rar5Parser parser = new Rar5Parser(fileName);
      System.out.println("is encrypted : " + parser.isEncrypted());

      while (parser.getNextEntry()) {
         String entryName = parser.getEntryName();
         System.out.println(entryName + ":" + parser.isDir() + ":");
         ByteArrayStream os = parser.getEntryOutputStream();
         os.writeToOutputStream(System.out, true);
         System.out.println("--------");
      }
   }
}
