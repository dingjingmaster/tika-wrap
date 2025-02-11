package com.github.dingjingmaster.tika.main.ndimporter.kwdmatch;

import com.github.dingjingmaster.tika.main.ndimporter.common.IKAnalyzerUtils;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.ScoreDoc;
import org.wltea.analyzer.lucene.IKAnalyzer;

public class TestLabel {
   public static void main(String[] args) throws IOException {
      System.out.println("Hello World!");
      testMyMLT_mdlp();
   }

   public static void testMemoryIndex() {
      Analyzer analyzer = new EnglishAnalyzer();
      MemoryIndex index = new MemoryIndex();
      QueryParser parser = new QueryParser("content", analyzer);
      float score = 0.0F;

      try {
         long startmils = System.currentTimeMillis();

         for (int i = 0; i < 100000; i++) {
            index.addField(
               "content",
               "Congratulations, your recharge succeeded. Your balance is 95.30 Nfa, Your account expires after 15/08/2018 Readings about Salmons and other select Alaska fishing Manuals",
               analyzer
            );
            score = index.search(parser.parse("+salmon +fish manual +\"select Alaska\""));
            index.reset();
         }

         long endmils = System.currentTimeMillis();
         long cost = (endmils - startmils) / 1000L;
         System.out.println("cost time " + cost + " second");
      } catch (ParseException var10) {
         var10.printStackTrace();
      }

      if (score > 0.0F) {
         System.out.println("it's a match");
      } else {
         System.out.println("no match found");
      }

      System.out.println("indexData=" + index.toString());
   }

   public static void testTokenizer() {
      IKAnalyzer analyzer = new IKAnalyzer(true);
      TokenStream ts = analyzer.tokenStream("myfield", new StringReader("1988年号码禁止非法贩运麻醉药品和1369360398精神药品公约lucene fishing"));
      OffsetAttribute offsetAtt = ts.addAttribute(OffsetAttribute.class);

      try {
         ts.reset();

         while (ts.incrementToken()) {
            System.out.println("token: " + ts.reflectAsString(true));
            System.out.println("token start offset: " + offsetAtt.startOffset());
            System.out.println("  token end offset: " + offsetAtt.endOffset());
         }

         ts.end();
      } catch (IOException var12) {
         var12.printStackTrace();
      } finally {
         try {
            ts.close();
            analyzer.close();
         } catch (IOException var11) {
            var11.printStackTrace();
         }
      }
   }

   public static void testsearchSynta() {
      Analyzer analyzer = new EnglishAnalyzer();
      MemoryIndex index = new MemoryIndex();
      QueryParser parser = new QueryParser("content", analyzer);
      float score = 0.0F;
      index.addField(
         "content",
         "Congratulations, bbaab roam your recharge succeeded. Your balance is 95.30 Nfa, Your account expires after 15/08/2018 Readings about Salmons and other select Alaska fishing Manuals",
         analyzer
      );

      try {
         score = index.search(parser.parse("+salmon +fish manual +\"select Alaska\""));
         System.out.println("+salmon +fish manual +\"select Alaska\" : score is " + score);
         score = index.search(parser.parse("salmon fish"));
         System.out.println("salmon fish : score is " + score);
         score = index.search(parser.parse("salmon OR fish"));
         System.out.println("salmon OR fish : score is " + score);
         score = index.search(parser.parse("salmon || fish"));
         System.out.println("salmon || fish : score is " + score);
         score = index.search(parser.parse("salmon AND fish"));
         System.out.println("salmon AND fish : score is " + score);
         score = index.search(parser.parse("salmon && fish"));
         System.out.println("salmon && fish : score is " + score);
         score = index.search(parser.parse("+salmon +fish"));
         System.out.println("+salmon +fish : score is " + score);
         score = index.search(parser.parse("salmon NOT fish"));
         System.out.println("salmon NOT fish : score is " + score);
         score = index.search(parser.parse("salmon !fish"));
         System.out.println("salmon !fish : score is " + score);
         score = index.search(parser.parse("salmon ! fish"));
         System.out.println("salmon ! fish : score is " + score);
         score = index.search(parser.parse("salmon -fish"));
         System.out.println("salmon -fish : score is " + score);
         score = index.search(parser.parse("bb??b"));
         System.out.println("bb??b : score is " + score);
         score = index.search(parser.parse("bb??b^4"));
         System.out.println("bb??b^4 : score is " + score);
         score = index.search(parser.parse("roam~"));
         System.out.println("roam~ : score is " + score);
         score = index.search(parser.parse("raom~0.7"));
         System.out.println("raom~0.7 : score is " + score);
         score = index.search(parser.parse("\"Congratulations account\"~20"));
         System.out.println("\"Congratulations account\"~20 : score is " + score);
         score = index.search(parser.parse("+make  +love"));
         System.out.println("make  AND  love : score is " + score);
      } catch (ParseException var5) {
         var5.printStackTrace();
      }
   }

