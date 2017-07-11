defenv VersionNo() :- "saferouting99".
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
      ?DstIP <: ?IPSet,
      authorizedAdvertise(?DstIP,?Path,$Self).

    authorizedAdvertise(?DstIP,?Path,?AS):-
      ?Path: advertise(?DstIP,?Path,?AS),
      ?Path :ownPrefix(?IPSet),
      ?DstIP <: ?IPSet.

    authorizedAdvertise(?DstIP,?Path,?AS):-
      splitHead(?Path,?Head,?Tail),
      ?Head:advertise(?DstIP,?Path,?AS),
      authorizedAdvertise(?DstIP,?Tail,?Head).

    label("routing policy($VersionNo)").
  }.

defpost postRoutingPolicy() :- [routingPolicy()].

defcon ownPrefixPolicy():-
  {
    ownPrefix(?Prefix):-
      $RpkiRoot: allocate($Self,?Prefix).

    ownPrefix(?SubPrefix):-
      ?UpStream: delegatePrefix(?Prcpl,?SubPrefix),
      isInRange(?SubPrefix,?Prefix),
      ?UpStream: ownPrefix(?Prefix).
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
  ?OwnPrefixPolicy:=label("own prefix policy($VersionNo)"),
  ?PathToken := label("Path/?Path ($VersionNo)"),
  {
    link($OwnPrefixPolicy).
    link($PathToken).
    advertise($DstIP,$Path,$Target).
    label("Path/$Target/$Path ($VersionNo)").
  }.

defpost postAdvertise(?DstIP,?Path,?Target):- [advertiseRoute(?DstIP,?Path,?Target)].

defcon linkRoute(?Token,?Path):-
  spec("link a token for an advertised path"),
  {
    link($Token).
    label("Path/$Self/$Path ($VersionNo)").
  }.

defpost addPathToken(?Token,?Path) :- [linkRoute(?Token,?Path)].

defcon ipAllocate(?Prcpl,?Prefix) :-
  {
    allocate($Prcpl,$Prefix).
    label("IP allocation($VersionNo)").
  }.

defpost postIPAllocate(?Prcpl,?Prefix) :-[ipAllocate(?Prcpl,?Prefix)].

defcon upStreamInternetRegistry(?Prcpl):-
  ?InternetRegistry := label("IP allocation($VersionNo)"),
  {
    link($InternetRegistry).
    label("allocated IP prefix($VersionNo)").
  }.

defpost postInternetRegistry(?Prcpl) :- [upStreamInternetRegistry(?Prcpl)].

defguard verifyRoute(?Dst,?Path) :-
  ?RoutingPolicyRef := label("routing policy($VersionNo)"),
  ?AcceptRoute := label("accept route($VersionNo)"),
  ?PathToken := label("Path/$Self/?Path ($VersionNo)"),
  ?RpkiRoot:= label($RpkiRoot,"IP allocation($VersionNo)"),
  {
    link($RpkiRoot).
    link($PathToken).
    link($RoutingPolicyRef).
    link($AcceptRoute).
    route($Dst,$Path)?
  }.
