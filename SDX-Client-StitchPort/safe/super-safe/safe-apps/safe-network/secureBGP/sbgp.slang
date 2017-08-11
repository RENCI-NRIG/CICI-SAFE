defenv VersionNo :- "1"
defcon makeMyIdenttitySet(?CN) :-
  spec("Construct the identity set of self"),
  {
    principal($SelfKey):- spec("identity set for $CN").
  }.

defpost postIdSet(?CN):-[makeMyIdentitySet(?CN)].

defcon routingPolicy() :-
  {
    route(?Dst,?Path) :-
      neighbor(?Dst),
      authorizedAdvertise(?Dst,?Path,$Self).

    authorizedAdvertise(?Dst,?Path,?AS):-
      eq([?Head|?Tail],?Path),
      eq(?Tail,[]),
      ?Head: advertise(?Dst,?Path,?AS),
      ownPrefix(?Head,?Dst).

    authorizedAdvertise(?Dst,?Path,?AS):-
      eq([?Head|?Tail],?Path),
      ?Head: advertise(?Dst,?Tail,?AS),
      authorizedAdvertise(?Dst,?Tail,?Head).
        label("routing policy($VersionNo)").
  }

defpost postRoutingPolicy():- [routingPolicy()].


(* local policy*)
defcon advertisePolicy():-
  {
    advertise(?Dst,?Path,?Nb):-
      carryTraffic(?Nb,Dst),
      neighbor(?Nb),
      route(?Dst,IPath),
      eq([$Self|IPath],Path).
    label("advertise policy($VersionNo)").
  }.

defpost postAdvertisePolicy() :- [advertisePolicy()].

defcon ownPrefixPolicy():-
  {
    ownPrefix(?Prcpl,?Prefix):-
      "trustroot": allocate(?Prcpl,?Prefix).

    ownPrefix(?Prcpl,?SubPrefix):-
      ?UpStream: delegatePrefix(?Prcpl,?SubPrefix),
      isInRange(?SubPrefix,?Prefix),
      ?UpStream: ownPrefix(?Prefix.
    label("own prefix policy($VersionNo)").
  }.

defcon postOwnPrefixPolicy() :- [ownPrefixPolicy()].

defcon addNeighbor(?Nb) :-
  {
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
    label("carry traffic($VersionNo)").
  }.

defpost postCarryTraffic(?Src,?Dst):- [carryTrafficPolicy(?Src,?Dst)]. 

defcon makeCarryTraffic() :-
  {
    label("carry traffic($VersionNo)").
  }.

defcon postInitCarryTraffic() :- [makeCarryTraffic()].

defcon advertiseRoute(?Dst,?Path,?TargetAS):-
  {
    advertise($Dst,$Path,$TargetAS).
    label("advertise($VersionNo)").
  }.

defpost postAdvertise(?Dst,?Path,?TargetAS):- [advertiseRoute(?Dst,?Path,?TargetAS)].

defcon makeAdvertise():-
  {
    label("advertise($VersionNo)").
  }.

defpost 

defcon addDelegatePrefix(?Prcpl,?Prefix):-
  {
    delegatePrefix(?Prcpl,?Prefix).
    label("prefix delegation($VersionNo)").
  }

defpost postDelegation(?Prcpl,?Prefix) :- [addDelegatePrefix(?Prcpl,?Prefix)].


