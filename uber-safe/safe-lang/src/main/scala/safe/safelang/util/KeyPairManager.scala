package safe.safelang
package util

import model.Principal
import safe.safelog.{SetId, StrLit}

import java.io.File
import java.io.PrintWriter
import scala.collection.mutable.{Map => MutableMap}
import com.typesafe.scalalogging.LazyLogging

/* Crypto libraries */
import java.security.spec._
import javax.crypto._
import javax.crypto.spec._

import safe.safelang.model.Identity

trait KeyPairManager extends LazyLogging {

  /** 
   * Helpers for key management in mulit-principal programing
   */
  def filepathsOfDir(dirStr: String): Seq[String] = {
    val dir = new File(dirStr)
    if(dir.exists && dir.isDirectory) {
      dir.listFiles.filter(_.isFile).toSeq.map(_.toString)
    } else {
      Seq[String]()
    }
  }

  def filenamesOfDir(dirStr: String): Seq[String] = {
    val dir = new File(dirStr)
    if(dir.exists && dir.isDirectory) {
      dir.listFiles.filter(_.isFile).toSeq.map(_.getName)
    } else {
      Seq[String]()
    }
  }

  def filesOfDir(dirStr: String): Seq[File] = {
    val dir = new File(dirStr)
    if(dir.exists && dir.isDirectory) {
      dir.listFiles.filter(_.isFile).toSeq
    } else {
      Seq[File]()
    }
  }

  /**
   * We may store principal ids (token of the public keys) to
   * a file after loading them.
   *
   * Get the absolute path to the file of principal pids 
   */
  def pathToPidsFile(dirStr: String, filename: String): String = {
    dirStr + "keyhashes/" + filename
  }

  def loadKeyPairs(): MutableMap[String, Principal] = {
    loadKeyPairs(Config.config.keyPairDir)
  }

  def loadKeyPairs(dir: String, nameToID: MutableMap[String, String] = MutableMap[String, String]()): MutableMap[String, Principal] = {
    val serverPrincipalSet = MutableMap[String, Principal]()   
    if(dir.isEmpty) return serverPrincipalSet
    val filepaths = filepathsOfDir(dir)
    println(s"[KeyPairManager] load key pairs from ${dir}")
    println(s"[KeyPairManager] number of key pairs: ${filepaths.size}")
    logger.info(s"All principals:")    
    var count = 0
    for(fname <- filepaths) {
      //println(s"[KeyPairManager loadKeyPairs] fname=${fname}");
      val p = Principal(pemFile = fname)
      val pid = p.pid
      serverPrincipalSet.put(pid, p)
      val pname = fname.substring(fname.lastIndexOf('/')+1, fname.lastIndexOf('.'))
      nameToID.put(pname, pid)
      logger.info(s"${pname}: ${pid}")
      //println(s"[KeyPairManager loadKeyPairs] fname=${fname};    p.subject.id.toString=${p.subject.id.toString};    p.scid.toString=${p.scid.toString};   p.scid.speakerId.toString=${p.scid.speakerId.toString};     p.scid.name=${p.scid.name};     p=${p}")
      count += 1
      if(count % 10000 == 0)
        println(s"[KeyPairManager]  count = ${count}")
    }
    serverPrincipalSet
  }
 
  /**
   * TODO: move part of utilities in safe.safelang.model._ to here, 
   * such as:
   * Principal.keyPairFromFile(pemFile: String)
   * Identity.hash()
   */

  /* Load access keys from a local directory */
  def loadAccessKeys(dir: String): MutableMap[String, SecretKeySpec] = {
    val accessKeySet = MutableMap[String, SecretKeySpec]()
    val kdir = new File(dir) 
    if(kdir.exists && kdir.isDirectory) {
      val kfiles = kdir.listFiles.filter(_.isFile).toSeq
      for(kf <- kfiles) {
        val path = kf.toString
        val name = kf.getName
        val keySpec = loadAccessKey(path)
        accessKeySet.put(name, keySpec) 
      }
    }
    accessKeySet
  }

  def loadAccessKey(path: String): SecretKeySpec = {
    val base64EncodedString: String = scala.io.Source.fromFile(path).mkString
    val aesKey: Array[Byte] = Identity.base64Decode(base64EncodedString)
    val aesKeySpec = new SecretKeySpec(aesKey, "AES")
    aesKeySpec
  }

  def createAESKey(keyLen: Int): SecretKey = {
    val kgen: KeyGenerator = KeyGenerator.getInstance("AES")
    kgen.init(keyLen)
    val key: SecretKey = kgen.generateKey()
    key
  }

  def saveAESKey(key: SecretKey, path: String): String = {
    val aesKey: Array[Byte] = key.getEncoded()
    val keyAsString: String = Identity.base64EncodeURLSafe(aesKey)
    val pw = new PrintWriter(new File(path))
    pw.write(keyAsString)
    pw.close()
    keyAsString
  }

  /* AES encryption */
  def encrypt(data: Array[Byte], accessKeySpec: SecretKeySpec): Array[Byte] = {
    val aesCipher = Cipher.getInstance("AES")
    aesCipher.init(Cipher.ENCRYPT_MODE, accessKeySpec)
    val cipherdata = aesCipher.doFinal(data) 
    cipherdata
  }

  /* AES decryption */
  def decrypt(cipherdata: Array[Byte], accessKeySpec: SecretKeySpec): Array[Byte] = {
    val aesCipher = Cipher.getInstance("AES")
    aesCipher.init(Cipher.DECRYPT_MODE, accessKeySpec)
    val plaindata = aesCipher.doFinal(cipherdata) 
    plaindata
  }
 
}
