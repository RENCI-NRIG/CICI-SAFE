defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/speaksfor1.pem'). *)

defcon grantSpeaksfor(?Issuer) :-
  spec('Subject issues a statement to grant speaksFor'),
  {
    (* Using speaksFor($Issuer, $Self). should work. 
       But I don't think this is the right way according to the code in slang/model/SpeaksFor *)
    speaksFor($Issuer, false).
    name("grantSpeaksfor/$Issuer").
  }.

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defcon speakStmtsFor(?Subject, ?SpeaksForRef) :-
  spec('exercise speaksFor'),
  {
    subject($Subject, nil).   (* subject(?SubjectId, ?Subject) *)
    speaker(nil, $SpeaksForRef).  (* speaker(?SpeakerSubject, ?SpeaksForRef)  Internally, the first attribute of speaker() is $Self;  $SpeaksForRef refers to a SafeSet that issues statements to grant speaksFor *)
    hello(speaksFor).
    said(?X) :- hello(?X).
    name("speakStmtsFor/$Subject/1").  (* this set is stored under the Subject's namespace in SafeSets *)
  }.

defetch fetchStmts(?StmtsRef) :- [?StmtsRef].

defguard checkSpeaksForStmts(?Subject) :-
  spec('Check stmts issued using speaksFor'),
  ?StmtsRef := computeIdFromName(?Subject, "speakStmtsFor/?Subject/1"),
  ?StmtsContext := fetchStmts(?StmtsRef),
  {
     import($StmtsContext).
     ?X: said(?Y)?
  }.

defpost postASpeaksfor(?Issuer) :- [grantSpeaksfor(?Issuer)].

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postStmtsUsingSpeaksfor(?Subject, ?SpeaksForRef) :- [speakStmtsFor(?Subject, ?SpeaksForRef)].
