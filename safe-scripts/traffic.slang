defenv Version() :- "set68".
defenv PolicyVersion() :- "policy1".
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
    label("subject($Self)($Version)").
  }.

defcon addTokenToSubjectSet(?Token) :-
  spec("Add a token to the user's subject set. Invoke after the user gets a delegation"),
  {
    link($Token).
    label("subject($Self)($Version)").
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
    label("tags/$Tag($Version)").
  }.

defpost postTagSet(?Tag) :- [makeTagSet(?Tag)].

defcon addTokenToTagSet(?Token, ?Tag) :-
  spec("Add a link to a tag's ID set. Invoked after delegation to this tag"),
  {
    link($Token).
    label("tags/$Tag($Version)").
  }.

defpost updateTagSet(?Token, ?Tag) :- [addTokenToTagSet(?Token, ?Tag)].

defcon grantAccess(?Tag, ?User, ?Delegatable) :-
  spec('Delegate access for a tag to a user'),
  ?TagRef := label($Self, "tags/?Tag($Version)"),
  {
    grantTagAccess($Tag, $User, $Delegatable).
    link($TagRef).
    label("grantTagAccess/$Tag/$User($Version)").     (* this link should be saved in the subject set of ?User *)
  }.

defpost postTagAccess(?Tag, ?User, ?Delegatable) :- [grantAccess(?Tag, ?User, ?Delegatable)].

defcon delegateToTag(?Tag, ?ToTag, ?Delegatable) :-
  spec('Delegate access to another tag'), (* Access is allowed if access to that tag is allowed *)
  ?TagRef := label($Self, "tags/?Tag($Version)"),
  {
    nestTag($Tag, $ToTag, $Delegatable).
    link($TagRef).
    label("delegateTag/$Tag/$ToTag($Version)").  (* this link should be saved in the tag set of ?ToTag *)
  }.

defpost postTagDelegation(?Tag, ?ToTag, ?Delegatable) :- [delegateToTag(?Tag, ?ToTag, ?Delegatable)].


//
// Network Authority
//

defcon labelNetwork(?Network, ?Tag) :-    (* both ?Network and ?Tag are scids *)
  spec('Associate a tag to a network'),
  {
    networkTag($Network, $Tag).
    label("networkLabel/$Network($Version)").
  }.

defpost postNetworkTag(?Network, ?Tag) :- [labelNetwork(?Network, ?Tag)].

//
// User
//

defcon delegateAccess(?Tag, ?User, ?Delegatable) :-
  spec('Delegate access for a tag to a user'),
  ?TagSetRef := label("tags/?Tag($Version)"),
  {
    delegateTagAccess($Tag, $User, $Delegatable).
    link($TagSetRef).
    label("delegateTagAccess/$Tag/$User($Version)").     (* this link should be saved in the identity set of ?User *)
  }.

defpost postTagAccessDelegation(?Tag, ?User, ?Delegatable) :- [delegateAccess(?Tag, ?User, ?Delegatable)].

//
// Network Registry
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

    label('tag-access-policy($PolicyVersion)').
  }.

//definit tagAccessPolicy().

defpost postTagAccessPolicy() :- [tagAccessPolicy()].

defcon networkAccessPolicy(?User, ?Network) :-
  spec('Local Network access policies'),
  {
    networkToAccess($Network, $User).

    tagAccess(?Tag, ?User) :-
      networkToAccess(?Network, ?User),
      ?NetworkOwner := rootPrincipal(?Network),
      ?NetworkOwner: networkTag(?Network, ?Tag).

    tagAccess(?Tag, ?User)?

    label('NetworkAccessPolicy/$Network($PolicyVersion)').
  }.

defguard checkNetworkAccess(?User, ?Network) :- 
  spec('Check if a subject can access a network'),
  ?NetworkOwner := rootPrincipal(?Network),
  ?NetworkTagsRef := label(?NetworkOwner, "networkLabel/?Network($Version)"),
  ?NetworkRulesRef := networkAccessPolicy(?User, ?Network),
  ?SubgoalsRef := inferQuerySet(?NetworkRulesRef, ?NetworkTagsRef),
  ?TagAccessPolicySet := label("tag-access-policy($PolicyVersion)"),
  {
    link($BearerRef).
    link($SubgoalsRef).
    link($TagAccessPolicySet).
  }.

defguard checkTagAccess(?User, ?Tag) :-
  spec('Check if a subject can have access for a tag'),
  ?TagAccessPolicySet := label("tag-access-policy($PolicyVersion)"),
  {
     link($BearerRef).
     link($TagAccessPolicySet).
     tagAccess($Tag, $User)?
  }.
