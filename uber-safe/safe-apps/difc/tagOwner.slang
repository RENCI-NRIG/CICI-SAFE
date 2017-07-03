defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/tag_owner1.pem'). *)

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
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
