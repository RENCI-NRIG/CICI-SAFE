defenv VersionNo() :- "saferouting125".
defenv RpkiRoot() :- "XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU".

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defcon routingPolicy() :-
  {
    route(?DstIP,?Path) :-
      acceptRoute(?Dst,?IPSet),
      authorizedAdvertise(?Dst,?DstIPSet,?Path,$Self),
      ?DstIP <: ?IPSet,
      ?DstIP <: ?DstIPSet.

    authorizedAdvertise(?Speaker,?DstIP,?Path,?AS):-
      ?Speaker: advertise(?DstIP,?Path,?AS),
      ownPrefix(?Speaker,?DstIPSet),
      ?DstIP <: ?DstIPSet.

    authorizedAdvertise(?Speaker,?DstIP,?Path,?AS):-
      splitHead(?Path,?Head,?Tail),
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

defcon acceptRoutePolicy(?Nb, ?Dst):-
  {
    acceptRoute($Nb, $Dst).
    label("accept route($VersionNo)").
  }.

defpost postAcceptRoute(?Nb,?Dst) :- [acceptRoutePolicy(?Nb,?Dst)].

defcon makeAcceptRoute():-
  {
    label("accept route($VersionNo)").
  }.

defpost postInitAcceptRoute() :- [makeAcceptRoute()].

defcon advertiseRoute(?DstIP,?Path,?Target):-
  ?PathToken := label("Path/?Path ($VersionNo)"),
  {
    link($PathToken).
    advertise($DstIP,$Path,$Target).
    label("Path/$Target/$Path ($VersionNo)").
  }.

defpost postAdvertise(?DstIP,?Path,?Target):- [advertiseRoute(?DstIP,?Path,?Target)].

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
    label("Path/$Self/$Path ($VersionNo)").
  }.

defpost postPathToken(?Token,?Path) :- [linkRoute(?Token,?Path)].

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
  ?AcceptRoute := label("accept route($VersionNo)"),
  ?PathToken := label("Path/$Self/?Path ($VersionNo)"),
  ?OwnPrefixPolicy := label("own prefix policy($VersionNo)"),
  {
    link($OwnPrefixPolicy).
    link($PathToken).
    link($RoutingPolicyRef).
    link($AcceptRoute).
    route($Dst,$Path)?
  }.
