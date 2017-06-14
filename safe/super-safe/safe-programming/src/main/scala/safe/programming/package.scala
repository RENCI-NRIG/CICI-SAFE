package safe 

import safe.safelang.SafelangManager

package object programming {

  /** 
   * Local slang call types 
   * These are abstraction of signatures of actual slang calls
   */
  val CALL = 0               // Remote calls to a single server and a single principal 
  val CALLSERVER = 1         // Single-principal remote calls at a specific server (p is installed)
  val DELEGATE = 10          // Delegations involving two server-hosted principals 
  val DELEGATE_QUERY = 20    // Query a diff principal at a diff server after delegation

  val callTypeTable = Map[Int, String](CALL          -> "simpleRemoteCall",
                                       CALLSERVER     -> "remoteCallToServer",
                                       DELEGATE       -> "simpleDelegate",
                                       DELEGATE_QUERY -> "delegateThenQuery")

  /** Description of local slang calls */
  type SimpleDelegateTuple= Tuple5[String, PrincipalStub, PrincipalStub, Seq[String], Seq[String]]
  type DelegateThenQueryTuple = Tuple7[String, PrincipalStub, PrincipalStub, PrincipalStub, String, 
                                      Seq[String], Seq[String]]
  type SimpleRemoteCallTuple = Tuple4[String, PrincipalStub, String, Seq[String]]
  type RemoteCallToServerTuple = Tuple5[String, PrincipalStub, String, String, Seq[String]]

  val slangManager = SafelangManager()

}
