defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/file_owner.pem'). *)

defcon labelFile(?File, ?Tag) :-    (* both ?File and ?Tag are scids *) 
  spec('Associate a tag to a file'),  
  {
    fileTag($File, $Tag).
    label("fileLabel/$File").
  }.

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postFileTag(?File, ?Tag) :- [labelFile(?File, ?Tag)].

(* definit postIdSet($1). *)
(* definit postFileTag($1, $2). *)
