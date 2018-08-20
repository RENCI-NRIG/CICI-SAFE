
//Used defcall
//postIdSet
//postSubjectSet
//updateSubjectSet
//postProjectSet
//postStandarSliceControlSet
//postSliceSet

//begin of mp_geni

defcall postIdSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?CN) :-
  {
    postIdSet($ServerJVM, $ServerPrincipal, $Envs, $CN).
  }.

defcall postSubjectSet(?ServerJVM, ?ServerPrincipal, ?Envs) :-
  {
    postSubjectSet($ServerJVM, $ServerPrincipal, $Envs).
  }.

defcall updateSubjectSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?Token) :-
  {
    updateSubjectSet($ServerJVM, $ServerPrincipal, $Envs, $Token).
  }.

defcall postMAEndorsement(?ServerJVM, ?ServerPrincipal, ?Envs, ?MA) :-
  {
    postMAEndorsement($ServerJVM, $ServerPrincipal, $Envs, $MA).
  }.

endorseMA(?GenirootJVM, ?Geniroot, ?MAJVM, ?MA, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postMAEndorsement(?GenirootJVM, ?Geniroot, ?EndorseEnvs, ?MA), 
  updateSubjectSet(?MAJVM, ?MA, ?UpdateEnvs, ?Token).

defcall postPAEndorsement(?ServerJVM, ?ServerPrincipal, ?Envs, ?PA) :-
  {
    postPAEndorsement($ServerJVM, $ServerPrincipal, $Envs, $PA).
  }.

endorsePA(?GenirootJVM, ?Geniroot, ?PAJVM, ?PA, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postPAEndorsement(?GenirootJVM, ?Geniroot, ?EndorseEnvs, ?PA), 
  updateSubjectSet(?PAJVM, ?PA, ?UpdateEnvs, ?Token).

defcall postSAEndorsement(?ServerJVM, ?ServerPrincipal, ?Envs, ?SA) :-
  {
    postSAEndorsement($ServerJVM, $ServerPrincipal, $Envs, $SA).
  }.

endorseSA(?GenirootJVM, ?Geniroot, ?SAJVM, ?SA, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postSAEndorsement(?GenirootJVM, ?Geniroot, ?EndorseEnvs, ?SA),  
  updateSubjectSet(?SAJVM, ?SA, ?UpdateEnvs, ?Token).

defcall postCPEndorsement(?ServerJVM, ?ServerPrincipal, ?Envs, ?CP) :-
  {
    postCPEndorsement($ServerJVM, $ServerPrincipal, $Envs, $CP).
  }.

endorseCP(?GenirootJVM, ?Geniroot, ?CPJVM, ?CP, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postCPEndorsement(?GenirootJVM, ?Geniroot, ?EndorseEnvs, ?CP),  
  updateSubjectSet(?CPJVM, ?CP, ?UpdateEnvs, ?Token).

defcall postPIEndorsement(?ServerJVM, ?ServerPrincipal, ?Envs, ?PI) :-
  {
    postPIEndorsement($ServerJVM, $ServerPrincipal, $Envs, $PI).
  }.

endorsePI(?MAJVM, ?MA, ?PIJVM, ?PI, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postPIEndorsement(?MAJVM, ?MA, ?EndorseEnvs, ?PI),  
  updateSubjectSet(?PIJVM, ?PI, ?UpdateEnvs, ?Token).

defcall postUserEndorsement(?ServerJVM, ?ServerPrincipal, ?Envs, ?User) :-
  {
    postUserEndorsement($ServerJVM, $ServerPrincipal, $Envs, $User).
  }.

endorseUser(?MAJVM, ?MA, ?UserJVM, ?User, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postUserEndorsement(?MAJVM, ?MA, ?EndorseEnvs, ?User),  
  updateSubjectSet(?UserJVM, ?User, ?UpdateEnvs, ?Token).

defcall createProject(?ServerJVM, ?ServerPrincipal, ?Envs) :-
  {
    createProject($ServerJVM, $ServerPrincipal, $Envs).
  }.

defcall postProjectSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?SubjectId, ?ProjectId, ?ProjectMembershipRef) :-
  {
    postProjectSet($ServerJVM, $ServerPrincipal, $Envs, $SubjectId, $ProjectId, $ProjectMembershipRef).
  }.

defcall postMemberSet(?ServerJVM, ?ServerPrincipal, ?Envs) :-
  {
    postMemberSet($ServerJVM, $ServerPrincipal, $Envs).
  }.

queryThenCreateProject(?PAJVM, ?PA, ?SubjectJVM, ?SubjectId, ?QueryEnvs, ?PostEnvs, ?UpdateEnvs, ?ProjectId, ?ProjectMembershipRef) :-
  createProject(?PAJVM, ?PA, ?QueryEnvs),
  ?Token := postProjectSet(?PAJVM, ?PA, ?PostEnvs, ?SubjectId, ?ProjectId, ?ProjectMembershipRef),
  updateSubjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token).

defcall postProjectMembership(?PIJVM, ?PI, ?Envs, ?SubjectId, ?ProjectId, ?Delegatable) :-
  {
    postProjectMembership($PIJVM, $PI, $Envs, $SubjectId, $ProjectId, $Delegatable). 
  }.

delegateProjectMembership(?PIJVM, ?PI, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?ProjectId, ?Delegatable) :-
  ?Token := postProjectMembership(?PIJVM, ?PI, ?PostEnvs, ?SubjectId, ?ProjectId, ?Delegatable),
  updateSubjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token).

defcall postStandardSliceControlSet(?ServerJVM, ?ServerPrincipal, ?Envs) :-
  {
    postStandardSliceControlSet($ServerJVM, $ServerPrincipal, $Envs).
  }.

defcall postStandardSliceDefaultPrivilegeSet(?ServerJVM, ?ServerPrincipal, ?Envs) :-
  {
    postStandardSliceDefaultPrivilegeSet($ServerJVM, $ServerPrincipal, $Envs).
  }.

defcall createSlice(?ServerJVM, ?ServerPrincipal, ?Envs, ?ProjectId) :-
  {
    createSlice($ServerJVM, $ServerPrincipal, $Envs, $ProjectId).
  }.

delegateProjectThenQuery(?UserJVM, ?User, ?SubjectJVM, ?SubjectId, ?SAJVM, ?SA, ?PostEnvs, ?UpdateEnvs, ?QueryEnvs, ?ProjectId, ?Delegatable) :-
  ?Res := delegateProjectMembership(?UserJVM, ?User, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?ProjectId, ?Delegatable),
  createSlice(?SAJVM, ?SA, ?QueryEnvs, ?ProjectId).

defcall postSliceSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?SubjectId, ?SliceId, ?ProjectId, ?SliceControlRef, ?SlicePrivRef) :-
  {
    postSliceSet($ServerJVM, $ServerPrincipal, $Envs, $SubjectId, $SliceId, $ProjectId, $SliceControlRef, $SlicePrivRef). 
  }.

queryThenCreateSlice(?SAJVM, ?SA, ?SubjectJVM, ?SubjectId, ?QueryEnvs, ?PostEnvs, ?UpdateEnvs, ?ProjectId,  ?SliceId, ?SliceControlRef, ?SlicePrivRef) :- 
  createSlice(?SAJVM, ?SA, ?QueryEnvs, ?ProjectId), 
  ?Token := postSliceSet(?SAJVM, ?SA, ?PostEnvs, ?SubjectId, ?SliceId, ?ProjectId, ?SliceControlRef, ?SlicePrivRef),
  updateSubjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token). 

defcall postSliceControl(?UserJVM, ?User, ?Envs, ?SubjectId, ?SliceId, ?Delegatable) :-
  {
    postSliceControl($UserJVM, $User, $Envs, $SubjectId, $SliceId, $Delegatable). 
  }.

delegateSliceControl(?UserJVM, ?User, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?SliceId, ?Delegatable) :-
  ?Token := postSliceControl(?UserJVM, ?User, ?PostEnvs, ?SubjectId, ?SliceId, ?Delegatable),
  updateSubjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token).

