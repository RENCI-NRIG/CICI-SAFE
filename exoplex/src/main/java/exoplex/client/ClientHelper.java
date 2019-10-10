package exoplex.client;

import java.util.ArrayList;
import java.util.List;

public class ClientHelper {
  public static String[] parseCommands(String cmd) {
    List<String> cmds = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    for(char c: cmd.toCharArray()) {
      if(c == ' ') {
        if(sb.length() >0) {
          cmds.add(sb.toString());
          sb.setLength(0);
        }
      } else {
        sb.append(c);
      }
    }
    if(sb.length() > 0) {
      cmds.add(sb.toString());
    }
    String[] retVal = new String[cmds.size()];
    for(int i = 0; i < retVal.length; i ++) {
      retVal[i] = cmds.get(i);
    }
    return retVal;
  }
}
