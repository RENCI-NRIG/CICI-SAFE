defenv Selfie() :-
  spec('Load the principal keypair'),
  principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/registry.pem').

(* The following statements don't work
defenv AccessDelegationPolicyRef :- computeIdFromName($Self, 'accessDelegationPolicies').
defenv AuthorityDelegationPolicyRef :- computeIdFromName($Self, 'authorityDelegationPolicies').
defenv DIFCAccessPolicyRef :- computeIdFromName($Self, 'DIFCAccessPolicies').
defenv DIFCAuthorityPolicyRef :- computeIdFromName($Self, 'DIFCAuthorityPolicies').
*)

defenv AccessDelegationPolicyRef :- computeIdFromName('Rnj7hCjk8eb10O37qpJGRDlgX6Tc36E_qZeOAF3u1ck', 'accessDelegationPolicies').
defenv AuthorityDelegationPolicyRef :- computeIdFromName('Rnj7hCjk8eb10O37qpJGRDlgX6Tc36E_qZeOAF3u1ck', 'authorityDelegationPolicies').
defenv DIFCAccessPolicyRef :- computeIdFromName('Rnj7hCjk8eb10O37qpJGRDlgX6Tc36E_qZeOAF3u1ck', 'DIFCAccessPolicies').
defenv DIFCAuthorityPolicyRef :- computeIdFromName('Rnj7hCjk8eb10O37qpJGRDlgX6Tc36E_qZeOAF3u1ck', 'DIFCAuthorityPolicies').
defenv FileRef     :- computeIdFromName('Rnj7hCjk8eb10O37qpJGRDlgX6Tc36E_qZeOAF3u1ck', 'file/testing_file').

defenv Owner :- u'0pbWZFJTrmeQ8PTFvpbYOJMfsFuAbPNk8kNDpjo6ETk'.
defenv File :- 'testing_file'.

