package sdx.core;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.io.IOUtils;

import org.renci.ahab.libndl.Slice;
import org.renci.ahab.libndl.resources.request.ComputeNode;
import org.renci.ahab.libndl.resources.request.Network;
import org.renci.ahab.libtransport.ISliceTransportAPIv1;
import org.renci.ahab.libtransport.PEMTransportContext;
import org.renci.ahab.libtransport.SSHAccessToken;
import org.renci.ahab.libtransport.SliceAccessContext;
import org.renci.ahab.libtransport.TransportContext;
import org.renci.ahab.libtransport.xmlrpc.XMLRPCProxyFactory;
import org.renci.ahab.libtransport.util.ContextTransportException;
import org.renci.ahab.libtransport.util.SSHAccessTokenFileFactory;
import org.renci.ahab.libtransport.util.TransportException;
import org.renci.ahab.libtransport.util.UtilTransportException;

import org.apache.log4j.Logger;

public abstract class SliceBase {
  private final static Logger logger = Logger.getLogger(SliceBase.class);
  private ISliceTransportAPIv1 sliceProxy;
  private SliceAccessContext<SSHAccessToken> sliceAccess;

  private String pemFile;
  private String sshKey;
  private String controllerUrl;

  private Slice thisSlice;
  private Map<String, String> resourceIPs = new HashMap<>();
  private Map<String, Session> sessions = new HashMap<>();

  public SliceBase(String configPath) throws SliceBaseException {
    try {
      System.out.println("Reading properties...");
      readProperties(configPath);
      System.out.println("Making proxy...");
      loadProxy();
      System.out.println("Setting access context...");
      loadSliceSSHAccess();
    } catch(Exception e) {
      throw new SliceBaseException(e);
    }
  }

  public SliceBase(String configPath, String sliceName) throws SliceBaseException {
    try {
      System.out.println("Reading properties...");
      readProperties(configPath);
      System.out.println("Making proxy...");
      loadProxy();
      System.out.println("Setting access context...");
      loadSliceSSHAccess();
      System.out.println("Accessing slice...");
      thisSlice = Slice.loadManifestFile(sliceProxy, sliceName);
      for(ComputeNode c : thisSlice.getComputeNodes()){
        resourceIPs.put(c.getName(), c.getManagementIP());
      }
    } catch(Exception e) {
      throw new SliceBaseException(e);
    }
  }

  public void createSlice() throws SliceBaseException {
    try {
      System.out.println("Creating slice!!...");
      thisSlice = Slice.create(sliceProxy, sliceAccess, sliceName());
      createSlice(thisSlice);
      // TODO Add the option to ignore this? idk
      try {
        thisSlice.commit();
      } catch (Exception e) {
        e.printStackTrace();
      }
      waitTillActive();
    } catch (Exception e) {
      throw new SliceBaseException(e);
    }
  }

  public ComputeNode retrieveNode(String name) {
    return (ComputeNode) thisSlice.getResourceByName(name);
  }

  public String retrieveIP(String c) {
    return resourceIPs.get(c);
  }

  public String retrieveIP(ComputeNode c) {
    return resourceIPs.get(c.getName());
  }

  public void execTillSuccess(String name, String command) throws SliceBaseException {
    execTillSuccess(retrieveNode(name), command);
  }

  public void execTillSuccess(ComputeNode c, String command) throws SliceBaseException {
    boolean ret;
    do { ret = execOnNode(c, command); } while (!ret);
  }

  public boolean execOnNode(String name, String command) throws SliceBaseException {
    return execOnNode(retrieveNode(name), command);
  }

  public boolean execOnNode(ComputeNode c, String command) throws SliceBaseException {
    return execOnNode(c, command, false) != null;
  }

  public String execOnNode(ComputeNode c, String command, boolean readOut) throws SliceBaseException {
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

      while (channel.getExitStatus() == -1) {}
      if (channel.getExitStatus() != 0) {
        System.out.println("Error exec!");
        String line;
        while ((line = err.readLine()) != null)
          System.out.println(line);
        return null;
      }
    } catch (IOException | JSchException e) {
      throw new SliceBaseException(e);
    }
    channel.disconnect();
    return output;
  }

  public void sftpToNode(String name, String path) throws SliceBaseException {
    sftpToNode(retrieveNode(name), path);
  }

  public void sftpToNode(ComputeNode c, String path) throws SliceBaseException {
    ChannelSftp channel;
    try {
      Session session = makeSshSession(c);
      channel = (ChannelSftp) session.openChannel("sftp");
      channel.connect();

      String[] pathParts = path.split("/");
      String dstPath = "/root/" + pathParts[pathParts.length - 1];
      System.out.println("dst path: " + dstPath);
      channel.put(getClass().getResourceAsStream(path), dstPath);
    } catch (SftpException | JSchException e) {
      throw new SliceBaseException(e);
    }
    channel.disconnect();
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
    pemFile = prop.getProperty("pemFile");
    sshKey = prop.getProperty("sshKey");
    controllerUrl = prop.getProperty("controllerUrl");
  }

  private void loadProxy() throws MalformedURLException,
                                  ContextTransportException,
                                  TransportException {
    sliceProxy =
        (new XMLRPCProxyFactory()).getSliceProxy(new PEMTransportContext("", pemFile, pemFile),
                                                 new URL(controllerUrl));
  }

  private void loadSliceSSHAccess() throws UtilTransportException {
    sliceAccess = new SliceAccessContext<>();
    sliceAccess.addToken("root",
                         (new SSHAccessTokenFileFactory(sshKey + ".pub", false))
                              .getPopulatedToken());

  }

  private void waitTillActive() {
    boolean sliceActive = false;

    while (!sliceActive) {
      thisSlice.refresh();
      sliceActive = true;

      System.out.println("Slice: " + thisSlice.getAllResources());
      for (ComputeNode c : thisSlice.getComputeNodes()) {
        System.out.println("Resource: " + c.getName() + ", state: "  + c.getState());
        if (c.getState() != "Active" || c.getManagementIP() == null)
          sliceActive = false;
      }

      for (Network l : thisSlice.getBroadcastLinks()) {
        System.out.println("Resource: " + l.getName() + ", state: "  + l.getState());
        if (l.getState() != "Active")
          sliceActive = false;
      }

      System.out.println("");
      sleep(10);
    }

    System.out.println("Done");
    for (ComputeNode n : thisSlice.getComputeNodes()) {
      System.out.println("ComputeNode: " + n.getName() + ", Managment IP =  " + n.getManagementIP());
      resourceIPs.put(n.getName(), n.getManagementIP());
    }
  }

  private void sleep(int ms) {
    try {
      Thread.sleep(ms*1000);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private Session makeSshSession(ComputeNode c) throws JSchException {
    String name = c.getName();
    if (!sessions.containsKey(name) ||
        !sessions.get(name).isConnected()) {
      String cnodeIp = resourceIPs.get(name);
      System.out.println("Creating session for " + name + ": " + cnodeIp);

      JSch jsch=new JSch();
      jsch.addIdentity(sshKey);
      Session session=jsch.getSession("root", cnodeIp, 22);

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
