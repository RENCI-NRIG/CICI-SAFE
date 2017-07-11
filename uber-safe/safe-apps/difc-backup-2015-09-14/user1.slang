defenv Selfie() :-
  spec('Load the principal keypair'),
  principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/user3.pem').

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defcon addALinkToIdSet(?LinkRef) :-
  spec("Add a link to the user's ID set. Invoked after the user gets a delegation"),
  {
    link($LinkRef).
    name('').
  }.

defcon delegateAccess(?Tag, ?User, ?Delegatable) :-
  spec('Delegate access for a tag to a user'),
  { 
    delegateTagAccess($Tag, $User, $Delegatable).
    link($Self).
    name("tagAccess/$Tag/$User").     (* this link should be saved in the identity set of ?User *)
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postALinkToIdSet(?LinkRef) :- [addALinkToIdSet(?LinkRef)].

defpost postTagAccessDelegation(?Tag, ?User, ?Delegatable) :- [delegateAccess(?Tag, ?User, ?Delegatable)].

(* definit postIdSet($1). *)
(* definit postALinkToIdSet($1). *)





(*
defguard createFileLabel(?File, ?Tag) :-
  {
    postFileLabel(?File, ?Tag).
  }.

definit createFileLabel($1, $2).
*)