defcon createAFile(?FileArg, ?OwnerArg) :-
  spec('Attest to the ownership of a file'),
  ?FileLabelRef := computeIdFromName($Owner, "fileLabel/$File"), (* We want to take the values from args *)
  {
    fileOwner($File, $Owner).  (* Need to make sure we don't re-assign a file to multiple owners *)
    link($FileLabelRef). 
    name("file/$File").
  }.

defcon accessDelegationPolicies() :-
  spec('Local policies on the delegation of tag access'),
  {
    tagAccess(?Tag, ?User) :-
      tagAccess(?Tag, ?User, _).

    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?Owner := rootId(?Tag), ?Owner: delegateTagAccess(?Tag, ?User, ?Delegatable).

    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?Delegator: delegateTagAccess(?Tag, ?User, ?Delegatable),
      tagAccess(?Tag, ?Delegator, true).

    (* This is probably not allowed by Safe *)
    (*
    ?SrcOwner: delegateTagAccessToAnotherTag(?SrcTag, ?DstTag) :-
      ?SrcOwner: delegateTagAccessToAnotherTag(?SrcTag, ?MidTag),
      ?MidOwner := rootId(?MidTag), ?MidOwner: delegateTagAccessToAnotherTag(?MidTag, ?DstTag).
    *)  

    (* this can be improved as the next one *)
    (*
    delegateTagAccessToAnotherTag(?SrcTag, ?DstTag) :-
      ?SrcOwner := rootId(?SrcTag), ?SrcOwner: delegateTagAccessToAnotherTag(?SrcTag, ?MidTag),
      ?MidOwner := rootId(?MidTag), ?MidOwner: delegateTagAccessToAnotherTag(?MidTag, ?DstTag).
    *)

    delegateTagAccessToAnotherTag(?SrcTag, ?DstTag) :-
      ?SrcOwner := rootId(?SrcTag), ?SrcOwner: delegateTagAccessToAnotherTag(?SrcTag, ?DstTag).

    (* We probably don't need this rule *)
    (*
    delegateTagAccessToAnotherTag(?SrcTag, ?DstTag) :-
      delegateTagAccessToAnotherTag(?SrcTag, ?MidTag), delegateTagAccessToAnotherTag(?MidTag, ?DstTag). 
    *)

    (* this caused an infinite loop *)
    (* 
    delegateTagAccessToAnotherTag(?SrcTag, ?DstTag) :-
      delegateTagAccessToAnotherTag(?SrcTag, ?MidTag), 
      ?MidOwner := rootId(?MidTag), ?MidOwner: delegateTagAccessToAnotherTag(?MidTag, ?DstTag).
    *)

    delegateTagAccessToAnotherTag(?SrcTag, ?DstTag) :- 
      ?SrcOwner := rootId(?SrcTag), ?SrcOwner: delegateTagAccessToAnotherTag(?SrcTag, ?MidTag),
      delegateTagAccessToAnotherTag(?MidTag, ?DstTag).

    (* WE don't need to check the ownership of the tag, because delegateTagAccessToAnotherTag does so *)
    (* tagAccess(?Tag, ?User, ?Delegatable) :-
      ?Owner := rootId(?Tag), ?Owner: delegateTagAccessToAnotherTag(?Tag, ?AnotherTag), tagAccess(?AnotherTag, ?User, ?Delegatable).
    *)

    tagAccess(?Tag, ?User, ?Delegatable) :-
      delegateTagAccessToAnotherTag(?Tag, ?AnotherTag), tagAccess(?AnotherTag, ?User, ?Delegatable).
  
    (* We don't support recursive delegations to sets of tags, as that needs slang support of TagSet or Label *)
    (* The user may have different privileges to delegate those two tags. We assume the user's privileges of those two tags are the same for now *)
    tagAccess(?Tag, ?User, ?Delegatable) :-
      ?Owner := rootId(?Tag), ?Owner: delegateTagAccessToTwoTags(?Tag, ?AnotherTag1, ?AnotherTag2), tagAccess(?AnotherTag1, ?User, ?Delegatable), tagAccess(?AnotherTag2, ?User, ?Delegatable).

    (* we probably don't need this *)
    (*
    tagAccess(?Tag, ?Owner, true) :-
      ?Owner := rootId(?Tag).
    *)

    name('accessDelegationPolicies').
  }.

defcon authorityDelegationPolicies() :-
  spec('Local policies on the delegation of tag authority'),
  {
    tagAuthority(?Tag, ?User) :-
      tagAuthority(?Tag, ?User, _).

    tagAuthority(?Tag, ?User, ?Delegatable) :-
      ?Owner := rootId(?Tag), ?Owner: delegateTagAuthority(?Tag, ?User, ?Delegatable).

    tagAuthority(?Tag, ?User, ?Delegatable) :-
      ?Delegator: delegateTagAuthority(?Tag, ?User, ?Delegatable),
      tagAuthority(?Tag, ?Delegator, true).

    delegateTagAuthorityToAnotherTag(?SrcTag, ?DstTag) :-
      ?SrcOwner := rootId(?SrcTag), ?SrcOwner: delegateTagAuthorityToAnotherTag(?SrcTag, ?DstTag).

    (* we don't use this *)
    (*
    delegateTagAuthorityToAnotherTag(?SrcTag, ?DstTag) :-
      delegateTagAuthorityToAnotherTag(?SrcTag, ?MidTag),
      ?MidOwner := rootId(?MidTag), ?MidOwner: delegateTagAuthorityToAnotherTag(?MidTag, ?DstTag).
    *)   

    delegateTagAuthorityToAnotherTag(?SrcTag, ?DstTag) :-
      ?SrcOwner := rootId(?SrcTag), ?SrcOwner: delegateTagAuthorityToAnotherTag(?SrcTag, ?MidTag),
      delegateTagAuthorityToAnotherTag(?MidTag, ?DstTag).
      
    tagAuthority(?Tag, ?User, ?Delegatable) :-
      delegateTagAuthorityToAnotherTag(?Tag, ?AnotherTag), tagAuthority(?AnotherTag, ?User, ?Delegatable).

    (* Don't support recursive delegations of authority to sets of tags for the same reason regarding delegation of access *)
    tagAuthority(?Tag, ?User, ?Delegatable) :-
      ?Owner := rootId(?Tag), ?Owner: delegateTagAuthorityToTwoTags(?Tag, ?AnotherTag1, ?AnotherTag2), 
      tagAuthority(?AnotherTag1, ?User, ?Delegatable), tagAuthority(?AnotherTag2, ?User, ?Delegatable).

    name('authorityDelegationPolicies').
  }.

defcon difcAccessPolicies() :-
  spec('Local DIFC access policies'),
  {
    (* The following policies are not allowed
    access(?Program, ?User, ?File) :-
      fileOwner(?File, ?Owner), ?Owner:fileTag(?File, ?Tag), tagAccess(?Tag, ?Program).
    access(?Program, ?User, ?File) :-
      fileOwner(?File, ?Owner), ?Owner:fileTag(?File, ?Tag), tagAccess(?Tag, ?User).
    *)
    
    (* Use the following rule temporarily *)
    (*  
    access(?Program, ?User, ?File) :-
      fileOwner(?File, ?Owner), ?Owner:fileTag(?File, ?Tag), tagAccess(?Tag, ?Program), tagAccess(?Tag, ?User).
    *)

    access(?User, ?File) :-
      fileOwner(?File, ?Owner), ?Owner:fileTag(?File, ?Tag), tagAccess(?Tag, ?User).

    name('DIFCAccessPolicies').
  }.

defcon difcAuthorityPolicies() :-
  spec('Local DIFC policies on tag authority'),
  {
    (* 
    authority(?Program, ?User, ?Tag) :-
      tagAuthority(?Tag, ?User).
    authority(?Program, ?User, ?Tag) :-
      tagAuthority(?Tag, ?Program).
    *)

    (* Use the following rule temporarily *)    
    authority(?Program, ?User, ?Tag) :-
      tagAuthority(?Tag, ?Program), tagAuthority(?Tag, ?User).
 
    name('DIFCAuthorityPolicies').
  }.

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.


(*
defenv AccessDelegationPolicyRef :- computeIdFromName($Self, 'accessDelegationPolicies').
defenv AuthorityDelegationPolicyRef :- computeIdFromName($Self, 'authorityDelegationPolicies').
defenv DIFCAccessPolicyRef :- computeIdFromName($Self, 'DIFCAccessPolicies').
defenv DIFCAuthorityPolicyRef :- computeIdFromName($Self, 'DIFCAuthorityPolicies').
*)

defetch fetchContext(?CntRef) :- [?CntRef].

(*
defetch fetchFileProperties(?File) :- 
  ?FileRef := computeIdFromName($Self, "file/$File"),
  [?FileRef].
*)

defetch fetchFileProperties() :-
  [$FileRef].

defetch fetchPolicies() :-
  [$AccessDelegationPolicyRef, $AuthorityDelegationPolicyRef, $DIFCAccessPolicyRef, $DIFCAuthorityPolicyRef].
  (* [accessDelegationPolicies(), authorityDelegationPolicies(), difcAccessPolicies(), difcAuthorityPolicies()]. *)

defguard authorizeFileAccess(?Program, ?User, ?File, ?BearerRef) :-
  spec('Authorize the subject/program to access a file'),
  ?InferenceContext := fetchContext(?BearerRef),
  // (* ?TagSet := fetchFileProperties(?File), *) (* this isn't allowed *)
  ?TagSet := fetchFileProperties(),
  ?PolicySet := fetchPolicies(),
  {
     import($InferenceContext).
     import($TagSet).
     import($PolicySet).
     (* access($Program, $User, $File)? *)
     access($User, $File)?
     (* fileOwner('testing_file', u'0pbWZFJTrmeQ8PTFvpbYOJMfsFuAbPNk8kNDpjo6ETk')? *)
     (* ?X: fileOwner(?File, ?User)? *)
  }.

defetch fetchASet(?Z) :- [?Z].

defguard guardTest(?Z) :-
  spec('Authorize the subject/program to access a file'),
  (* ?TagSet := fetchFileProperties(), *)
  ?X := fetchASet(?Z),
  {
     import($X).
     // import($TagSet).
     ?Y: fileOwner(?File, ?User)?
  }.

(* 
defguard authorizeFileAccess(?Program, ?User, ?File, ?BearerRef) :-
  spec('Authorize the subject/program to access a file'),
  ?InferenceContext := fetchContext(?BearerRef),
  ?TagSet := fetchFileProperties(?File), 
  ?PolicySet := fetchPolicies(),
  {
     import($InferenceContext).
     import($TagSet).
     import($PolicySet).
     access($Program, $User, $File)?
     access($User, $File)?
  }.
*)



defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postFileCreation(?File, ?Owner) :- [createAFile(?File, ?Owner)].

defpost postAllPolicies() :- [accessDelegationPolicies(), authorityDelegationPolicies(), difcAccessPolicies(), difcAuthorityPolicies()].

(* definit postAllPolicies(). *)
definit authorizeFileAccess($1, $2, $3, $4).
(* definit guardTest($1). *)
(* definit postFileCreation($1, $2). *)
(* definit postIdSet($1). *)



(*
defguard uploadAFile(?File, ?Owner) :-
  {
    postFileCreation(?File, ?Owner).
  }.
*)
