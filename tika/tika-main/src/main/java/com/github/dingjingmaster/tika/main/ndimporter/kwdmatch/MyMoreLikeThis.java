package com.github.dingjingmaster.tika.main.ndimporter.kwdmatch;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.wltea.analyzer.lucene.IKAnalyzer;

public class MyMoreLikeThis {
   IKAnalyzer analyzer = new IKAnalyzer(true);

   public Map<String, Integer> getTermCount(String parsestr) {
      TokenStream ts = this.analyzer.tokenStream("myfield", new StringReader(parsestr));
      CharTermAttribute termAttribute = ts.getAttribute(CharTermAttribute.class);
      Map<String, Integer> term2count = new HashMap<>();

      try {
         ts.reset();

         while (ts.incrementToken()) {
            String term = termAttribute.toString();
            term2count.merge(term, 1, (x, y) -> x + y);
         }

         ts.end();
      } catch (IOException var14) {
         var14.printStackTrace();
      } finally {
         try {
            ts.close();
         } catch (IOException var13) {
            var13.printStackTrace();
         }
      }

      return term2count;
   }

   public float getScore(Map<String, Integer> clueterms, Map<String, Integer> attmaps) {
      int count = 0;

      for (String term : attmaps.keySet()) {
         if (clueterms.keySet().contains(term)) {
            count++;
         }
      }

      return (float)count / (float)clueterms.keySet().size();
   }

   public float getScore(Map<String, Integer> clueterms, String otherstr) {
      Map<String, Integer> attmaps = this.getTermCount(otherstr);
      int count = 0;

      for (String term : attmaps.keySet()) {
         if (clueterms.keySet().contains(term)) {
            count++;
         }
      }

      return (float)count / (float)clueterms.keySet().size();
   }

   public float getScore_mdlp(String clueterms, String otherstr) {
      if (clueterms == null || clueterms.isEmpty()) {
         return -1.0F;
      } else if (otherstr != null && !otherstr.isEmpty()) {
         String[] splits = clueterms.split(",");
         if (splits != null && splits.length != 0) {
            List<String> keywords = Arrays.asList(splits);
            Map<String, Integer> attmaps = this.getTermCount(otherstr);
            int count = 0;

            for (String term : attmaps.keySet()) {
               if (keywords.contains(term)) {
                  count++;
               }
            }

            return (float)count / (float)keywords.size();
         } else {
            return -1.0F;
         }
      } else {
         return -1.0F;
      }
   }

   public String getTermCount_mdlp(String parsestr) {
      if (parsestr != null && !parsestr.isEmpty()) {
         TokenStream ts = this.analyzer.tokenStream("myfield", new StringReader(parsestr));
         CharTermAttribute termAttribute = ts.getAttribute(CharTermAttribute.class);
         Map<String, Integer> term2count = new HashMap<>();

         try {
            ts.reset();

            while (ts.incrementToken()) {
               String term = termAttribute.toString();
               term2count.merge(term, 1, (x, y) -> x + y);
            }

            ts.end();
         } catch (IOException var14) {
            var14.printStackTrace();
         } finally {
            try {
               ts.close();
            } catch (IOException var13) {
               var13.printStackTrace();
            }
         }

         StringBuilder strbuilder = new StringBuilder();
         term2count.forEach((key, value) -> {
            strbuilder.append(key);
            strbuilder.append(",");
         });
         return strbuilder.length() < 1 ? null : strbuilder.substring(0, strbuilder.length() - 1);
      } else {
         return null;
      }
   }
}
