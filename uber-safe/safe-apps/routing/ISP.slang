defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/routing/keys/ISP.pem'). *)

defenv SelfNet :- computeIdFromName('Rnj7hCjk8eb10O37qpJGRDlgX6Tc36E_qZeOAF3u1ck', '152.3.136.4/30').

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
    name('').
  }.

defcon addLink2IDSet(?LinkRef) :-
  spec("Add a link to the ISP's ID set"),
  {
    link($LinkRef).
    name('').
  }.

defcon addNeighbor(?Neighbor) :-
  spec("Add a neighbor"),
  {
    neighbor($Neighbor).
    name(''). (* Store this token to the id set of ?Neighbor *)
  }.

defcon carryTraffic(?Src, ?Dst) :- 
  spec("Agree to carry any traffic that is from $Src to $Dst"),
  {
    carryTraffic($Src, $Dst).  offerRoute(, $Dst)
    name('').  (* Store this token to the id set of ?Src *)
  }.

defcon acceptRoute(?NextHop, ?Dst) :-
  spec("Accept a next hop for a destination"),
  {
    acceptRoute($NextHop, $Dst).
    name(''). 
  }.

defcon routeRules() :-
  spec("Rules for inferring a route"),
  {

    routeIP(?DstIP, ?Path) :- routeIP($SelfNet, ?DstIP, ?Path). (* DstIP is an ip address *) 
    routeIP(?Net, ?DstIP, ?Path) :- route(?Net, ?Dst, ?Path), ipPrefix(?DstIP, ?Dst).

    route(?Dst, ?Path) :- route($SelfNet, ?Dst, ?Path).  (* ?Dst is a net *)

    route(?Net, ?Dst, ?Path) :- 
      ?Authority := rootId(?Net), 
      ?Authority: neighbor(?Dst), 
      ?Path := initPath(?Dst).

    route(?Net, ?Dst, ?Path) :-
      ?Authority := rootId(?Net),
      ?Authority: neighbor(?Nb),
      advertise(?Nb, ?Net, ?Dst, ?Path),  (* ?Nb advertises to ?Net *)
      ?Authority: acceptRoute(?Nb, ?Dst).

    route(?Net, ?Dst, ?Path) :-
      ?Authority := rootId(?Net),
      ?Authority: neighbor(?Nb),
      ?Authority: carryTraffic(?Nb, ?Dst),
      route(?Nb, ?Dst, ?P1), 
      ?Path := concat(?Nb, ?P1).

    advertise(?Nb, ?Net, ?Dst, ?Path) :-  (* ?Nb advertises to ?Net *)
      inPath(?Path, ?Net) = false,    (* ?Net isn't on ?Path *)
      ?NbAuthority := rootId(?Nb),
      ?NbAuthority: neighbor(?Net),
      route(?Nb, ?Dst, ?P1),
      ?NbAuthority: carryTraffic(?Net, ?Dst),
      ?Path := concat(?Nb, ?P1).
  }.

defetch fetchCert(?LinkRef) :- [?LinkRef].

defguard queryRoute(?Dst) :-
  spec("Query routes to the Dst"),
  ?CertChainRef := fetchCert($SelfNet),
  {
    import($CertChainRef).
    route($Dst, ?Path)?
  }.
     






(*
    advertise(?Nb, ?Dst, ?Path) :- ?Nb: neighbor($Self), ?Nb: route(?Dst, ?Path), ?Nb: carryTraffic($Self, $Dst).
    route(?Dst, ?Path) :- neighbor(?Dst), ?Path := initPath(?Dst).    
    route(?Dst, ?Path) :- advertise(?Nb, ?Dst, ?Path), acceptRoute(?Nb, ?Dst).
*)

(* We don't need this *)
defcon advertiseRoute(?Dst, ?Path, ?Neighbor) :-
  spec("Advertise a route to a neighbor),
  {
    advertise($Dst, $Path, $Neighbor).    (* have to sent this link to the neighbor *)
  }.



defguard queryRoute(?Dst, ?Path) :-
  spec("Query a route to a destination"),
  {
    
  }.



defcon makeATagIdSet(?Tag) :-
  spec('Construct the identity set for a tag'),
  {
    name("tag/$Tag").
  }.

defcon delegateAccess(?Tag, ?User, ?Delegatable) :-
  spec('Delegate access for a tag to a user'),
  ?TagRef := computeIdFromName($Self, "tag/?Tag"),
  {
    delegateTagAccess($Tag, $User, $Delegatable).
    link($TagRef).
    name("tagAccess/$Tag/$User").     (* this link should be saved in the identity set of ?User *)
  }.

defcon delegateAccessToATag(?Tag, ?AnotherTag) :-
  spec('Delegate access to another tag'), (* Access is allowed if access to that tag is allowed *)
  ?TagRef := computeIdFromName($Self, "tag/?Tag"),
  {
    delegateTagAccessToAnotherTag($Tag, $AnotherTag).
    link($TagRef).
    name("tagAccess/$Tag/$AnotherTag").  (* this link should be saved in the identity set of ?AnotherTag *)
  }.

defcon addALinkToTagIdSet(?LinkRef, ?Tag) :-
  spec("Add a link to a tag's ID set. Invoked after delegation to this tag"),
  (* ?TagIdSetRef := computeIdFromName($Self, "tag/?Tag"), *)
  {
    link($LinkRef).
    name("tag/$Tag").
  }.


defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postATag(?Tag) :- [makeATagIdSet(?Tag)].

defpost postTagAccessDelegation(?Tag, ?User, ?Delegatable) :- [delegateAccess(?Tag, ?User, ?Delegatable)].

defpost postTagAccessDelegationToATag(?Tag, ?AnotherTag) :- [delegateAccessToATag(?Tag, ?AnotherTag)].

defpost postALinkToTagIdSet(?LinkRef, ?Tag) :- [addALinkToTagIdSet(?LinkRef, ?Tag)].
