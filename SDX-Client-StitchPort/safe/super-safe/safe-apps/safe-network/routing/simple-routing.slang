defenv SetVersion() :- "set7".
defenv PolicyVersion() :- "policy1".
defenv RpkiRoot() :- "vOgEXMLrVvDg5YredhSnDn-OIBTRiBVdHoBj8LK2HQs".

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("===identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defcon makeMySubjectSet():-
  spec('Construct an subject set for advertised routes'),
  {
    label("subject($Self) ($PolicyVersion)").
  }.

defpost postSubjectSet():- [makeMySubjectSet()].

defcon routingPolicy() :-
  {
    route(?DstIP,?Path) :-
      authorizedAdvertise(?Dst,?DstIPSet,?Path,$Self),
      ?DstIP <: ?DstIPSet.

    authorizedAdvertise(?Speaker,?DstIP,?Path,?AS):-
      ?Speaker: advertise(?DstIP,?Path,?AS),
      ownPrefix(?Speaker,?DstIPSet),
      ?DstIP <: ?DstIPSet.

    authorizedAdvertise(?Speaker,?DstIP,?Path,?AS):-
      eq([?Head|?Tail],?Path),
      ?Speaker:advertise(?DstIP,?Path,?AS),
      authorizedAdvertise(?P,?DstIPSet,?Tail,?Speaker),
      ?DstIP <: ?DstIPSet.

    label("routing policy($PolicyVersion)").
  }.

defpost postRoutingPolicy() :- [routingPolicy()].

defcon ownPrefixPolicy():-
  {
    ownPrefix(?Prcpl,?Prefix):-
      $RpkiRoot: allocate(?Prcpl,?Prefix).

    ownPrefix(?Prcpl,?Prefix):-
      ?UpStream: allocate(?Prcpl,?Prefix),
      ownPrefix(?UpStream,?PrefixSet),
      ?Prefix <: ?PrefixSet.
    label("own prefix policy($PolicyVersion)").
  }.

defpost postOwnPrefixPolicy() :- [ownPrefixPolicy()].


defcon advertiseRoute(?DstIP,?Path,?Target,?Src,?Len):-
  ?PathToken := label("Path/?Src/?DstIP/?Len ($SetVersion)"),
  {
    link($PathToken).
    advertise($DstIP,$Path,$Target).
    label("Advertise/$Target/$DstIP/$Len ($SetVersion)").
  }.

defpost postAdvertise(?DstIP,?Path,?Target,?Src,?Len):- [advertiseRoute(?DstIP,?Path,?Target,?Src,?Len)].

defcon startRoute(?DstIP,?Path,?Target,?Len):-
  ?Token := label("Token for IP allocation/?DstIP($SetVersion)"),
  {
    link($Token).
    advertise($DstIP,$Path,$Target).
    label("Advertise/$Target/$DstIP/$Len ($SetVersion)").
  }.

defpost postInitRoute(?DstIP,?Path,?Target,?Len) :- [startRoute(?DstIP,?Path,?Target,?Len)].

defcon linkRoute(?Token,?DstIP,?Src,?Len):-
  spec("link a token for an advertised path"),
  {
    link($Token).
    label("Path/$Src/$DstIP/$Len ($SetVersion)").
  }.

defcon addTokenPerIP(?Token,?DstIP):-
  {
    link($Token).
    label("Token for IP/$DstIP ($SetVersion)").
  }.


defpost postPathToken(?Token,?DstIP,?Src,?Len) :- [linkRoute(?Token,?DstIP,?Src,?Len), addTokenPerIP(?Token,?DstIP)].

//Could creat a seperate Set for each principal

defcon ipAllocate(?Prcpl,?Prefix,?DlgPrefix) :-
  ?IPSetToken := label("Token for IP allocation/?DlgPrefix($SetVersion)"),
  {
    link($IPSetToken).
    allocate($Prcpl,$Prefix).
    label("IP allocation/$Prcpl($SetVersion)").
  }.

defpost postIPAllocate(?Prcpl,?Prefix,?DlgPrefix) :-[ipAllocate(?Prcpl,?Prefix,?DlgPrefix)].

defcon addDlgToken(?Token,?Prefix):-
  spec("link a token for allocated IP range"),
  {
    link($Token).
    label("Token for IP allocation/$Prefix($SetVersion)").
  }.

defpost postDlgToken(?Token,?Prefix) :- [addDlgToken(?Token,?Prefix)].

defcon makeIPTokenSet(?DlgPrefix) :-
  {
    label("Token for IP allocation/$DlgPrefix($SetVersion)").
  }.

defpost postMakeIPTokenSet(?DlgPrefix):- [makeIPTokenSet(?DlgPrefix)].

defguard verifyOwnPrefix(?AS,?Prefix):-
  ?OwnPrefixPolicy := label("own prefix policy($PolicyVersion)"),
  ?IPDlgToken := label(?AS,"Token for IP allocation/?Prefix($SetVersion)"),
  {
    link($IPDlgToken).
    link($OwnPrefixPolicy).
    ownPrefix($AS,$Prefix)?
  }.

defguard verifyRoute(?DstIP,?Path,?Src,?Len) :-
  ?RoutingPolicyRef := label("routing policy($PolicyVersion)"),
  ?PathToken := label("Path/?Src/?DstIP/?Len ($SetVersion)"),
  ?OwnPrefixPolicy := label("own prefix policy($PolicyVersion)"),
  {
    link($OwnPrefixPolicy).
    link($RoutingPolicyRef).
    link($PathToken).
    route($DstIP,$Path)?
  }.
