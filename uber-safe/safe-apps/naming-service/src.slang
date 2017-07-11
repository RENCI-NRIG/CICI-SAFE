defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/naming-service/keys/na1.pem'). *)

defcon checkDirectObject(?Dir, ?Name) :- 
  spec("Rules to check if a name represents a direct object"),
  {
    ?Scid :- ?NA := rootId(?Dir), ?NA: O($Name, ?Scid, $Dir).
    ?Scid?
    name("$Dir/$Name").
  }. 

defetch fetchCert(?LinkRef) :- [?LinkRef].

resolve(?Dir, ?Name, ?Scid, ?LastCertToken) :-
  singleComponent(?Name),
  ?RulesRef := checkDirectObject(?Dir, ?Name),
  ?ObjRecToken := computeIdFromName(?Dir, ?Name),
  ?ObjRecRef := fetchCert(?ObjRecToken),
  ?ScidSet := inferSet(?RulesRef, ?ObjRecRef),
  ?ObjScid := reapId(?ScidSet),
  unify(?Scid, ?ObjScid), 
  unify(?LastCertToken, ?ObjRecToken).

resolve(?Dir, ?Name, ?Scid, ?LastCertToken) :-
  splitHead(?Name, ?FirstComponent, ?Tail),
  ?RulesRef := checkDirectObject(?Dir, ?FirstComponent), 
  ?ObjRecToken := computeIdFromName(?Dir, ?FirstCompent),
  ?ObjRecRef := fetchCert(?ObjRecToken), 
  ?SubDirSet := inferSet(?RulesRef, ?ObjRecRef), 
  ?SubDir := reapId(?SubDirSet), 
  resolve(?SubDir, ?Tail, ?Scid, ?LastCertToken). 


defcon SRNEvaluation(?RootDir) :- 
  spec("Rules for SRN certified evaluation, in a bottom-up fashion"),
  {
    SRN(?Name, ?Scid) :- singleComponent(?Name), ?RootAuthority := rootId($RootDir), ?RootAuthority: O(?Name, ?Scid, $RootDir).
    SRN(?Name, ?Scid) :- splitLast(?Name, ?Init, ?LastComponent), SRN(?Init, ?Dir), ?DirAuthority := rootId(?Dir), ?DirAuthority: O(?LastComponent, ?Scid, ?Dir).
  }.

defguard queryName(?RootDir, ?Name, ?Scid, ?LastCertToken) :-
  spec("Query a name"),
  resolve(?RootDir, ?Name, ?Scid, ?LastCertToken),
  ?CertChainRef := fetchCert(?LastCertToken),
  ?EvalRulesRef := SRNEvaluation(?RootDir),
  {
    import($CertChainRef).
    import($EvalRulesRef).
    SRN($Name, ?Scid)?
  }.