defcall createSliver(?ServerJVM, ?ServerPrincipal, ?Envs, ?SliceId) :-
  {
    createSliver($ServerJVM, $ServerPrincipal, $Envs, $SliceId).
  }.

delegateSliceThenQuery(?UserJVM, ?User, ?SubjectJVM, ?SubjectId, ?CPJVM, ?CP, ?PostEnvs, ?UpdateEnvs, ?QueryEnvs, ?SliceId, ?Delegatable) :-
  ?Res := delegateSliceControl(?UserJVM, ?User, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?SliceId, ?Delegatable),
  createSliver(?CPJVM, ?CP, ?QueryEnvs, ?SliceId).

//end of mp_geni

defcall postTagSet(?ServerJVM,?ServerPrincipal,?Envs,?Tag):-
  {
    postTagSet($ServerJVM,$ServerPrincipal,$Envs,$Tag).
  }.

defcall updateTagSet(?ServerJVM,?ServerPrincipal,?Envs,?Token,?Tag):-
  {
    updateTagSet($ServerJVM,$ServerPrincipal,$Envs,$Token,$Tag).
  }.

defcall postLinkTagSetToProject(?ServerJVM,?ServerPrincipal,?Envs, ?ProjectId,?TagSetRef):-
  {
    postLinkTagSetToProject($ServerJVM,$ServerPrincipal,$Envs,$ProjectId,$TagSetRef).
  }.

