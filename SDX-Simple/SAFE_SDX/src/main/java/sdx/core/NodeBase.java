package sdx.core;

import java.util.Map;
import java.util.HashMap;

import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;

class NodeBaseInfo {
  public String nisn;
  public String niurl;
  public String nihash;
  public String ntype;
  public String domain;

  public NodeBaseInfo(String nisn, String niurl, String nihash, String ntype, String domain) {
    this.nisn = nisn;
    this.niurl = niurl;
    this.nihash = nihash;
    this.ntype = ntype;
    this.domain = domain;
  }
}

public class NodeBase {

  private static Map<String, NodeBaseInfo> images = new HashMap<>();

  static {
    images.put(
      "Bro",
      new NodeBaseInfo("Centos 7.4 Bro",
        "http://geni-images.renci.org/images/standard/centos/centos7.4-bro-v1.0.4/centos7.4-bro-v1.0.4.xml",
        "50c973571fc6da95c3f70d0f71c9aea1659ff780",
        "XO Medium",
        "BBN/GPO (Boston, MA USA) XO Rack"));
    images.put(
      "Ubuntu 14.04",
      new NodeBaseInfo("Ubuntu 14.04 v1.0.3",
        "http://geni-images.renci.org/images/standard/ubuntu/ubuntu-14.04-v1.0.3/ubuntu-14.04-v1.0.3.xml",
        "5196cbf03938af57a9a3f9034613c3882066bc91",
        "XO Medium",
        "BBN/GPO (Boston, MA USA) XO Rack"));
    images.put(
      "Ubuntu 16.04",
      new NodeBaseInfo("Ubuntu 16.04",
        "http://geni-images.renci.org/images/standard/ubuntu/ubuntu-16.04-v1.0.3/ubuntu-16.04-v1.0.3.xml",
        "fe7dd9f25fab9bab890ce653319192875fa106a0",
        "XO Medium",
        "BBN/GPO (Boston, MA USA) XO Rack"));
    images.put(
      "Ubuntu 17.10",
      new NodeBaseInfo("Ubuntu 17.10 v1.0.2",
        "http://geni-images.renci.org/images/standard/ubuntu/ubuntu-17.10-v1.0.2/ubuntu-17.10-v1.0.2.xml",
        "32c6a6ab0062ce8963a927925b43d8e962395fc6",
        "XO Medium",
        "SL (Chicago, IL USA) XO Rack"));

    images.put(
      "Ubuntu 17.10 XL",
      new NodeBaseInfo("Ubuntu 17.10 v1.0.2",
        "http://geni-images.renci.org/images/standard/ubuntu/ubuntu-17.10-v1.0.2/ubuntu-17.10-v1.0.2.xml",
        "32c6a6ab0062ce8963a927925b43d8e962395fc6",
        "XO Extra large",
        "SL (Chicago, IL USA) XO Rack"));
  }

  public static NodeBaseInfo getImageInfo(String key){
    return images.get(key);
  }

  public static ComputeNode makeNode(Slice s, String type, String name) {
    NodeBaseInfo tbase = images.get(type);
    if (tbase != null) {
      ComputeNode node = s.addComputeNode(name);
      node.setImage(tbase.niurl, tbase.nihash, tbase.nisn);
      node.setNodeType(tbase.ntype);
      node.setDomain(tbase.domain);
      return node;
    }
    return null;
  }

  public static ComputeNode makeNode(Slice s, String type, String name, String site) {
    ComputeNode node = makeNode(s, type, name);
    if (node != null)
      node.setDomain(site);
    return node;
  }
}
