defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/tag_owner1.pem'). *)

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

defcon makeTagSet(?Tag) :-
  spec('Construct the identity set for a tag'),
  {
    label("tags/$Tag").
  }.

defcon addTokenToTagSet(?Token, ?Tag) :-
  spec("Add a link to a tag's ID set. Invoked after delegation to this tag"),
  {
    link($Token).
    label("tags/$Tag").
  }.

defcon grantAccess(?Tag, ?User, ?Delegatable) :-
  spec('Delegate access for a tag to a user'), 
  ?TagRef := label($Self, "tags/?Tag"),
  {
    grantTagAccess($Tag, $User, $Delegatable).
    link($TagRef).
    label("tagAccess/$Tag/$User").     (* this link should be saved in the subject set of ?User *)
  }.

defcon delegateToTag(?Tag, ?ToTag, ?Delegatable) :-
  spec('Delegate access to another tag'), (* Access is allowed if access to that tag is allowed *)
  ?TagRef := label($Self, "tags/?Tag"),
  {
    nestTag($Tag, $ToTag, $Delegatable).
    link($TagRef).
    label("tagAccess/$Tag/$ToTag").  (* this link should be saved in the tag set of ?ToTag *)
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postSubjectSet() :- [makeMySubjectSet()].

defpost updateSubjectSet(?Token) :- [addTokenToSubjectSet(?Token)].

defpost postTagSet(?Tag) :- [makeTagSet(?Tag)].

defpost updateTagSet(?Token, ?Tag) :- [addTokenToTagSet(?Token, ?Tag)].

defpost postTagAccess(?Tag, ?User, ?Delegatable) :- [grantAccess(?Tag, ?User, ?Delegatable)].

defpost postTagDelegation(?Tag, ?ToTag, ?Delegatable) :- [delegateToTag(?Tag, ?ToTag, ?Delegatable)].
