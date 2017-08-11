package safe.sdx.rpki;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import safe.sdx.utils.SafePost;

import java.util.regex.Matcher;

public class RpkiServer{
  public RpkiServer(){}
  public static String safeserver;
  private static HashMap<String, String> principals=new HashMap<String,String>();

  public static void main(String[]args){
    Pattern pattern = Pattern.compile("\"message\":\"(.*?)\"");
    String token=null;
    readPrincipals("data/principals");
    //Init subject sets and id set
    safeserver="152.3.136.36:7777";
    Set<String> keyset=principals.keySet();
    for(String key:keyset){
      postIdSet(principals.get(key),key);
      postSubjectSet(principals.get(key));
      postPolicy(principals.get(key),"postOwnPrefixPolicy");
      postPolicy(principals.get(key),"postRoutingPolicy");
      postPolicy(principals.get(key),"postStitchPolicy");
    }
    //my project authority
    String pa=principals.get("pa");
    endorsePA(principals.get("client1"),pa);
    token=endorsePM(pa,principals.get("client1"));
    token=SafePost.getToken(token);
    //token=token.substring(2,token.length()-2);
    postEndorseToken(principals.get("client1"),token);
    endorsePA(principals.get("client2"),pa);
    token=endorsePM(pa,principals.get("client2"));
    //token=token.substring(2,token.length()-2);
    token=SafePost.getToken(token);
    postEndorseToken(principals.get("client2"),token);
    endorsePA(principals.get("as1"),pa);
    token=endorsePM(pa,principals.get("as1"));
    //token=token.substring(2,token.length()-2);
    token=SafePost.getToken(token);
    postEndorseToken(principals.get("as1"),token);
    endorsePA(principals.get("as2"),pa);
    token=endorsePM(pa,principals.get("as2"));
    //token=token.substring(2,token.length()-2);
    token=SafePost.getToken(token);
    postEndorseToken(principals.get("as2"),token);
    //update subject set

    String rpki=principals.get("rpkiroot");
    makeIPTokenSet(rpki,"ipv4\\\"192.168.1.1/16\\\"");
    //IP allocation
    postIPAllocate(rpki,principals.get("client1"),"ipv4\\\"192.168.19.2/24\\\"","ipv4\\\"192.168.1.1/16\\\"");
    postIPAllocate(rpki,principals.get("client2"),"ipv4\\\"192.168.36.2/24\\\"","ipv4\\\"192.168.1.1/16\\\"");
  }

  public static void readPrincipals(String filepath){
    try{
      java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(filepath));
      String input = br.readLine();  
      while(input!=null){
        String[] items=input.replace("\n","").split(":");
        principals.put(items[0],items[1]);
        input = br.readLine();
      }
		}catch (java.io.IOException e) {
				System.out.println(e);
		}  
  }

  public static boolean endorsePA(String pm,String pa){
    String[] othervalues=new String[1];
    othervalues[0]=pa;
    String message=SafePost.postSafeStatements(safeserver,"postEndorsePA",pm,othervalues);
    if(message.contains("fail")){
      return false;
    }
    else
      return true;
  }

  public static String endorsePM(String pa,String pm){
    String[] othervalues=new String[1];
    othervalues[0]=pm;
    String message=SafePost.postSafeStatements(safeserver,"postEndorsePM",pa,othervalues);
    if(message.contains("fail")){
      return null;
    }
    else
      return message;
  }

  public static boolean postEndorseToken(String prcpl, String token){
    String[] othervalues=new String[1];
    othervalues[0]=token;
    String message=SafePost.postSafeStatements(safeserver,"updateSubjectSet",prcpl,othervalues);
    if(message.contains("fail")){
      return false;
    }
    else
      return true;
  }

  public static boolean postIPAllocate(String keyhash,String prcpl, String prefix, String dlgprefix){
    String[] othervalues=new String[3];
    othervalues[0]=prcpl;
    othervalues[1]=prefix;
    othervalues[2]=dlgprefix;
    String message=SafePost.postSafeStatements(safeserver,"postIPAllocate",keyhash,othervalues);
    if(message.contains("fail")){
      return false;
    }
    else{
      //"message": "['TelYsrZgj4UADSM0eo1TPkEHNjioVK9qmAwtwehD3XU']"
      //parse for token
      String token=message.substring(2,message.length()-2);
      othervalues=new String[2];
      othervalues[0]=token;
      othervalues[1]=prefix;
      message=SafePost.postSafeStatements(safeserver,"postDlgToken",prcpl,othervalues);
      if(message==null||message.contains("fail")){
        return false;
      }
      else
        return true;
    }
  }

  public static boolean makeIPTokenSet(String keyhash,String prefix){
    String[] othervalues=new String[1];
    othervalues[0]=prefix;
    String message=SafePost.postSafeStatements(safeserver, "postMakeIPTokenSet", keyhash, othervalues);
    if(message==null||message.contains("fail")){
      return false;
    }
    else{
      return true;
    }
  }

  public static boolean postIdSet(String keyhash, String cn){
    String[] othervalues=new String[1];
    othervalues[0]=cn;
    String message=SafePost.postSafeStatements(safeserver, "postIdSet", keyhash, othervalues);
    if(message==null||message.contains("fail")){
      return false;
    }
    else{
      return true;
    }
  }
  public static boolean postSubjectSet(String keyhash){
    String[] othervalues=new String[0];
    String message=SafePost.postSafeStatements(safeserver, "postSubjectSet", keyhash, othervalues);
    if(message==null||message.contains("fail")){
      return false;
    }
    else{
      return true;
    }
  }

  public static boolean postPolicy(String keyhash,String op){
    String[] othervalues={};
    String message=SafePost.postSafeStatements(safeserver, op, keyhash, othervalues);
    if(message==null||message.contains("fail")){
      return false;
    }
    else{
      return true;
    }
  }
}
