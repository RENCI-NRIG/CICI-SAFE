defenv Selfie() :-
  spec('Load the principal keypair'),
  principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/tag_owner.pem').

(* defenv Registry :- u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM'. *)  (* Change the registry ID at deployment *)

defenv TagGuidEnv :- 'aTestingTag'.

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

(* defcon makeATagIdSet(?TagGuid) :-
  ?TagScid := "$Self:$TagGuidEnv", *)  (* We should take the value of TagGuid from the defcon args, which we cannot do now *)
  (* println("TagGuid: $TagGuid"), *)
  (* println("TagScid: $TagScid"), *)

defcon makeATagIdSet(?TagScid) :-
  spec('Construct the identity set for a tag'),
  {
    (* This is said by the tag owner;  don't need these *)
    (*
    tagAccess($TagScid, $Self, true).
    tagAuthority($TagScid, $Self, true).
    *)
    name("tag/$TagScid").
  }.

defcon delegateAccess(?Tag, ?User, ?Delegatable) :-
  spec('Delegate access for a tag to a user'),
  ?TagRef := computeIdFromName($Self, "tag/$Tag"),
  {
    delegateTagAccess($Tag, $User, $Delegatable).
    link($TagRef).
    name("tagAccess/$Tag/$User").     (* this link should be saved in the identity set of ?User *)
  }.

defcon delegateAccessToATag(?Tag, ?AnotherTag) :-
  spec('Delegate access to another tag'), (* Access is allowed if access to that tag is allowed *)
  ?TagRef := computeIdFromName($Self, "tag/$Tag"),
  {
    delegateTagAccessToAnotherTag($Tag, $AnotherTag).
    link($TagRef).
    name("tagAccess/$Tag/$AnotherTag").  (* this link should be saved in the identity set of ?AnotherTag *)
  }.

(* defcon delegateAccessToATagSet(?Tag, ?TagSet) :- spec('Delegate access to a set of tags), *)
(* Access is allowd if access to that tag set, i.e., all tags, is allowed *)
(* Postpone this generic version, as we need support for the type TagSet or Label *)

defcon delegateAccessToTwoTags(?Tag, ?AnotherTag1, ?AnotherTag2) :-
  spec('Delegate access to two tags'),  (* Access is allowed if access to each of those tags is allowed *)
  (* ?TagOwner := rootId(?Tag), *)
  (* ?TagRef := computeIdFromName($TagOwner, "tag/$Tag"), *)
  ?TagRef := computeIdFromName($Self,  "tag/$Tag"),
  {
     delegateTagAccessToTwoTags($Tag, $AnotherTag1, $AnotherTag2).
     link($TagRef).
     name("tagAccess/$Tag/$AnotherTag1/$AnotherTag2").  (* this link should be save in each identity set of ?AnotherTagX *)
  }.

defcon delegateAuthority(?Tag, ?User, ?Delegatable) :-
  spec('Delegate tag authority to a user'),
  ?TagRef := computeIdFromName($Tag, "tag/$Tag"),
  {
    delegateTagAuthority($Tag, $User, $Delegatable).
    link($Self).
    name("tagAuthority/$Tag/$User").
  }.

defcon delegateAuthorityToATag(?Tag, ?AnotherTag) :-
  spec('Delegate authority to another tag'),
  ?TagRef := computeIdFromName($Self, "tag/$Tag"),
  {
    delegateTagAuthorityToAnotherTag($Tag, $AnotherTag).
    link($TagRef).
    name("tagAuthority/$Tag/$AnotherTag").
  }.

defcon delegateAuthorityToTwoTags(?Tag, ?AnotherTag1, ?AnotherTag2) :-
  spec('Delegate authority to two tags'), (* Authority is granted if authority to each of those tags is granted *)
  ?TagRef := computeIdFromName($Self, "tag/$Tag"),
  {
    delegateTagAuthorityToTwoTags($Tag, $AnotherTag1, $AnotherTag2).
    link($TagRef).
    name("tagAuthority/$Tag/$AnotherTag1/$AnotherTag2").
  }.

defcon addALinkToIdSet(?LinkRef) :-
  spec("Add a link to the user's ID set. Invoked after the user gets a delegation"),
  {
    link($LinkRef).
    name('').
  }.

defcon addALinkToTagIdSet(?LinkRef, ?Tag) :-
  spec("Add a link to a tag's ID set. Invoked after delegation to this tag"),
  (* ?TagIdSetRef := computeIdFromName($Self, "tag/$Tag"), *)
  {
    link($LinkRef).
    name("tag/$Tag").
  }.



defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postATag(?TagScid) :- [makeATagIdSet(?TagScid)].

defpost postTagAccessDelegation(?Tag, ?User, ?Delegatable) :- [delegateAccess(?Tag, ?User, ?Delegatable)].

defpost postTagAccessDelegationToATag(?Tag, ?AnotherTag) :- [delegateAccessToATag(?Tag, ?AnotherTag)].

defpost postTagAccessDelegationToTwoTags(?Tag, ?AnotherTag1, ?AnotherTag2) :- [delegateAccessToTwoTags(?Tag, ?AnotherTag1, ?AnotherTag2)].

defpost postTagAuthorityDelegation(?Tag, ?User, ?Delegatable) :- [delegateAuthority(?Tag, ?User, ?Delegatable)].

defpost postTagAuthorityDelegationToATag(?Tag, ?AnotherTag) :- [delegateAuthorityToATag(?Tag, ?AnotherTag)].

defpost postTagAuthorityDelegationToTwoTags(?Tag, ?AnotherTag1, ?AnotherTag2) :- [delegateAuthorityToTwoTags(?Tag, ?AnotherTag1, ?AnotherTag2)].

defpost postALinkToIdSet(?LinkRef) :- [addALinkToIdSet(?LinkRef)].

defpost postALinkToTagIdSet(?LinkRef, ?Tag) :- [addALinkToTagIdSet(?LinkRef, ?Tag)].


(* definit postIdSet($1). *)
(* definit postALinkToIdSet($1). *)
(* definit postATag($1). *)
(* definit postTagAccessDelegation($1, $2, $3). *)







(* We don't need a defguard for a post *)
(*
defguard createIdSet(?CN) :- [postIdSet(?CN)].

defguard createATag(?Tag) :-
  {
    postATag($Tag).
  }.

defguard createTagAccessDelegation(?Tag, ?User, ?Delegatable) :-
  {
    postTagAccessDelegation($Tag, $User, $Delegatable).
  }.

defguard createTagAccessDelegationToATag(?Tag, ?AnotherTag) :-
  {
    postTagAccessDelegationToATag($Tag, $AnotherTag).
  }.

defguard createTagAccessDelegationToTwoTags(?Tag, ?AnotherTag1, ?AnotherTag2) :-
  {
    postTagAccessDelegationToTwoTags($Tag, $AnotherTag1, $AnotherTag2).
  }.

defguard createTagAuthorityDelegation(?Tag, ?User, ?Delegatable) :-
  {
    postTagAuthorityDelegation($Tag, $User, $Delegatable).
  }.

defguard createTagAuthorityDelegationToATag(?Tag, ?AnotherTag) :-
  {
    postTagAuthorityDelegationToATag($Tag, $AnotherTag).
  }.

defguard createTagAuthorityDelegationToTwoTags(?Tag, ?AnotherTag1, ?AnotherTag2) :-
  {
    postTagAuthorityDelegationToTwoTags($Tag, $AnotherTag1, $AnotherTag2).
  }.

defguard addPostALinkToIdSet(?LinkRef) :- 
  { 
    postALinkToIdSet(?LinkRef).
  }.

defguard addPostALinkToTagIdSet(?LinkRef, ?Tag) :-
  {
    postALinkToTagIdSet(?LinkRef, ?Tag).
  }.
*)

(* definit createIdSet('tag_owner'). *)
(* definit createIdSet($1). *)
