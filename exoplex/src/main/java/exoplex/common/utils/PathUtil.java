package exoplex.common.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtil {
  public static String joinFilePath(String dir, String file) {
    Path dirPath = null;
    if (dir.startsWith("~/")) {
      dirPath = Paths.get(System.getProperty("user.home"), dir.replaceFirst("~/", ""));
    } else if (dir.startsWith("./")) {
      dirPath = Paths.get(System.getProperty("user.dir"), dir.replaceFirst("\\./", ""));
    } else if (!dir.startsWith("/")) {
      dirPath = Paths.get(System.getProperty("user.dir"), dir);
    } else {
      dirPath = Paths.get(dir);
    }
    return Paths.get(dirPath.toString(), file).toString();
  }
}
