package exoplex.sdx.slice.exogeni;

import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;

import java.util.HashMap;
import java.util.Map;

public class NodeBase {

  public static final String CENTOS_7_6 = "Centos 7.6";
  public static final String UBUNTU16 = "Ubuntu 16.04";
  public static final String UBUNTU19 = "Ubuntu 19.10";
  public static final String xoMedium = "XO Medium";
  public static final String xoLarge = "XO Large";
  public static final String xoExtraLarge = "XO Extra large";

  private static Map<String, NodeBaseInfo> images = new HashMap<>();

  static {
    images.put(
      CENTOS_7_6,
      new NodeBaseInfo("CentOS 6.10 QCOW2",
        "http://geni-images.renci.org/images/standard/centos-comet/centos6" +
          ".10-comet/centos6.10-comet.xml",
        "c21cce26d89e336695c64f94c3ccfebac88e856c")
    );
    images.put(
      UBUNTU16,
      new NodeBaseInfo(
        "Ubuntu 16.04 QCOW2",
        "http://geni-images.renci.org/images/standard/ubuntu-comet/ubuntu-16" +
          ".04-comet/ubuntu-16.04-comet.xml",
        "cd51e0f0399b54b3c6b48917ec819ffe75d8c200"
      )
    );
    images.put(
      UBUNTU19,
      new NodeBaseInfo(
        "Ubuntu 19.10 QCOW2",
        "http://geni-images.renci.org/images/standard/ubuntu-comet/ubuntu-19" +
          ".10-comet/ubuntu-19.10-comet.xml",
        "d9d95a052b0827f581843e1ba985712fbe8cfd06"
      )
    );

  }

  public static NodeBaseInfo getImageInfo(String key) {
    return images.get(key);
  }

  public static ComputeNode makeNode(Slice s, String type, String name) {
    NodeBaseInfo tbase = images.get(type);
    if (tbase != null) {
      ComputeNode node = s.addComputeNode(name);
      node.setImage(tbase.imageUrl, tbase.imageHash, tbase.imageName);
      node.setNodeType(xoMedium);
      node.setDomain(SiteBase.get("BBN"));
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
