package com.github.dingjingmaster.tika.main.ndimporter.fingerprint.service;

import com.github.dingjingmaster.tika.main.ndimporter.common.MurmurHash;
import com.github.dingjingmaster.tika.main.ndimporter.common.IKAnalyzerUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimHashService2 {
   private static final Logger log = LoggerFactory.getLogger(SimHashService2.class);
   private final int bitNum = 64;
   private final int fracCount;
   private final int fracBitNum;
   private final int hammingThresh;
   Map<String, List<Map<String, List<Long>>>> database = Collections.synchronizedMap(new HashMap<>());

   public SimHashService2() {
      this(4, 3);
   }

   public SimHashService2(int fracCount, int hammingThresh) {
      this.fracCount = fracCount;
      this.fracBitNum = 64 / fracCount;
      this.hammingThresh = hammingThresh;
   }

   private String preProcess(String content) {
      return content;
   }

   public long simHash(String tokens) {
      tokens = this.preProcess(tokens);
      int bitNum = 64;
      int[] weight = new int[bitNum];
      Map<String, Integer> segMaps = IKAnalyzerUtils.getTermCount(tokens);

      for (String word : segMaps.keySet()) {
         long wordHash = MurmurHash.hash64(word);

         for (int i = 0; i < bitNum; i++) {
            if ((wordHash >> i & 1L) == 1L) {
               weight[i]++;
            } else {
               weight[i]--;
            }
         }
      }

      StringBuilder sb = new StringBuilder();

      for (int ix = 0; ix < bitNum; ix++) {
         sb.append(weight[ix] > 0 ? 1 : 0);
      }

      return new BigInteger(sb.toString(), 2).longValue();
   }

   public List<String> splitSimhash(Long simhash) {
      int bitNum = 64;
      int fracBitNum = this.fracBitNum;
      List<String> ls = new ArrayList<>();
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < bitNum; i++) {
         sb.append(simhash >> i & 1L);
         if ((i + 1) % fracBitNum == 0) {
            ls.add(sb.toString());
            sb.setLength(0);
         }
      }

      return ls;
   }

   private int hamming(Long s1, Long s2) {
      int bitNum = 64;
      int dis = 0;

      for (int i = 0; i < bitNum; i++) {
         if ((s1 >> i & 1L) != (s2 >> i & 1L)) {
            dis++;
         }
      }

      return dis;
   }

   public void createIndex(String clueid, String fingerprint) {
      if (!StringUtils.isBlank(clueid)) {
         if (StringUtils.isBlank(fingerprint)) {
            this.database.remove(clueid);
         } else {
            int fracCount = this.fracCount;
            List<Map<String, List<Long>>> ffList = new ArrayList<>();

            for (int i = 0; i < fracCount; i++) {
               ffList.add(new HashMap<>());
            }

            this.database.put(clueid, ffList);
            String[] array = fingerprint.split(",");

            for (String str : array) {
               if (!StringUtils.isBlank(str)) {
                  try {
                     long simhash = Long.parseLong(str);
                     List<String> fracList = this.splitSimhash(simhash);

                     for (int i = 0; i < fracList.size(); i++) {
                        String frac = fracList.get(i);
                        Map<String, List<Long>> fracMap = ffList.get(i);
                        if (fracMap == null) {
                           ffList.add(fracMap);
                        }

                        if (fracMap.containsKey(frac)) {
                           fracMap.get(frac).add(simhash);
                        } else {
                           List<Long> l = new ArrayList<>();
                           l.add(simhash);
                           fracMap.put(frac, l);
                        }
                     }
                  } catch (Exception var17) {
                  }
               }
            }

            System.out.println(clueid + " fingerprint set : " + ffList);
         }
      }
   }

   public String getScore(String clueid, String content) {
      int fracCount = this.fracCount;
      List<Map<String, List<Long>>> index = this.database.get(clueid);
      Long mySimhash = null;
      int dis = 64;
      Long fingerprint = null;

      try {
         for (int i = 0; i < fracCount; i++) {
            Map<String, List<Long>> map = index.get(i);
            if (map != null) {
               if (mySimhash == null) {
                  mySimhash = this.simHash(content);
               }

               if (mySimhash != 0L) {
                  List<String> pracList = this.splitSimhash(mySimhash);

                  for (int j = 0; j < pracList.size(); j++) {
                     String frac = pracList.get(i);
                     List<Long> list = map.get(frac);
                     if (list != null) {
                        for (Long ffSimhash : list) {
                           int hammingDistance = this.hamming(mySimhash, ffSimhash);
                           if (hammingDistance == 0) {
                              return ffSimhash + ":0.99";
                           }

                           if (hammingDistance < dis) {
                              dis = hammingDistance;
                              fingerprint = ffSimhash;
                           }
                        }
                     }
                  }
               }
            }
         }
      } catch (Exception var17) {
         log.error("", (Throwable)var17);
      }

      if (dis >= this.hammingThresh) {
         return ":0";
      } else {
         float score = 1.0F - (float)dis / (float)this.hammingThresh;
         return fingerprint + ":" + score;
      }
   }

   public static void main(String[] args) {
      SimHashService2 sim = new SimHashService2(4, 20);
      String string = "劳斯莱斯女神\n\n这个车标的设计者是英国画家兼雕刻家查尔斯·赛克斯。20世纪初，经朋友蒙塔古邀请，赛克斯负责为劳斯莱斯设计一尊雕塑车标。当时，已婚的蒙塔古疯狂地爱着他的女秘书桑顿，恳请赛克斯以桑顿为原型设计车标。所以，赛克斯的最初设计中，雕像是一尊披着长袍的女人将手指放在嘴唇上，象征着蒙塔古与桑顿之间不能说的秘密情史。这个恋爱故事历经重重磨难，桑顿身份地位曾是脱衣舞女郎，所以两人根本无法在一起生活，在得到家庭与蒙塔古妻子的谅解后，两人最终可以走到一起，不幸的是，后来桑顿在一次乘船旅行中不幸遭遇德军水雷，永远沉入了冰冷的大海。\n\n后来，他们这段美好的爱情又略带凄惨故事就保留在了这个车标上，罗 -罗二人也是蒙塔古的好友，他们得知这件事之后非常感动。后来，他们邀请赛克斯又把它改为双手如羽翼般向后伸展的形象，也就是今天的“飞天女神”。 1911年，它正式成为劳斯莱斯车的车标。从此，劳斯莱斯的飞天女神车标更是美丽的爱情象征了!";
      long simhash = sim.simHash(string);
      sim.createIndex("1001", simhash + "");
      System.out.println(simhash);
      String str1 = "被投诉的商家系陕西华盛兴勇商贸有限公司汉台区分公司西环路店，汉中市市场监督管理局在其微信公众号通报了市场监管所核查情况,汉中市市场监督管理局," + string.substring(0, string.length() - 10);
      long simhash2 = sim.simHash(str1);
      sim.createIndex("1002", simhash2 + "," + simhash);
      System.out.println(sim.getScore("1001", str1));
      System.out.println(sim.getScore("1002", str1));
      System.out.println(sim.getScore("1001", string));
   }

   class MyFingerPrint {
      String id;
      Long simhash;

      protected MyFingerPrint(String id, Long simhash) {
         this.id = id;
         this.simhash = simhash;
      }

      public String getId() {
         return this.id;
      }

      public void setId(String id) {
         this.id = id;
      }

      public Long getSimhash() {
         return this.simhash;
      }

      public void setSimhash(Long simhash) {
         this.simhash = simhash;
      }
   }
}
