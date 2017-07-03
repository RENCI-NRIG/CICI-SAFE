(**
 *
 * Name authority records:
 *   NA(?DomainName, ?NameAuthority)
 * 
 * Object records:
 *   O(?Name, ?Scid)
 *
 *)

defenv RootAuthority :- u'As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8'.

defcon rules2InferChildAuthority(?NameAuthority, ?SubdomainName) :-
  spec("Rules to infer a child authority"),
  {
    ?ChildAuthority :- $NameAuthority: NS($SubdomainName, ?ChildAuthority).
    ?ChildAuthority?
  }.

defcon rules2InferLeaf(?NameAuthority, ?LeafName) :-
  spec("Rules to infer the scid of a leaf name"),
  {
    ?Scid :- $NameAuthority: O($LeafName, ?Scid).
    ?Scid?
  }.

defetch fetchCert(?LinkRef) :- [?LinkRef].

resolve(?CurNameAuthority, ?DirectNameAuthority, ?Name) :-
  singleComponent(?Name),
  

resolve(?CurNameAuthority, ?DirectNameAuthority, ?Name) :-
  splittable(?Name),
  ?FirstComponent := splitHead(?Name),
  ?RestName := splitTail(?Name),
  ?NARef := fetchCert(?CurNameAuthority),
  ?InferRulesRef := rules2InferChildAuthority(?CurNameAuthority, ?FirstComponent),
  ?ChildNameAuthoritySet := inferSet(?InferRulesRef, ?NARef),
  ?ChildNameAuthority := reapId(?ChildNameAuthoritySet),
  resolve(?ChildNameAuthority, ?DirectNamethority, ?Name).


resolve(?NameAuthority, ?Name) :-
  singleComponent(?Name),
  ?RootRef := fetchCert($RootAuthority),
  ?InferRulesRef := rules2InferLeaf($RootAuthority, ?Name).
  inferSet(?RootRef, ?InferRulesRef).
  

resolve(?NameAuthority, ?Name, ?DirectNameAuthority, ?Scid) :- 
  singleComponent(?Name), 
  ?NARef := fetchCert(?NameAuthority), 
  ?InferRulesRef := rules2InferLeaf(?NameAuthority, ?Name), 
  ?LeafSet := inferSet(?NARef, ?InferRulesRef), 
  ?Scid := reapId(?LeafSet), 
  ?DirectNameAuthority := ?NameAuthority.


resolve(?NameAuthority, ?Name, ?DirectNameAuthority, ?Scid) :-
  splittable(?Name),
  ?FirstComponent := splitHead(?Name), 
  ?RestName := splitTail(?Name), 
  ?NARef := fetchCert(?NameAuthority), 
  ?InferRulesRef := rules2InferChildAuthority(?NameAuthority, ?FirstComponent), 
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
