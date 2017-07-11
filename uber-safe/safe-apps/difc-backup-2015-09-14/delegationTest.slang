defenv Selfie() :-
  spec('Load the principal keypair'),
  principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/registry.pem').


defcon accessDelegationPolicies() :-
  spec('Local policies on the delegation of tag access'),
  {
    tagAccess(?Tag, ?User) :-
      tagAccess(?Tag, ?User, _).

    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?Owner := rootId(?Tag), ?Owner: delegateTagAccess(?Tag, ?User, ?Delegatable).
    

    (* We cannot write a rule like these in safe *) 
    (*
    tagAccess(?Tag, ?Owner, true) :-
      ?Owner := rootId(?Tag).
    *)
    (*
    tagAccess(?Tag, ?Owner, ?Delegatable) :-
      ?Owner := rootId(?Tag).
    *)

    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?Delegator: delegateTagAccess(?Tag, ?User, ?Delegatable),
      tagAccess(?Tag, ?Delegator, true).


    delegateTagAccessToAnotherTag(?SrcTag, ?DstTag) :-
      ?SrcOwner := rootId(?SrcTag), ?SrcOwner: delegateTagAccessToAnotherTag(?SrcTag, ?DstTag).

    delegateTagAccessToAnotherTag(?SrcTag, ?DstTag) :-
      ?SrcOwner := rootId(?SrcTag), ?SrcOwner: delegateTagAccessToAnotherTag(?SrcTag, ?MidTag),
      delegateTagAccessToAnotherTag(?MidTag, ?DstTag).

    tagAccess(?Tag, ?User, ?Delegatable) :-
      delegateTagAccessToAnotherTag(?Tag, ?AnotherTag), tagAccess(?AnotherTag, ?User, ?Delegatable).

    name('accessDelegationPolicies').
  }.


defcon difcAccessPolicies(?User, ?File) :-
  spec('Local DIFC access policies'),
  {
    fileAccess($File, $User).
    authenticFileTag(?File, ?Tag) :- ?FileOwner := rootId(?File), ?FileOwner: fileTag(?File, ?Tag).
    tagAccess(?Tag, ?User) :- fileAccess(?File, ?User), authenticFileTag(?File, ?Tag).
    (* We cannot use this; inference issue *)
    (* tagAccess(?Tag, ?User) :- ?FileOwner := rootId(?File), fileAccess(?File, ?User), ?FileOwner: fileTag(?File, ?Tag). *)
    tagAccess(?Tag, ?User)?
    name('DIFCAccessPolicies').
  }.


defetch fetchContext(?CntRef) :- [?CntRef].
defetch fetchFileTags(?LinkRef) :- [?LinkRef].

defguard authorizeFileAccess(?FileOwner, ?File, ?User) :-
  spec('Authorize a suject to access a file'),
  ?InferenceContext := fetchContext(?User),
  (* ?FileOwner := rootId(?File), *)  (* rootId belongs to slog; we cannot use it here *)
  ?FileTagsLink := computeIdFromName(?FileOwner, "fileLabel/?File"),
  ?FileTagsRef := fetchFileTags(?FileTagsLink),
  ?PolicySet := accessDelegationPolicies(),
  ?DifcRulesRef := difcAccessPolicies(?User, ?File),
  ?SubgoalsRef := inferSet(?DifcRulesRef, ?FileTagsRef),
  {
    import($InferenceContext).
    import($SubgoalsRef).
    import($PolicySet).
  }.


defguard authorizeTagAccess(?Tag, ?User) :-
  spec('Authorize a suject to access a tag'),
  ?InferenceContext := fetchContext(?User),
  ?PolicySet := accessDelegationPolicies(),
  {
     import($InferenceContext).
     import($PolicySet).
     tagAccess($Tag, $User)?
  }.

