package exoplex.sdx.slice.exogeni;

import java.util.HashMap;

public class SiteBase {
  public static HashMap<String, String> sites = new HashMap<>();

  static {
    sites.put("BBN", "BBN/GPO (Boston, MA USA) XO Rack");
    sites.put("CIENA", "CIENA (Ottawa,  CA) XO Rack");
    sites.put("FIU", "FIU (Miami, FL USA) XO Rack");
    sites.put("GWU", "GWU (Washington DC,  USA) XO Rack");
    sites.put("OSF", "OSF (Oakland, CA USA) XO Rack");
    sites.put("RENCI", "RENCI (Chapel Hill, NC USA) XO Rack");
    sites.put("SL", "SL (Chicago, IL USA) XO Rack");
    sites.put("TAMU", "TAMU (College Station, TX, USA) XO Rack");
    sites.put("UAF", "UAF (Fairbanks, AK, USA) XO Rack");
    sites.put("UFL", "UFL (Gainesville, FL USA) XO Rack");
    sites.put("UH", "UH (Houston, TX USA) XO Rack");
    sites.put("UMASS", "UMass (UMass Amherst, MA, USA) XO Rack");
    sites.put("UNF", "UNF (Jacksonville, FL) XO Rack");
    sites.put("UVA", "UvA (Amsterdam, The Netherlands) XO Rack");
    sites.put("WSU", "WSU (Detroit, MI, USA) XO Rack");
    sites.put("WVN", "WVN (UCS-B series rack in Morgantown, WV, USA)");
    sites.put("PSC", "PSC (Pittsburgh, PA, USA) XO Rack");
  }

  public static String get(String site) {
    if (sites.containsKey(site)) {
      return sites.get(site);
    } else if (sites.containsValue(site)) {
      return site;
    } else {
      for (String geniSite : sites.values()) {
        if (geniSite.contains(site)) {
          return geniSite;
        }
      }
      return null;
    }
  }
}
