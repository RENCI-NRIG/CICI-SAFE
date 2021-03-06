package exoplex.sdx.slice.exogeni;

import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;

import java.util.HashMap;
import java.util.Map;

public class NodeBase {

  public static final String CENTOS_7_6 = "Centos 7.6";
  public static final String UBUNTU16 = "Ubuntu 16.04";
  public static final String UBUNTU19 = "Ubuntu 19.04";
  public static final String xoMedium = "XO Medium";
  public static final String xoLarge = "XO Large";
  public static final String xoExtraLarge = "XO Extra large";

  private static Map<String, NodeBaseInfo> images = new HashMap<>();

  static {
    images.put(
      CENTOS_7_6,
      new NodeBaseInfo("CentOS 6.10 QCOW2 v1.0.2",
        "http://geni-images.renci.org/images/standard/centos-comet/centos6.10/centos6.10.xml",
        "94489b9e78d9c20ffe1c2b5b4b7d64d3c284345f")
    );
    images.put(
      UBUNTU16,
      new NodeBaseInfo(
        "Ubuntu 16.04 QCOW2 v1.0.2",
        "http://geni-images.renci.org/images/standard/ubuntu-comet/ubuntu-16.04/ubuntu-16.04.xml",
        "564ae072fb3500fa6721c2f976f24fc407e41b5e"
      )
    );
    images.put(
      UBUNTU19,
      new NodeBaseInfo(
        "Ubuntu 19.04 QCOW2 v1.0.4",
        "http://geni-images.renci.org/images/standard/ubuntu-comet/ubuntu-19.04.v4/ubuntu-19.04.v4.xml",
        "c7baea709ee9c4b354a175a315c6cf74a1c1a1f5"
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
