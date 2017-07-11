defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/registry.pem'). *)

defcon tagAccessPolicy() :-
  spec('Local policies on the delegation of tag access'),
  {
    tagAccess(?Tag, ?User) :-
      tagAccess(?Tag, ?User, _).

    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?Owner := rootId(?Tag), 
      ?Owner: grantTagAccess(?Tag, ?User, ?Delegatable).
    
    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?Delegator: delegateTagAccess(?Tag, ?User, ?Delegatable),
      tagAccess(?Tag, ?Delegator, true).

    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?TRoot := rootId(?Tag),
      ?TRoot: nestTag(?Tag, ?ToTag, true), 
      tagAccess(?ToTag, ?User, ?Delegatable).

    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?TRoot := rootId(?Tag),
      ?TRoot: nestTag(?Tag, ?ToTag, false), 
      ?ToTagRoot := rootId(?ToTag),
      ?ToTagRoot: grantTagAccess(?ToTag, ?User, ?Delegatable).

//
//  These rules are redundant
//
//    nestTag(?SrcTag, ?DstTag) :-
//      ?SrcOwner := rootId(?SrcTag), ?SrcOwner: nestTag(?SrcTag, ?DstTag).
//
//    nestTag(?SrcTag, ?DstTag) :-
//      ?SrcOwner := rootId(?SrcTag), ?SrcOwner: nestTag(?SrcTag, ?MidTag),
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
      ?FileOwner := rootId(?File), 
      ?FileOwner: fileTag(?File, ?Tag).

    tagAccess(?Tag, ?User)?

    label('DIFCAccessPolicy/$File').
  }.

defguard checkFileAccess(?User, ?File) :- 
  spec('Check if a subject can access a file'),
  ?FileOwner := rootId(?File),
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
