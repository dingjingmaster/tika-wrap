package com.github.dingjingmaster.tika.main.ndimporter.kwdmatch;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wltea.analyzer.lucene.IKAnalyzer;

public class LabelbyKeywords {
   private static final Logger logger = LoggerFactory.getLogger(LabelbyKeywords.class);
   Analyzer analyzer;
   MemoryIndex index = new MemoryIndex();
   QueryParser parser;
   Map<String, List<String>> keywordsdef = null;
   String language;
   MoreLikeThis mlt = new MoreLikeThis(this.index.createSearcher().getIndexReader());
   float mltlimitscore = 0.0F;

   public LabelbyKeywords(Map<String, List<String>> keywordsdef, String language) throws IOException {
      this.keywordsdef = keywordsdef;
      this.language = language;
      if (language.equals("CN")) {
         this.analyzer = new IKAnalyzer();
      } else if (language.equals("AR")) {
         this.analyzer = new ArabicAnalyzer();
      } else {
         this.analyzer = new EnglishAnalyzer();
      }

      this.parser = new QueryParser("content", this.analyzer);
   }

   public LabelbyKeywords(String language) throws IOException {
      this.language = language;
      if (language.equals("CN")) {
         this.analyzer = new IKAnalyzer();
      } else if (language.equals("AR")) {
         this.analyzer = new ArabicAnalyzer();
      } else {
         this.analyzer = new EnglishAnalyzer();
      }

      this.parser = new QueryParser("content", this.analyzer);
      this.mlt.setMinTermFreq(0);
      this.mlt.setMinDocFreq(2);
      this.mlt.setFieldNames(new String[]{"content"});
      this.mlt.setAnalyzer(this.analyzer);
   }

   public String GetLabels(String msg) {
      StringBuilder strb = new StringBuilder();

      for (Entry<String, List<String>> entry : this.keywordsdef.entrySet()) {
         boolean ret = this.match1keywords(msg, entry.getValue());
         if (ret) {
            strb.append(entry.getKey());
            strb.append(",");
            logger.warn("found matched msg : " + msg);
            logger.warn("category is " + entry.getKey());
         }
      }

      return strb.length() > 0 ? strb.deleteCharAt(strb.length() - 1).toString() : "";
   }

   public boolean match1keywords(String msg, List<String> keywords) {
      if (msg.length() == 0) {
         return false;
      } else {
         this.index.addField("content", msg, this.analyzer);
         boolean found = false;

         for (String phrase : keywords) {
            try {
               float score = this.index.search(this.parser.parse(phrase));
               if (score > 0.0F) {
                  logger.warn("matched msg : " + msg);
                  logger.warn("matched phrase is : " + phrase);
                  found = true;
                  break;
               }
            } catch (ParseException var7) {
               logger.error("search exception : " + phrase, (Throwable)var7);
            }
         }

         this.index.reset();
         return found;
      }
   }

   public void createIndex(String attTxt) {
      this.index.addField("content", attTxt, this.analyzer);
   }

   public boolean matchone(String keywords) {
      boolean found = false;

      try {
         float score = this.index.search(this.parser.parse(keywords));
         String debugstr = this.index.toStringDebug();
         if (score > 0.0F) {
            found = true;
         }
      } catch (ParseException var5) {
         logger.error("search exception : " + keywords, (Throwable)var5);
         var5.printStackTrace();
      }

      return found;
   }

   public float morelikeone(String liketxt) {
      Reader sreader = new StringReader(liketxt);
      float score = 0.0F;

      try {
         Query query = this.mlt.like("content", sreader);
         ScoreDoc[] hits = this.index.createSearcher().search(query, 10).scoreDocs;
         int i = 0;
         if (i < hits.length) {
            return hits[i].score;
         }
      } catch (IOException var7) {
         var7.printStackTrace();
      }

      return score;
   }

   public void resetIndex() {
      this.index.reset();
   }
}
