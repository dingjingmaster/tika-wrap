package com.github.dingjingmaster.tika.main.ndimporter.clue;

import org.jdom2.Element;

public class ClueItem {
   public String protocolid;
   public String caseid;
   public String clueid;
   public String kewords;
   public boolean hasother;

   public ClueItem(Element xmlelement) {
   }

   public boolean matchkeywords(String contents) {
      return true;
   }

   public String getCaseid() {
      return this.caseid;
   }

   public void setCaseid(String caseid) {
      this.caseid = caseid;
   }

   public String getClueid() {
      return this.clueid;
   }

   public void setClueid(String clueid) {
      this.clueid = clueid;
   }

   public String getKewords() {
      return this.kewords;
   }

   public void setKewords(String kewords) {
      this.kewords = kewords;
   }

   public boolean isHasother() {
      return this.hasother;
   }

   public void setHasother(boolean hasother) {
      this.hasother = hasother;
   }

   public String getProtocolid() {
      return this.protocolid;
   }

   public void setProtocolid(String protocolid) {
      this.protocolid = protocolid;
   }
}
