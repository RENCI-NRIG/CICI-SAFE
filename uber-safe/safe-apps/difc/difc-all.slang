//defenv Selfie() :-
//  spec('Load the principal keypair'),
//  principal($1).
//  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/registry.pem'). *)

//
// Identity set (apply to all participants)
//

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defcon makeMySubjectSet() :-
  spec("Construct the subject set for self"),
  {
    link($Self).
    label("subject($Self)").
  }.

defcon addTokenToSubjectSet(?Token) :-
  spec("Add a token to the user's subject set. Invoke after the user gets a delegation"),
  {
    link($Token).
    label("subject($Self)").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postSubjectSet() :- [makeMySubjectSet()].

defpost updateSubjectSet(?Token) :- [addTokenToSubjectSet(?Token)].

//
// Tag Authority 
//

defcon makeTagSet(?Tag) :-
  spec('Construct the identity set for a tag'),
  {
    label("tags/$Tag").
  }.

defpost postTagSet(?Tag) :- [makeTagSet(?Tag)].

defcon addTokenToTagSet(?Token, ?Tag) :-
  spec("Add a link to a tag's ID set. Invoked after delegation to this tag"),
  {
    link($Token).
    label("tags/$Tag").
  }.

defpost updateTagSet(?Token, ?Tag) :- [addTokenToTagSet(?Token, ?Tag)].

defcon grantAccess(?Tag, ?User, ?Delegatable) :-
  spec('Delegate access for a tag to a user'),
  ?TagRef := label($Self, "tags/?Tag"),
  {
    grantTagAccess($Tag, $User, $Delegatable).
    link($TagRef).
    label("grantTagAccess/$Tag/$User").     (* this link should be saved in the subject set of ?User *)
  }.

defpost postTagAccess(?Tag, ?User, ?Delegatable) :- [grantAccess(?Tag, ?User, ?Delegatable)].

defcon delegateToTag(?Tag, ?ToTag, ?Delegatable) :-
  spec('Delegate access to another tag'), (* Access is allowed if access to that tag is allowed *)
  ?TagRef := label($Self, "tags/?Tag"),
  {
    nestTag($Tag, $ToTag, $Delegatable).
    link($TagRef).
    label("delegateTag/$Tag/$ToTag").  (* this link should be saved in the tag set of ?ToTag *)
  }.

defpost postTagDelegation(?Tag, ?ToTag, ?Delegatable) :- [delegateToTag(?Tag, ?ToTag, ?Delegatable)].


//
// File Authority
//

defcon labelFile(?File, ?Tag) :-    (* both ?File and ?Tag are scids *)
  spec('Associate a tag to a file'),
  {
    fileTag($File, $Tag).
    label("fileLabel/$File").
  }.

defpost postFileTag(?File, ?Tag) :- [labelFile(?File, ?Tag)].

//
// User
//

defcon delegateAccess(?Tag, ?User, ?Delegatable) :-
  spec('Delegate access for a tag to a user'),
  ?SubjectSetRef := label("subject($Self)"),
  {
    delegateTagAccess($Tag, $User, $Delegatable).
    link($SubjectSetRef).
    label("delegateTagAccess/$Tag/$User").     (* this link should be saved in the identity set of ?User *)
  }.

defpost postTagAccessDelegation(?Tag, ?User, ?Delegatable) :- [delegateAccess(?Tag, ?User, ?Delegatable)].

//
// File Registry
//

defcon tagAccessPolicy() :-
  spec('Local policies on the delegation of tag access'),
  {
    tagAccess(?Tag, ?User) :-
      tagAccess(?Tag, ?User, _).

    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?Owner := rootPrincipal(?Tag), 
      ?Owner: grantTagAccess(?Tag, ?User, ?Delegatable).
    
    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?Delegator: delegateTagAccess(?Tag, ?User, ?Delegatable),
      tagAccess(?Tag, ?Delegator, true).

    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?TRoot := rootPrincipal(?Tag),
      ?TRoot: nestTag(?Tag, ?ToTag, true), 
      tagAccess(?ToTag, ?User, ?Delegatable).

    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?TRoot := rootPrincipal(?Tag),
      ?TRoot: nestTag(?Tag, ?ToTag, false), 
      ?ToTagRoot := rootPrincipal(?ToTag),
      ?ToTagRoot: grantTagAccess(?ToTag, ?User, ?Delegatable).

//
//  These rules are redundant
//
//    nestTag(?SrcTag, ?DstTag) :-
//      ?SrcOwner := rootPrincipal(?SrcTag), ?SrcOwner: nestTag(?SrcTag, ?DstTag).
//
//    nestTag(?SrcTag, ?DstTag) :-
//      ?SrcOwner := rootPrincipal(?SrcTag), ?SrcOwner: nestTag(?SrcTag, ?MidTag),
//      nestTag(?MidTag, ?DstTag).
//

    label('tag-access-policy').
  }.

definit tagAccessPolicy().

defcon difcAccessPolicy(?User, ?File) :-
  spec('Local DIFC access policies'),
  {
    fileToAccess($File, $User).

    tagAccess(?Tag, ?User) :- 
      fileToAccess(?File, ?User), 
      ?FileOwner := rootPrincipal(?File), 
      ?FileOwner: fileTag(?File, ?Tag).

    tagAccess(?Tag, ?User)?

    label('DIFCAccessPolicy/$File').
  }.

defguard checkFileAccess(?User, ?File) :- 
  spec('Check if a subject can access a file'),
  ?FileOwner := rootPrincipal(?File),
  ?FileTagsRef := label(?FileOwner, "fileLabel/?File"),
  ?DifcRulesRef := difcAccessPolicy(?User, ?File),
  ?SubgoalsRef := inferQuerySet(?DifcRulesRef, ?FileTagsRef),
  ?TagAccessPolicySet := label("tag-access-policy"),
  {
    link($BearerRef).
    link($SubgoalsRef).
    link($TagAccessPolicySet).
  }.

defguard checkTagAccess(?User, ?Tag) :-
  spec('Check if a subject can have access for a tag'),
  ?TagAccessPolicySet := label("tag-access-policy"),
  {
     link($BearerRef).
     link($TagAccessPolicySet).
     tagAccess($Tag, $User)?
  }.
