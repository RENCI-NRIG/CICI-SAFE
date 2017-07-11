defcall publishMembership(?JVM, ?Principal, ?Group, ?User, ?Delegatable) :-
{
  postTagAccessDelegation($JVM, $Principal, $Group, $User, $Delegatable).
}.

defcall publishToken(?JVM, ?Principal, ?LinkRef) :-
{
  postALinkToIdSet($JVM, $Principal, $LinkRef).
}.

delegateMembership(?JVM1, ?Principal1, ?Group, ?JVM2, ?Principal2, ?Delegatable) :- 
  ?Token := publishMembership(?JVM1, ?Principal1, ?Group, ?Principal2, ?Delegatable),
  publishToken(?JVM2, ?Principal2, ?Token). 


(**
 * An example query invoked by the scala benchmarkHarness
 * DelegateMembership("152.3.145.10:7777", u'jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3ow', 
 *                    "153.3.145.11:7777", u'SakSG4JGhi7Cejx3D9rl06UOb4hk6jAQRJs68DP57RE',
 *                    true)
 *)
