defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/file_owner.pem'). *)

defcon labelAFile(?File, ?Tag) :-    (* both ?File and ?Tag are scids *) 
  spec('Associate a tag to a file'),  
  {
    hello(?World) :- bob: Hi(?World).
    alice: fileTag($File, $Tag).
    alice: fileTag(fileB, tagx).
    fileTag($File, $Tag).
    name("fileLabel/$File").
  }.

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postFileLabel(?File, ?Tag) :- [labelAFile(?File, ?Tag)].

defetch fetchContext(?CntRef) :- [?CntRef].
defetch fetchFileTags(?LinkRef) :- [?LinkRef].


defguard fileAccess(?Token) :-
  spec('Authorize a suject to access a file'),
  ?InferenceContext := fetchContext(?Token),
  {
    import($InferenceContext).
    access(?File) :- alice: fileTag(?File, ?Tag).
    access(?File)?
  }.


(* definit postIdSet($1). *)
(* definit postFileLabel($1, $2). *)

