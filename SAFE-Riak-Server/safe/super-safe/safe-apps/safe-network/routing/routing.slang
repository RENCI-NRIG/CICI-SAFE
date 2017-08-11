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

    label("routing policy39").
  }.

defpost postRoutingPolicy() :- [routingPolicy()].

defcon advertisePolicy() :-
  {
    advertiseRoute(?Dst,?Path,?Target):-
      carryTraffic(?Target,?Dst),
      neighbor(?Target),
      route(?Dst,?IPath),
      ?Path = "$Self/?IPath".

    label("advertise policy39").
  }.

defpost postAdvertisePolicy() :- [advertisePolicy()].

defcon addNeighbor(?Nb) :-
  ?AdvertisePolicyofN := label(?Nb,"advertise policy39"),
  ?NeighborofN := label(?Nb,"add neighbor39"),
  ?CarryTrafficofN := label(?Nb,"carry traffic39"),
  ?AcceptRouteofN := label(?Nb,"accept route39"),
  ?RoutingPolicyofN := label(?Nb,"routing policy39"),
  {
    link($AdvertisePolicyofN).
    link($NeighborofN).
    link($RoutingPolicyofN).
    link($AcceptRouteofN).
    link($CarryTrafficofN).
    neighbor($Nb).
    label("add neighbor39").
  }.

defpost postNeighbor(?Nb):- [addNeighbor(?Nb)].

defcon makeNeighbor() :-
  spec('Construct a set for a group'),
  {
    label("add neighbor39").
  }.

defpost postInitNeighbor() :- [makeNeighbor()].


defcon carryTrafficPolicy(?Src, ?Dst) :-
  {
    carryTraffic($Src, $Dst).
    label("advertise policy39").
  }.

defpost postCarryTraffic(?Src,?Dst):- [carryTrafficPolicy(?Src,?Dst)]. 

defcon makeCarryTraffic():-
  {
    label("carry traffic39").
  }.

defpost postInitCarryTraffic() :- [makeCarryTraffic()].

defcon acceptRoutePolicy(?Nb, ?Dst):-
  {
    acceptRoute($Nb, $Dst).
    label("accept route39").
  }.

defpost postAcceptRoute(?Nb,?Dst) :- [acceptRoutePolicy(?Nb,?Dst)].

defcon makeAcceptRoute():-
  {
    label("accept route39").
  }.

defpost postInitAcceptRoute() :- [makeAcceptRoute()].

defguard VerifyRoute(?Dst,?Path) :-
  ?RoutingPolicyRef := label("routing policy39"),
  ?AdvertisePolicy := label("advertise policy39"),
  ?Neighbor := label("add neighbor39"),
  ?AcceptRoute := label("accept route39"),
  ?CarryTraffic := label("carry traffic39"),
  {
    link($Neighbor).
    link($RoutingPolicyRef).
    link($AdvertisePolicy).
    link($AcceptRoute).
    link($CarryTraffic).
    route($Dst,$Path)?
  }.

defguard queryPath(?Dst) :-
  ?RoutingPolicyRef := label("routing policy39"),
  ?AdvertisePolicy := label("advertise policy39"),
  ?Neighbor := label("add neighbor39"),
  ?AcceptRoute := label("accept route39"),
  ?CarryTraffic := label("carry traffic39"),
  {
    link($Neighbor).
    link($RoutingPolicyRef).
    link($AdvertisePolicy).
    link($AcceptRoute).
    link($CarryTraffic).
    route($Dst,?Path)?
  }.

