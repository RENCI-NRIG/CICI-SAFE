defenv VersionNo() :- "saferouting155".
defenv RpkiRoot() :- "T6FIXxTGm_VEDXYUg8IZhWdOygjpBE0DsQ5C6wgAzmU".

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("===identity set for $CN($VersionNo)").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defcon makeMySubjectSet():-
  spec('Construct an subject set for advertised routes'),
  {
    label("subject($Self)").
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

    label("routing policy($VersionNo)").
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
    label("own prefix policy($VersionNo)").
  }.

defpost postOwnPrefixPolicy() :- [ownPrefixPolicy()].


defcon advertiseRoute(?DstIP,?Path,?Target,?OldPath):-
  ?PathToken := label("Path/?OldPath ($VersionNo)"),
  {
    link($PathToken).
    advertise($DstIP,$Path,$Target).
    label("Path/$Target/$Path ($VersionNo)").
  }.

defpost postAdvertise(?DstIP,?Path,?Target,?OldPath):- [advertiseRoute(?DstIP,?Path,?Target,?OldPath)].

defcon startRoute(?DstIP,?Path,?Target):-
  ?Token := label("Token for IP allocation($VersionNo)"),
  {
    link($Token).
    advertise($DstIP,$Path,$Target).
    label("Path/$Target/$Path ($VersionNo)").
  }.

defpost postInitRoute(?DstIP,?Path,?Target) :- [startRoute(?DstIP,?Path,?Target)].

defcon linkRoute(?Token,?Path):-
  spec("link a token for an advertised path"),
  {
    link($Token).
    label("Path/$Path ($VersionNo)").
  }.

defcon addPathTokentoSubjectSet(?Token):-
  {
    label("subject($Self)").
  }.

defpost postPathToken(?Token,?Path) :- [linkRoute(?Token,?Path), addPathTokentoSubjectSet(?Token)].

//Could creat a seperate Set for each principal

defcon ipAllocate(?Prcpl,?Prefix) :-
  {
    allocate($Prcpl,$Prefix).
    label("IP allocation($VersionNo)").
  }.

defpost postIPAllocate(?Prcpl,?Prefix) :-[ipAllocate(?Prcpl,?Prefix)].

defcon addDlgToken(?Token):-
  spec("link a token for allocated IP range"),
  {
    link($Token).
    label("Token for IP allocation($VersionNo)").
  }.

defpost postDlgToken(?Token) :- [addDlgToken(?Token)].

defcon makeIPTokenSet() :-
  {
    label("Token for IP allocation($VersionNo)").
  }.

defpost postMakeIPTokenSet():- [makeIPTokenSet()].

defguard verifyRoute(?Dst,?Path) :-
  ?RoutingPolicyRef := label("routing policy($VersionNo)"),
  ?PathToken := label("Path/?Path ($VersionNo)"),
  ?OwnPrefixPolicy := label("own prefix policy($VersionNo)"),
  {
    link($OwnPrefixPolicy).
    link($PathToken).
    link($RoutingPolicyRef).
    route($Dst,$Path)?
  }.

defguard queryRoute(?DstIP):-
  ?RoutingPolicyRef := label("routing policy($VersionNo)"),
  ?PathToken := label("subject($Self) ($VersionNo)"),
  ?OwnPrefixPolicy := label("own prefix policy($VersionNo)"),
  {
    link($OwnPrefixPolicy).
    link($PathToken).
    link($RoutingPolicyRef).
    route($Dst,?Path)?
  }.
