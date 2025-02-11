package com.github.dingjingmaster.tika.main.ndimporter.clue;

import com.github.dingjingmaster.tika.main.ndimporter.kwdmatch.LabelbyKeywords;
import java.util.List;
import java.util.Map;

public class ClueManager {
   Map<String, List<Clue>> protocol2cluesnoprp;
   LabelbyKeywords matchKeywords;

   public ClueManager(String cluexmlpath) {
   }

   public List<String> getprotocolNoClue() {
      return null;
   }

   public List<Clue> getprotocolClue(String protocolid) {
      return null;
   }

   public String matchClues(String protocolid, String attTxt) {
      List<Clue> cluelist = this.getprotocolClue(protocolid);
      if (cluelist != null && !cluelist.isEmpty()) {
         StringBuilder clues = new StringBuilder();
         this.matchKeywords.createIndex(attTxt);

         for (Clue clue : cluelist) {
            for (ClueItem item : clue.getClueitemlist()) {
               boolean matched = this.matchKeywords.matchone(item.getKewords());
               if (matched) {
                  clues.append(item.getCaseid());
                  clues.append(".");
                  clues.append(item.getClueid());
                  clues.append("\t");
                  break;
               }
            }
         }

         this.matchKeywords.resetIndex();
         return clues.substring(0, clues.length() - 1);
      } else {
         return "";
      }
   }
}
