package com.github.dingjingmaster.tika.main.ndimporter.common;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.wltea.analyzer.lucene.IKAnalyzer;

public class IKAnalyzerUtils {
   private static IKAnalyzer analyzer = new IKAnalyzer(true);

   public static Map<String, Integer> getTermCount(String parsestr) {
      TokenStream ts = analyzer.tokenStream("myfield", new StringReader(parsestr));
      CharTermAttribute termAttribute = ts.getAttribute(CharTermAttribute.class);
      Map<String, Integer> term2count = new HashMap<>();

      try {
         ts.reset();

         while (ts.incrementToken()) {
            String term = termAttribute.toString();
            term2count.merge(term, 1, (x, y) -> x + y);
         }

         ts.end();
      } catch (IOException var13) {
         var13.printStackTrace();
      } finally {
         try {
            ts.close();
         } catch (IOException var12) {
            var12.printStackTrace();
         }
      }

      return term2count;
   }

   public static List<String> getTerms(String parsestr) {
      List<String> resultList = new ArrayList<>();

      try (TokenStream ts = analyzer.tokenStream("myfields", new StringReader(parsestr))) {
         CharTermAttribute termAttribute = ts.getAttribute(CharTermAttribute.class);
         ts.reset();

         while (ts.incrementToken()) {
            String term = termAttribute.toString();
            resultList.add(term);
         }

         ts.end();
      } catch (IOException var16) {
         var16.printStackTrace();
      }

      return resultList;
   }
}
