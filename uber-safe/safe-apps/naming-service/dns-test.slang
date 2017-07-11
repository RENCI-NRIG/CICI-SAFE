
(*
defcon resolve() :- 
  spec('Resolving rules'),
  {
    resolve(?Name, ?O) :- singleComponent(?Name), rootNameserver: A(?Name, ?O).
    resolve(?Name, ?O) :- splitFirst(?Name, ?First, ?Rest), rootNameServer: NS(?First, ?TldNameserver), down(?TldNameserver, ?Rest, ?O).
    down(?Nameserver, ?Subpath, ?O) :- ?Nameserver: A(?Subpath, ?O).
    down(?Nameserver, ?Subpath, ?O) :- splitFirst(?Subpath, ?First, ?Rest), ?Nameserver: NS(?First, ?ChildNameserver), down(?ChildNameServer, ?Rest, ?O).
  }
*)

(*
    resolve(?NameAuthority, ?Name, ?Scid) :- singleComponent(?Name), ?NameAuthority: A(?Name, ?Scid).
    resolve(?NameAuthority, ?Name, ?Scid) :- splitFirst(?Name, ?First, ?Rest), NameAuthority: NS(?First, ?ChildAuthority), resolve(?ChildAuthority, ?Rest, ?Scid).
*)


(**
 *
 * Name authority records:
 *   NA(?DomainName, ?NameAuthority)
 * 
 * Object records:
 *   O(?Name, ?Scid)
 *
 *)

defcon inferChildAuthority(?NameAuthority, ?SubdomainName) :-
  spec("Rules to infer a child authority"),
  {
    ?ChildAuthority :- $NameAuthority: NS($SubdomainName, ?ChildAuthority)
    ?ChildAuthority?
  }.

defcon InferLeaf(?NameAuthority, ?LeafName) :-
  spec("Rules to infer the scid of a leaf name"),
  {
    ?Scid :- $NameAuthority: O($LeafName, ?Scid),
    ?Scid?
  }.

defetch fetchCert(?LinkRef) :- [?LinkRef].

resolve(?NameAuthority, ?Name, ?DirectNameAuthority, ?Scid) :- 
  singleComponent(?Name), 
  ?NARef := fetchCert(?NameAuthority), 
  ?InferRulesRef := inferLeaf(?NameAuthority, ?Name), 
  ?LeafSet := inferSet(?NARef, ?InferRulesRef), 
  ?Scid := reapId(?LeafSet), 
  ?DirectNameAuthority := ?NameAuthority.


resolve(?NameAuthority, ?Name, ?DirectNameAuthority, ?Scid) :-
  splittable(?Name),
  ?FirstComponent := splitHead(?Name), 
  ?RestName := splitTail(?Name), 
  ?NARef := fetchCert(?NameAuthority), 
  ?InferRulesRef := inferChildAuthority(?NameAuthority, ?FirstComponent), 
  ?ChildNameAuthoritySet := inferSet(?InferRulesRef, ?NARef), 
  ?ChildNameAuthority := reapId(?ChildNameAuthoritySet), 
  resolve(?ChildNameAuthority, ?RestName, ?DirectNameAuthority, ?Scid). 


defguard queryName(?RootAuthority, ?Name) :-
  spec("Query a name"),
  resolve(?RootAuthority, ?Name, ?DirectNameAuthority, ?Scid),
  ?NameAuthorityRef := fetchCert(?DirectNameAuthority),
  {
    import($NameAuthorityRef).
    O($Name, ?Scid)?
  }.



















defguard authenticatedName(?NameAuthority, ?Name, ?Scid) :-
  spec("Check if the name is authenticated"),
  ?NameAuthIdLink := computeIdFromName(?NameAuthority),
  ?NameAuthIdRef := fetchCert(?NameAuthIdLink),
  {
    import($NameAuthIdRef).
    $NameAuthority: A($Name, ?Scid)?
  }.

defguard testResolver(?RootAuth, ?Name) :-
  spec('Test name resolver'),
  ?NameAuthRef := resolve(?RootAuth, ?Name),
(*  ?CertChainRef := fetchCert(?NameAuthRef),*)
  {
(*    import($CertChainRef). *)
    Name($Name, $Scid)?
  }.



(*
resolve(?NameAuthority, ?Name, ?Scid) :- 
  singleComponent(?Name), 
  ?NameAuthority: A(?Name, ?Scid).

resolve(?NameAuthority, ?Name, ?Scid) :- 
  splitFirst(?Name, ?First, ?Rest), 
  NameAuthority: NS(?First, ?ChildAuthority), 
  resolve(?ChildAuthority, ?Rest, ?Scid),
end
*)