   public static void testbitop() {
      long clslabel = 0L;
      String pos = "035";
      int label = Integer.parseInt(pos);
      System.out.println("label is " + label);
      long tmpl = 1L;
      tmpl <<= label;
      System.out.println("tmpl is " + tmpl);
      System.out.println("tmpl in binary is " + Long.toBinaryString(tmpl));
      clslabel |= tmpl;
      System.out.println("clslabel is " + clslabel);
      System.out.println("clslabel in binary is " + Long.toBinaryString(clslabel));
   }

   public static void testsearchSyntaCn() {
      Analyzer analyzer = new IKAnalyzer();
      MemoryIndex index = new MemoryIndex();
      QueryParser parser = new QueryParser("content", analyzer);
      float score = 0.0F;
      index.addField("content", "厉害了我的国一经播出，受到各方好评，强烈激发了国人的爱国之情、自豪感！", analyzer);

      try {
         score = index.search(parser.parse("+厉害 +各方好评 +\"国人的爱国之情\""));
         System.out.println("+厉害 +各方好评 +\"国人的爱国之情\" : score is " + score);
         score = index.search(parser.parse("各方好评"));
         System.out.println("+各方好评 : score is " + score);
         score = index.search(parser.parse("各方的好评"));
         System.out.println("+各方的 好评 : score is " + score);
         score = index.search(parser.parse("\"各方的好评\""));
         System.out.println("\"各方的好评\" : score is " + score);
         score = index.search(parser.parse("厉害"));
         System.out.println("厉害: score is " + score);
         score = index.search(parser.parse("厉害 各方的好评"));
         System.out.println("厉害 各方的好评 : score is " + score);
      } catch (ParseException var5) {
         var5.printStackTrace();
      }
   }

   public static void testMoreLikeThis() throws IOException {
      Analyzer analyzer = new IKAnalyzer();
      MemoryIndex index = new MemoryIndex();
      index.addField("content", "厉害了我的国一经播出，受到各方好评，强烈激发了国人的爱国之情、自豪感！", analyzer);
      MoreLikeThis mlt = new MoreLikeThis(index.createSearcher().getIndexReader());
      mlt.setMinTermFreq(0);
      mlt.setMinDocFreq(0);
      mlt.setFieldNames(new String[]{"content"});
      mlt.setAnalyzer(analyzer);
      Reader sreader = new StringReader("受到各方好评");
      Query query = mlt.like("content", sreader);
      ScoreDoc[] hits = index.createSearcher().search(query, 10).scoreDocs;
      float score = 0.0F;

      for (int i = 0; i < hits.length; i++) {
         score = hits[i].score;
         if (score > 0.0F) {
            System.out.println("it's a match");
         } else {
            System.out.println("no match found");
         }
      }
   }

   public static void testLabelKeywords() throws IOException {
      LabelbyKeywords labelkeywords = new LabelbyKeywords("CN");
      File likefile = new File("\\\\whs-pc\\zzzzzzzzzzzz\\morelikethisdoc\\3_pop3_1565684342_000006.txt");
      String likestr = FileUtils.readFileToString(likefile, "UTF-8");
      labelkeywords.createIndex(likestr);
      File otherdir = new File("\\\\whs-pc\\zzzzzzzzzzzz\\morelikethisdoc\\otherdocs");
      File[] otherfiles = otherdir.listFiles();
      String otherstr1 = FileUtils.readFileToString(otherfiles[0], "UTF-8");
      boolean matched1 = labelkeywords.matchone("第五套|人民币");
      long start = System.currentTimeMillis();

      for (int i = 0; i < 1000; i++) {
         for (File f : otherfiles) {
            String otherstr = FileUtils.readFileToString(f, "UTF-8");
            boolean fx = labelkeywords.matchone("第五套|人民币");
         }
      }

      long keyword = System.currentTimeMillis();
      System.out.println("keywords cost : " + (keyword - start) / 1000L + "second");

      for (int i = 0; i < 1000; i++) {
         for (File f : otherfiles) {
            String otherstr = FileUtils.readFileToString(f, "UTF-8");
            boolean matched = labelkeywords.matchone("第五套|人民币");
            float var18 = labelkeywords.morelikeone(otherstr);
         }
      }

      long keywordandlike = System.currentTimeMillis();
      System.out.println("keywordandlike cost : " + (keywordandlike - keyword) / 1000L + "second");
      labelkeywords.resetIndex();
   }

