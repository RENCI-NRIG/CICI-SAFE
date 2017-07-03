defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/routing/keys/tenant.pem'). *)

defenv SelfNet :- computeIdFromName('Rnj7hCjk8eb10O37qpJGRDlgX6Tc36E_qZeOAF3u1ck', 'vnet/Rnj7hCjk8eb10O37qpJGRDlgX6Tc36E_qZeOAF3u1ck:152.3.136.4/30').

defenv IANA :- u'jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3ow'.  (* The owner of the entire IP space *)
defenv IPUniverse :- 'jSvdyfIYzmWO-d1V_mJqPq1fPkIS8CPkE2TxFfsk3ow:0/0'. (* The entire space *)

defcon makeVNetIDSet(?VNet) :-
  spec('Construct the identity set for a vnet'),
  {
    name("vnet/$VNet").
  }.

defcon addLink2VNetIDSet(?LinkRef, ?VNet) :-
  spec("Add a link to the VNet's ID set"),
  {
    link($LinkRef).
    name("vnet/$VNet").
  }.

defcon addNeighbor(?VNet, ?Neighbor) :-
  spec("Add a neighbor"),
  {
    neighbor($VNet, $Neighbor).
    name("vnet/$VNet").    (* Store this token to the id set of ?Neighbor *)
  }.

defcon carryTraffic(?VNet, ?Src, ?Dst) :- 
  spec("Agree to let $VNet carry traffic from $Src to $Dst"),
  {
    carryTraffic($VNet, $Src, $Dst).
    name("vnet/$VNet").  (* Store this token to the id set of ?Src *)
  }.

defcon acceptRoute(?VNet, ?NextHop, ?Dst) :-
  spec("Accept a next hop for a destination"),
  {
    acceptRoute($VNet, $NextHop, $Dst).
    name("vnet/$VNet"). 
  }.

defcon routingRules() :-
  spec("Rules for inferring a route"),
  {
    routeIP(?DstIP, ?NextHop) :- routeIP($SelfNet, ?DstIP, ?NextHop).   (* DstIP is an ip address *) 
    routeIP(?VNet, ?DstIP, ?NextHop) :- route(?VNet, ?Dst, ?NextHop), contain(?Dst, ?DstIP).

    route(?Dst, ?NextHop) :- route($SelfNet, ?Dst, ?NextHop).  (* ?Dst is a vnet *)

    peering(?VNet1, ?VNet2) :- 
      ?Auth1 := rootId(?VNet1), ?Auth1: neighbor(?VNet2), 
      ?Auth2 := rootId(?VNet2), ?Auth2: neighbor(?VNet1).
 
    route(?VNet, ?Dst, ?NextHop) :-      (* A route from ?VNet to ?Dst via a specified next hop *)
      peering(?VNet, ?Nb),
      advertiseRoute(?Nb, ?VNet, ?Dst),  (* ?Nb advertises to ?VNet *)
      ?Authority := rootId(?VNet),
      ?Authority: acceptRoute(?VNet, ?Nb, ?Dst),
      controlPrefix(?VNet, ?Authority),
      ?NextHop = ?Nb.                    (* Unify *)

    advertiseRoute(?Nb, ?VNet, ?Dst) :-       (* ?Nb advertises to ?VNet *)
      ?NbAuthority := rootId(?Nb),
      ?NbAuthority: carryTraffic(?Nb, ?VNet, ?Dst),
      controlPrefix(?Nb, ?NbAuthority),
      route(?Nb, ?Dst, ?NH),
      ?NH != ?VNet.                      (* ?NH isn't ?VNet; avoid loops *)

    advertiseRoute(?Nb, ?VNet, ?Dst) :-
      ?NbAuthority := rootId(?Nb),
      ?NbAuthority: carryTraffic(?Nb, ?VNet, ?Nb),   (* ?NbAuthority originates ?Nb *)
      controlPrefix(?Nb, ?NbAuthority),
      ?Dst = ?Nb.
  }.


defetch fetchCert(?LinkRef) :- [?LinkRef].

defguard queryRoute(?Dst) :-
  spec("Query routes to the Dst"),
  ?RoutingChainRef := fetchCert($SelfNet),
  ?NetIDChainRef := fetchCert($Self),
  ?RoutingRulesRef := routingRules(),
  ?PrefixRulesRef := PrefixRules(),
  {
    import($RoutingChainRef).
    import($NetIDChainRef).
    import($RoutingRulesRef).
    import($PrefixRulesRef).
    route($Dst, ?NextHop)?
  }.
     

defpost postVNetIDSet(?CN) :- [makeVNetIDSet(?CN)].

defpost postLink2VNetIDSet(?LinkRef, ?VNet) :- [addLink2VNetIDSet(?LinkRef, ?VNet)].

defpost postNeighbor(?VNet, ?Neighbor) :- [addNeighbor(?VNet, ?Neighbor)].

defpost postRouteAd(?VNet, ?Src, ?Dst) :- [carryTraffic(?VNet, ?Src, ?Dst)].

defpost postRouteAcceptance(?VNet, ?NextHop, ?Dst) :- [acceptRoute(?VNet, ?NextHop, ?Dst)].