defcall postLinkTagSetToSlice(?ServerJVM,?ServerPrincipal,?Envs, ?SliceId,?TagSetRef):-
  {
    postLinkTagSetToSlice($ServerJVM,$ServerPrincipal,$Envs,$SliceId,$TagSetRef).
  }.

defcall postObjectTagSet(?ServerJVM,?ServerPrincipal,?Envs, ?ObjectId,?Tag):-
  {
    postObjectTagSet($ServerJVM,$ServerPrincipal,$Envs,$ObjectId,$Tag).
  }.

defcall postUpdateObjectTagSet(?ServerJVM,?ServerPrincipal,?Envs, ?ObjectId,?Tag,?Token):-
  {
    postUpdateObjectTagSet($ServerJVM,$ServerPrincipal,$Envs,$ObjectId,$Tag,$Token).
  }.

defcall postGrantTagPriv(?ServerJVM,?ServerPrincipal,?Envs,?Tag,?User, ?Delegatable):-
  {
    postGrantTagPriv($ServerJVM,$ServerPrincipal,$Envs,$Tag,$User, ?Delegatable).
  }.

defcall postAssignTag(?ServerJVM,?ServerPrincipal,?Envs,?Slice, ?Tag) :-
  {
    postAssignTag($ServerJVM,$ServerPrincipal,$Envs,$Slice,$Tag).
  }.

defcall postAclEntrySet(?ServerJVM,?ServerPrincipal,?Envs) :-
  {
    postAclEntrySet($ServerJVM,$ServerPrincipal,$Envs).
  }.

defcall postUserAclEntry(?ServerJVM,?ServerPrincipal,?Envs,?User) :-
  {
    postUserAclEntry($ServerJVM,$ServerPrincipal,$Envs,$User).
  }.

defcall postUserTagAclEntry(?ServerJVM,?ServerPrincipal,?Envs,?Tag) :-
  {
    postUserTagAclEntry($ServerJVM,$ServerPrincipal,$Envs,$Tag).
  }.

defcall postTagAclEntry(?ServerJVM,?ServerPrincipal,?Envs,?Tag) :-
  {
    postTagAclEntry($ServerJVM,$ServerPrincipal,$Envs,$Tag).
  }.


defcall postProjectAuthorityAcl(?ServerJVM,?ServerPrincipal,?Envs,?PA) :-
  {
    postProjectAuthorityAcl($ServerJVM,$ServerPrincipal,$Envs,$PA).
  }.

defcall postStitchPolicy(?ServerJVM,?ServerPrincipal,?Envs) :-
  {
    postStitchPolicy($ServerJVM,$ServerPrincipal,$Envs).
  }.

defcall postTagPrivilegePolicy(?ServerJVM,?ServerPrincipal,?Envs) :-
  {
    postTagPrivilegePolicy($ServerJVM,$ServerPrincipal,$Envs).
  }.

controlPrivilegeDelegation(?FromJVM,?FromPrincipal,?ToJVM,?ToPrincipal,?GrantEnvs,?UpdateEnvs,?Slice,?Priv,?Delegatable):-
  ?Token := postTagDelegation(?FromJVM,?FromPrincipal,?GrantEnvs,?Slice,?Priv,?Delegatable),
  updateSubjectSet(?ToJVM,?ToPrincipal,?UpdateEnvs,?Token).

tagPrivDelegation(?FromJVM,?FromPrincipal,?ToJVM,?ToPrincipal,?GrantEnvs,?UpdateEnvs,?Tag,?Delegatable):-
  ?Token := postGrantTagPriv(?FromJVM,?FromPrincipal,?GrantEnvs,?ToPrincipal,?Tag,?Delegatable),
  updateTagSet(?ToJVM,?ToPrincipal,?UpdateEnvs,?Token,?Tag).

assignTagtoObject(?FromJVM,?FromPrincipal,?ToJVM,?ToPrincipal,?GrantEnvs,?UpdateEnvs,?ObjectId,?Tag):-
  ?Token := postAssignTag(?FromJVM,?FromPrincipal,?GrantEnvs,?ObjectId,?Tag),
  postUpdateObjectTagSet(?ToJVM,?ToPrincipal,?UpdateEnvs,?ObjectId,?Tag,?Token).

defcall authorizeStitch(?ServerJVM, ?ServerPrincipal, ?Envs, ?User, ?Slice) :-
  {
    authorizeStitch($ServerJVM,$ServerPrincipal,$Envs,$User,$Slice).
  }.

