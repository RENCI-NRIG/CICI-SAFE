defreq publishMembership(?JVM, ?Principal, ?User, ?Delegatable) :-
{
  postMembership($JVM, $Principal, $User, $Delegatable).  
}.

defreq publishToken(?JVM, ?Principal, ?LinkRef) :-
{
  postLinktoIDSet($JVM, $Principal, $LinkRef).
}.

delegateMembership(?JVM1, ?Principal1, ?JVM2, ?Principal2, ?Delegatable) :- 
  ?Token := publishMembership(?JVM1, ?Principal1, ?Principal2, ?Delegatable),
  publishToken(?JVM2, ?Principal2, ?Token). 


(**
 * An example query invoked by the scala benchmarkHarness
 * DelegateMembership("152.3.145.10:7777", u'jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3ow', 
 *                    "153.3.145.11:7777", u'SakSG4JGhi7Cejx3D9rl06UOb4hk6jAQRJs68DP57RE',
 *                    true)
 *)
