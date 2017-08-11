defenv VersionNo() :- "45".

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defcon routingPolicy() :-
  {
    route(?Dst,?Path) :-
      neighbor(?Dst),
      ?Path = ?Dst.

    route(?Dst,?Path):-
      neighbor(?Nb),
      ?Nb: advertiseRoute(?Dst,?Path,$Self),
      acceptRoute(?Nb,?Dst).

    label("routing policy($VersionNo)").
  }.

defpost postRoutingPolicy() :- [routingPolicy()].

defcon advertisePolicy() :-
  {
    advertiseRoute(?Dst,?Path,?Target):-
      carryTraffic(?Target,?Dst),
      neighbor(?Target),
      route(?Dst,?IPath),
      ?Path = "$Self:?IPath".

    label("advertise policy($VersionNo)").
  }.

defpost postAdvertisePolicy() :- [advertisePolicy()].

defcon addNeighbor(?Nb) :-
  ?AdvertisePolicyofN := label(?Nb,"advertise policy($VersionNo)"),
  ?NeighborofN := label(?Nb,"add neighbor($VersionNo)"),
  ?CarryTrafficofN := label(?Nb,"carry traffic($VersionNo)"),
  ?AcceptRouteofN := label(?Nb,"accept route($VersionNo)"),
  ?RoutingPolicyofN := label(?Nb,"routing policy($VersionNo)"),
  {
    link($AdvertisePolicyofN).
    link($NeighborofN).
    link($RoutingPolicyofN).
    link($AcceptRouteofN).
    link($CarryTrafficofN).
    neighbor($Nb).
    label("add neighbor($VersionNo)").
  }.

defpost postNeighbor(?Nb):- [addNeighbor(?Nb)].

defcon makeNeighbor() :-
  spec('Construct a set for a group'),
  {
    label("add neighbor($VersionNo)").
  }.

defpost postInitNeighbor() :- [makeNeighbor()].


defcon carryTrafficPolicy(?Src, ?Dst) :-
  {
    carryTraffic($Src, $Dst).
    label("advertise policy($VersionNo)").
  }.

defpost postCarryTraffic(?Src,?Dst):- [carryTrafficPolicy(?Src,?Dst)]. 

defcon makeCarryTraffic():-
  {
    label("carry traffic($VersionNo)").
  }.

defpost postInitCarryTraffic() :- [makeCarryTraffic()].

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

defcon linkPath():-
  {
     linkpath(?Path)

defpost postInitAcceptRoute() :- [makeAcceptRoute()].

defguard verifyRoute(?Dst,?Path) :-
  ?RoutingPolicyRef := label("routing policy($VersionNo)"),
  ?AdvertisePolicy := label("advertise policy($VersionNo)"),
  ?Neighbor := label("add neighbor($VersionNo)"),
  ?AcceptRoute := label("accept route($VersionNo)"),
  ?CarryTraffic := label("carry traffic($VersionNo)"),
  {
    link($Neighbor).
    link($RoutingPolicyRef).
    link($AdvertisePolicy).
    link($AcceptRoute).
    link($CarryTraffic).
    route($Dst,$Path)?
  }.

defguard queryPath(?Dst) :-
  ?RoutingPolicyRef := label("routing policy($VersionNo)"),
  ?AdvertisePolicy := label("advertise policy($VersionNo)"),
  ?Neighbor := label("add neighbor($VersionNo)"),
  ?AcceptRoute := label("accept route($VersionNo)"),
  ?CarryTraffic := label("carry traffic($VersionNo)"),
  {
    link($Neighbor).
    link($RoutingPolicyRef).
    link($AdvertisePolicy).
    link($AcceptRoute).
    link($CarryTraffic).
    route($Dst,?Path)?
  }.

