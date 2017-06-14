/* -*-mode:java; c-basic-offset:2; indent-tabs-mode:nil -*- */
package safe.sdx;
import com.jcraft.jsch.*;
import java.awt.*;
import javax.swing.*;
import java.io.*;
import java.util.Properties;

public class Exec{
  public static void exec(String cmd){
    System.out.println(cmd);
    try{
      Runtime run = Runtime. getRuntime();
      Process pr = run.exec(cmd);
      pr. waitFor();
      BufferedReader buf = new BufferedReader(new InputStreamReader(pr. getInputStream()));
      String line = "";
      while ((line=buf. readLine())!=null) {
        System.out.println(line);
      }
    }catch (Exception e){
      System.out.println("Exeption while setting up link"+e);

    }
  }
  public static String sshExec(String user, String host,String command, String privkey){
    String result="";
    try{
      JSch jsch=new JSch();  
      jsch.addIdentity(privkey);
      Session session=jsch.getSession(user, host, 22);
      Properties config = new Properties(); 
      config.put("StrictHostKeyChecking", "no");
      session.setConfig(config);
      session.connect();
      Channel channel=session.openChannel("exec");
      ((ChannelExec)channel).setCommand(command);
      channel.setInputStream(null);
      ((ChannelExec)channel).setErrStream(System.err);
      InputStream in=channel.getInputStream();
      channel.connect();
      byte[] tmp=new byte[1024];
      while(true){
        while(in.available()>0){
          int i=in.read(tmp, 0, 1024);
          if(i<0)break;
          result+=new String(tmp,0,i)+"\n";
          System.out.print(new String(tmp, 0, i));
        }
        if(channel.isClosed()){
          //get status returns int;
          System.out.println("exit-status: "+channel.getExitStatus());
          if (channel.getExitStatus()!=0){
            result="error:"+String.valueOf(channel.getExitStatus());
          }
          break;
        }
        try{Thread.sleep(1000);}catch(Exception ee){}
      }
      channel.disconnect();
      session.disconnect();
    }
    catch(Exception e){
      System.out.println(e);
    }
    return result;
  }

  public static class MyUserInfo implements UserInfo, UIKeyboardInteractive{
    public String getPassword(){ return passwd; }
    public boolean promptYesNo(String str){
      Object[] options={ "yes", "no" };
      int foo=JOptionPane.showOptionDialog(null, 
             str,
             "Warning", 
             JOptionPane.DEFAULT_OPTION, 
             JOptionPane.WARNING_MESSAGE,
             null, options, options[0]);
       return foo==0;
    }
  
    String passwd;
    JTextField passwordField=(JTextField)new JPasswordField(20);

    public String getPassphrase(){ return null; }
    public boolean promptPassphrase(String message){ return true; }
    public boolean promptPassword(String message){
      Object[] ob={passwordField}; 
      int result=
        JOptionPane.showConfirmDialog(null, ob, message,
                                      JOptionPane.OK_CANCEL_OPTION);
      if(result==JOptionPane.OK_OPTION){
        passwd=passwordField.getText();
        return true;
      }
      else{ 
        return false; 
      }
    }
    public void showMessage(String message){
      JOptionPane.showMessageDialog(null, message);
    }
    final GridBagConstraints gbc = 
      new GridBagConstraints(0,0,1,1,1,1,
                             GridBagConstraints.NORTHWEST,
                             GridBagConstraints.NONE,
                             new Insets(0,0,0,0),0,0);
    private Container panel;
    public String[] promptKeyboardInteractive(String destination,
                                              String name,
                                              String instruction,
                                              String[] prompt,
                                              boolean[] echo){
      panel = new JPanel();
      panel.setLayout(new GridBagLayout());

      gbc.weightx = 1.0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.gridx = 0;
      panel.add(new JLabel(instruction), gbc);
      gbc.gridy++;

      gbc.gridwidth = GridBagConstraints.RELATIVE;

      JTextField[] texts=new JTextField[prompt.length];
      for(int i=0; i<prompt.length; i++){
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 0;
        gbc.weightx = 1;
        panel.add(new JLabel(prompt[i]),gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 1;
        if(echo[i]){
          texts[i]=new JTextField(20);
        }
        else{
          texts[i]=new JPasswordField(20);
        }
        panel.add(texts[i], gbc);
        gbc.gridy++;
      }

      if(JOptionPane.showConfirmDialog(null, panel, 
                                       destination+": "+name,
                                       JOptionPane.OK_CANCEL_OPTION,
                                       JOptionPane.QUESTION_MESSAGE)
         ==JOptionPane.OK_OPTION){
        String[] response=new String[prompt.length];
        for(int i=0; i<prompt.length; i++){
          response[i]=texts[i].getText();
        }
	return response;
      }
      else{
        return null;  // cancel
      }
    }
  }
}
