defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/naming-service/keys/na1.pem'). *)

defcon checkDirectObject(?Dir, ?Name) :- 
  spec("Rules to check if a name represents a direct object"),
  {
    ?Scid :- ?NameAuthority := rootId($Dir), ?NameAuthority: nameObject($Name, ?Scid, $Dir).
    ?Scid?
    label("$Dir-$Name").
  }. 

resolve(?Dir, ?Name, ?Scid, ?LastCertToken) :-
  singleComponent(?Name),
  ?RulesRef := checkDirectObject(?Dir, ?Name),
  ?ObjectAuthority := rootId(?Dir),
  ?ObjectGuid := objectFromScid(?Dir),   
  ?ObjRecToken := label(?ObjectAuthority, "?ObjectGuid/?Name"),
  ?ScidSet := inferSet(?RulesRef, ?ObjRecToken),
  ?ObjScid := reapId(?ScidSet),
  unify(?Scid, ?ObjScid), 
  unify(?LastCertToken, ?ObjRecToken).

resolve(?Dir, ?Name, ?Scid, ?LastCertToken) :-
  splitHead(?Name, ?FirstComponent, ?Tail),
  ?RulesRef := checkDirectObject(?Dir, ?FirstComponent), 
  ?ObjectAuthority := rootId(?Dir),
  ?ObjectGuid := objectFromScid(?Dir),   
  ?ObjRecToken := label(?ObjectAuthority, "?ObjectGuid/?FirstComponent"), 
  ?SubDirSet := inferSet(?RulesRef, ?ObjRecToken), 
  ?SubDir := reapId(?SubDirSet), 
  resolve(?SubDir, ?Tail, ?Scid, ?LastCertToken). 

defcon SRNEvaluation(?RootDir) :- 
  spec("Rules for SRN certified evaluation, in a bottom-up fashion"),
  {
    SRN(?Name, ?Scid) :- singleComponent(?Name), ?RootAuthority := rootId($RootDir), ?RootAuthority: nameObject(?Name, ?Scid, $RootDir).
    SRN(?Name, ?Scid) :- splitLast(?Name, ?Init, ?LastComponent), SRN(?Init, ?Dir), ?DirAuthority := rootId(?Dir), ?DirAuthority: nameObject(?LastComponent, ?Scid, ?Dir).
    label("$RootDir").
  }.

defguard queryName(?RootDir, ?Name) :-
  spec("Query a name"),
  resolve(?RootDir, ?Name, ?Scid, ?LastCertToken),
  ?EvalRulesRef := SRNEvaluation(?RootDir),
  {
    link($LastCertToken).
    link($EvalRulesRef).
    SRN($Name, ?Scid)?
  }.