   public static void testLabel1Keywords() throws IOException {
      LabelbyKeywords labelkeywords = new LabelbyKeywords("CN");
      new File("\\\\whs-pc\\zzzzzzzzzzzz\\morelikethisdoc\\3_pop3_1565684342_000006.txt");
      String likestr = "测试微信文件苹果银行卡6217991389994758764 测试热线400-898-1617";
      labelkeywords.createIndex(likestr);
      boolean matched1 = labelkeywords.matchone(" 微信 || 习惯");
      labelkeywords.resetIndex();
   }

   public static void testMLTScore() throws IOException {
      LabelbyKeywords labelkeywords = new LabelbyKeywords("CN");
      File likefile = new File("\\\\whs-pc\\zzzzzzzzzzzz\\morelikethisdoc\\otherdocs\\5.txt");
      System.out.println("likefile size is : " + likefile.length());
      String likestr = FileUtils.readFileToString(likefile, "UTF-8");
      labelkeywords.createIndex(likestr);
      float score = labelkeywords.morelikeone(likestr);
      System.out.println("score of likestr is : " + score);
      File otherdir = new File("\\\\whs-pc\\zzzzzzzzzzzz\\morelikethisdoc\\otherdocs");
      File[] otherfiles = otherdir.listFiles();

      for (File f : otherfiles) {
         System.out.println("File size is : " + f.length());
         String otherstr = FileUtils.readFileToString(f, "UTF-8");
         score = labelkeywords.morelikeone(otherstr);
         System.out.println("score is : " + score);
      }

      labelkeywords.resetIndex();
   }

   public static void testMLTScore1() throws IOException {
      LabelbyKeywords labelkeywords = new LabelbyKeywords("CN");
      File likefile = new File("F:\\data\\fingerprint\\21\\introduction.txt");
      System.out.println("likefile size is : " + likefile.length());
      String likestr = FileUtils.readFileToString(likefile, "UTF-8");
      labelkeywords.createIndex(likestr);
      File otherfile = new File("F:\\data\\fingerprint\\21\\3_httpget_1571913315_000178.xml");
      String otherstr = FileUtils.readFileToString(otherfile, "UTF-8");
      float score = labelkeywords.morelikeone(likestr);
      System.out.println("score of likestr is : " + otherstr);
      labelkeywords.resetIndex();
   }

   public static void testTokenizer4mlt() throws IOException {
      IKAnalyzer analyzer = new IKAnalyzer(true);
      File likefile = new File("F:\\data\\fingerprint\\21\\introduction.txt");
      System.out.println("likefile size is : " + likefile.length());
      String likestr = FileUtils.readFileToString(likefile, "UTF-8");
      TokenStream ts = analyzer.tokenStream("myfield", new StringReader(likestr));
      OffsetAttribute offsetAtt = ts.addAttribute(OffsetAttribute.class);
      CharTermAttribute termAttribute = ts.getAttribute(CharTermAttribute.class);

      try {
         ts.reset();

         while (ts.incrementToken()) {
            System.out.println("termAttribute: " + termAttribute.toString());
            System.out.println("token: " + ts.reflectAsString(true));
            System.out.println("token start offset: " + offsetAtt.startOffset());
            System.out.println("  token end offset: " + offsetAtt.endOffset());
         }

         ts.end();
      } catch (IOException var15) {
         var15.printStackTrace();
      } finally {
         try {
            ts.close();
            analyzer.close();
         } catch (IOException var14) {
            var14.printStackTrace();
         }
      }
   }

