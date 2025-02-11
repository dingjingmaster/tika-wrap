package com.github.dingjingmaster.tika.main.ndimporter.clue;

import java.util.List;
import org.jdom2.Element;

public class Clue {
   String protocolid;
   String caseid;
   String clueid;
   List<ClueItem> clueitemlist;

   public Clue(Element xmlelement) {
   }

   public String getProtocolid() {
      return this.protocolid;
   }

   public void setProtocolid(String protocolid) {
      this.protocolid = protocolid;
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

   public List<ClueItem> getClueitemlist() {
      return this.clueitemlist;
   }

   public void setCluelist(List<ClueItem> cluelist) {
      this.clueitemlist = cluelist;
   }
}
