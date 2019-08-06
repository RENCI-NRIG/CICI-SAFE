package exoplex.sdx.bro;

import com.google.inject.Inject;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import exoplex.common.utils.ScpTo;
import exoplex.sdx.slice.SliceManager;
import exoplex.sdx.slice.SliceManagerFactory;
import exoplex.sdx.slice.SliceProperties;
import exoplex.sdx.slice.exogeni.SliceCommon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public abstract class SliceBase extends SliceCommon {
  private final static Logger logger = LogManager.getLogger(SliceBase.class);


  private SliceManager thisSlice;

  @Inject
  private SliceManagerFactory sliceManagerFactory;

  private Map<String, String> resourceIPs = new HashMap<>();
  private Map<String, Session> sessions = new HashMap<>();

  public SliceBase(String configPath) throws SampleSlice.SliceBaseException {
    try {
      System.out.println("Reading properties...");
      readConfig(configPath);
      System.out.println("Making proxy...");
      System.out.println("Setting access context...");
    } catch (Exception e) {
      throw new SampleSlice.SliceBaseException(e);
    }
  }

  public SliceBase(String configPath, String sliceName) throws SampleSlice.SliceBaseException {
    try {
      System.out.println("Reading properties...");
      readConfig(configPath);
      System.out.println("Making proxy...");
      System.out.println("Setting access context...");
      thisSlice = sliceManagerFactory.create(sliceName, pemLocation, keyLocation, controllerUrl,
        sshKey);
      thisSlice.loadSlice();
      for (String c : thisSlice.getComputeNodes()) {
        if (thisSlice.getManagementIP(c) == null) {
          break;
        }
        resourceIPs.put(c, thisSlice.getManagementIP(c));
      }
    } catch (Exception e) {
      throw new SampleSlice.SliceBaseException(e);
    }
  }

  public void createSlice() throws SampleSlice.SliceBaseException {
    try {
      System.out.println("Creating slice!!...");
      thisSlice = sliceManagerFactory.create(sliceName, pemLocation, keyLocation, controllerUrl, sshKey);
      thisSlice.createSlice();
      // TODO Add the option to ignore this? idk
      try {
        thisSlice.commitAndWait();
      } catch (Exception e) {
        e.printStackTrace();
      }
    } catch (Exception e) {
      throw new SampleSlice.SliceBaseException(e);
    }
  }

  public String retrieveNode(String name) {
    return thisSlice.getResourceByName(name);
  }

  public String retrieveIP(String c) {
    return resourceIPs.get(c);
  }

  public String retrieveIP(ComputeNode c) {
    return resourceIPs.get(c.getName());
  }

  public void execTillSuccess(String name, String command) throws SampleSlice.SliceBaseException {
    execTillSuccess(retrieveNode(name), command);
  }

  public void execTillSuccess(ComputeNode c, String command) throws SampleSlice.SliceBaseException {
    boolean ret;
    do {
      ret = execOnNode(c, command);
    } while (!ret);
  }

  public boolean execOnNode(String name, String command) throws SampleSlice.SliceBaseException {
    return execOnNode(retrieveNode(name), command);
  }

  public boolean execOnNode(ComputeNode c, String command) throws SampleSlice.SliceBaseException {
    return execOnNode(c, command, false) != null;
  }

  public String execOnNode(ComputeNode c, String command, boolean readOut) throws SampleSlice.SliceBaseException {
    System.out.println("Running " + command);
    ChannelExec channel;
    String output = "";
    try {
      Session session = makeSshSession(c);
      channel = (ChannelExec) session.openChannel("exec");
      channel.setCommand(command);
      BufferedReader err = new BufferedReader(new InputStreamReader(channel.getErrStream()));

      BufferedReader buff = null;
      if (readOut) {
        System.out.println("Initing buff");
        buff = new BufferedReader(new InputStreamReader(channel.getInputStream()));
      } else {
        channel.setInputStream(null);
      }

      channel.connect();
      if (readOut) {
        StringBuilder outp = new StringBuilder();
        String line;
        while ((line = buff.readLine()) != null)
          outp.append(line);
        output = outp.toString();
      }

      while (channel.getExitStatus() == -1) {
      }
      if (channel.getExitStatus() != 0) {
        System.out.println("Error exec!");
        String line;
        while ((line = err.readLine()) != null)
          System.out.println(line);
        return null;
      }
    } catch (IOException | JSchException e) {
      throw new SampleSlice.SliceBaseException(e);
    }
    channel.disconnect();
    return output;
  }

  public void sftpToNode(String name, String path) throws SampleSlice.SliceBaseException {
    sftpToNode(retrieveNode(name), path);
  }

  public void sftpToNode(ComputeNode c, String path) throws SampleSlice.SliceBaseException {
    String[] pathParts = path.split("/");
    String dstPath = SliceProperties.homeDir + pathParts[pathParts.length - 1];
    ScpTo.Scp(path, SliceProperties.userName, c.getManagementIP(), dstPath, sshKey);
  }

  public void releaseSshChannels() {
    for (Map.Entry<String, Session> entry : sessions.entrySet())
      if (entry.getValue().isConnected())
        entry.getValue().disconnect();
  }

  protected abstract void createSlice(Slice s);

  protected abstract String sliceName();

  private void readProperties(String configPath) throws IOException {
    Properties prop = new Properties();
    prop.load(new FileInputStream(configPath));
    pemLocation = prop.getProperty("pemLocation");
    sshKey = prop.getProperty("sshKey");
    controllerUrl = prop.getProperty("controllerUrl");
  }

  private void waitTillActive() {
    boolean sliceActive = false;

    while (!sliceActive) {
      thisSlice.refresh();
      sliceActive = true;

      System.out.println("Slice: " + thisSlice.getAllResources());
      for (String c : thisSlice.getComputeNodes()) {
        System.out.println("Resource: " + c + ", state: " + thisSlice.getState(c));
        if (thisSlice.getState(c) != "Active" || thisSlice.getManagementIP(c) == null)
          sliceActive = false;
      }

      for (String l : thisSlice.getBroadcastLinks()) {
        System.out.println("Resource: " + l + ", state: " + thisSlice.getState(l));
        if (thisSlice.getState(l) != "Active")
          sliceActive = false;
      }

      System.out.println("");
      sleep(10);
    }

    System.out.println("Done");
    for (String n : thisSlice.getComputeNodes()) {
      System.out.println("ComputeNode: " + n + ", Managment IP =  " + thisSlice.getManagementIP(n));
      resourceIPs.put(n, thisSlice.getManagementIP(n));
    }
  }

  private Session makeSshSession(ComputeNode c) throws JSchException {
    String name = c.getName();
    if (!sessions.containsKey(name) ||
      !sessions.get(name).isConnected()) {
      String cnodeIp = resourceIPs.get(name);
      System.out.println("Creating session for " + name + ": " + cnodeIp);

      JSch jsch = new JSch();
      jsch.addIdentity(sshKey);
      Session session = jsch.getSession(SliceProperties.userName, cnodeIp, 22);

      Properties config = new Properties();
      config.put("StrictHostKeyChecking", "no");
      session.setConfig(config);

      session.connect();
      sessions.put(name, session);
      System.out.println("Session up!");
      return session;
    } else {
      return sessions.get(name);
    }
  }
}