   public static void testMyMLT(MyMoreLikeThis mmlt) throws IOException {
      File likefile = new File("F:\\data\\fingerprint\\21\\introduction.txt");
      String likestr = FileUtils.readFileToString(likefile, "UTF-8");
      File otherfile = new File("F:\\data\\fingerprint\\21\\introduction1.txt");
      String otherstr = FileUtils.readFileToString(otherfile, "UTF-8");
      Map<String, Integer> cluetermmap = IKAnalyzerUtils.getTermCount(likestr);
      float score = mmlt.getScore(cluetermmap, otherstr);
      System.out.println("score is : " + score);
   }

   public static void testMyMLTrepeat() throws IOException {
      MyMoreLikeThis mmlt = new MyMoreLikeThis();

      for (int i = 0; i < 100; i++) {
         testMyMLT(mmlt);
      }
   }

   public static void testRegQuery() {
      Analyzer analyzer = new IKAnalyzer();
      MemoryIndex index = new MemoryIndex();
      index.addField("content", "厉害了我的国一经播出，受到各方好评，sj@aa.com强烈13693607398激发了国人的爱国之情、自豪感！", analyzer);
      Query query = null;
      Term term = new Term("content", "([a-z0-9A-Z]+[-_|\\.]?)+[a-z0-9A-Z]*@([a-z0-9A-Z]+(-[a-z0-9A-Z]+)?\\.)+[a-zA-Z]{2,}");
      Query var5 = new RegexpQuery(term);
      float score = index.search(var5);
      System.out.println(score);
   }

   public static void testLabelKeywords_() throws IOException {
      LabelbyKeywords labelkeywords = new LabelbyKeywords("CN");
      String likestr = "{\"msg\":\"success\",\"data\":{\"newmail\":0,\"folders\":{\"folders\":[{\"total\":4,\"size\":63663,\"unread\":0,\"name\":\"收件箱\",\"folder_id\":1,\"type\":\"sys\"},{\"total\":0,\"size\":0,\"unread\":0,\"name\":\"星标邮件\",\"folder_id\":-5,\"type\":\"sys\"},{\"total\":48,\"size\":1466906,\"unread\":0,\"name\":\"已发送\",\"folder_id\":3,\"type\":\"sys\"},{\"total\":0,\"size\":0,\"unread\":0,\"name\":\"已删除\",\"folder_id\":4,\"type\":\"sys\",\"autCleanDays\":30},{\"total\":1,\"size\":14811,\"unread\":1,\"name\":\"垃圾邮件\",\"folder_id\":5,\"type\":\"sys\",\"autCleanDays\":30},{\"total\":0,\"size\":0,\"unread\":0,\"name\":\"未读邮件\",\"folder_id\":-2,\"type\":\"sys\"},{\"total\":0,\"size\":0,\"unread\":0,\"name\":\"草稿箱\",\"folder_id\":2,\"type\":\"sys\"}],\"totalSize\":1545380,\"isUnlimited\":true,\"quotaSize\":2147483648,\"totalUnread\":0,\"totalMail\":53},\"t\":158987826337,\"i\":4},\"status\":200}";
      labelkeywords.createIndex(likestr);
      boolean matched1 = labelkeywords.matchone(
         "机密|绝密|\"秘密\"|商密|商秘|商业秘密|安全保卫|安全性测评|标底|二次系统安全防护|法律纠纷|方案|概算|规划|核心商密|会议记录|稽核报告|纪要|决算报告|内部事项|内部资料|体制改革|\"同业对标\"|投标|投资|薪酬|预算|预算报告|招标|中标价格|自动化系统|综合计划"
      );
      labelkeywords.resetIndex();
   }

   public static void testMyMLT_mdlp() throws IOException {
      MyMoreLikeThis mmlt = new MyMoreLikeThis();
      File likefile = new File("F:\\data\\fingerprint\\526\\咪咕文化DLP测试文档.txt");
      String likestr = FileUtils.readFileToString(likefile, "UTF-8");
      File otherfile = new File("F:\\data\\fingerprint\\549\\1.txt");
      String otherstr = FileUtils.readFileToString(otherfile, "UTF-8");
      String keywords = mmlt.getTermCount_mdlp(likestr);
      float score = mmlt.getScore_mdlp(keywords, otherstr);
      System.out.println("score is : " + score);
   }
}
