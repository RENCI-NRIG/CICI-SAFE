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

defcall postObjectSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?ObjectId) :-
  {
    postObjectSet($ServerJVM, $ServerPrincipal, $Envs, $ObjectId).
  }.

defcall updateObjectSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?Token, ?ObjectId) :-
  {
    updateObjectSet($ServerJVM, $ServerPrincipal, $Envs, $Token, $ObjectId).
  }.

defcall postObjectDelegation(?ServerJVM, ?ServerPrincipal, ?Envs, ?Name, ?Scid, ?MountScid) :-
  {
    postObjectDelegation($ServerJVM, $ServerPrincipal, $Envs, $Name, $Scid, $MountScid).
  }.

delegateObject(?ServerJVM, ?ServerPrincipal, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?Name, ?Scid, ?MountScid) :-
  ?Token := postObjectDelegation(?ServerJVM, ?ServerPrincipal, ?PostEnvs, ?Name, ?Scid, ?MountScid),
  updateObjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token, ?Scid).

defcall queryName(?ServerJVM, ?ServerPrincipal, ?Envs, ?RootScid, ?Name) :-
  {
    queryName($ServerJVM, $ServerPrincipal, $Envs, $RootScid, $Name).
  }.

endorseMA(?GenirootJVM, ?Geniroot, ?MAJVM, ?MA, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postMAEndorsement(?GenirootJVM, ?Geniroot, ?EndorseEnvs, ?MA), 
  updateSubjectSet(?MAJVM, ?MA, ?UpdateEnvs, ?Token),
  postObjectSet(?MAJVM, ?MA, ?UpdateEnvs, "?MA:root").

defcall postPAEndorsement(?ServerJVM, ?ServerPrincipal, ?Envs, ?PA) :-
  {
    postPAEndorsement($ServerJVM, $ServerPrincipal, $Envs, $PA).
  }.

endorsePA(?GenirootJVM, ?Geniroot, ?PAJVM, ?PA, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postPAEndorsement(?GenirootJVM, ?Geniroot, ?EndorseEnvs, ?PA), 
  updateSubjectSet(?PAJVM, ?PA, ?UpdateEnvs, ?Token),
  postObjectSet(?PAJVM, ?PA, ?UpdateEnvs, "?PA:root").

defcall postSAEndorsement(?ServerJVM, ?ServerPrincipal, ?Envs, ?SA) :-
  {
    postSAEndorsement($ServerJVM, $ServerPrincipal, $Envs, $SA).
  }.

endorseSA(?GenirootJVM, ?Geniroot, ?SAJVM, ?SA, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postSAEndorsement(?GenirootJVM, ?Geniroot, ?EndorseEnvs, ?SA),  
  updateSubjectSet(?SAJVM, ?SA, ?UpdateEnvs, ?Token),
  postObjectSet(?SAJVM, ?SA, ?UpdateEnvs, "?SA:root").

defcall postCPEndorsement(?ServerJVM, ?ServerPrincipal, ?Envs, ?CP) :-
  {
    postCPEndorsement($ServerJVM, $ServerPrincipal, $Envs, $CP).
  }.

endorseCP(?GenirootJVM, ?Geniroot, ?CPJVM, ?CP, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postCPEndorsement(?GenirootJVM, ?Geniroot, ?EndorseEnvs, ?CP),  
  updateSubjectSet(?CPJVM, ?CP, ?UpdateEnvs, ?Token),
  postObjectSet(?CPJVM, ?CP, ?UpdateEnvs, "?CP:root").

defcall postPIEndorsement(?ServerJVM, ?ServerPrincipal, ?Envs, ?PI) :-
  {
    postPIEndorsement($ServerJVM, $ServerPrincipal, $Envs, $PI).
  }.

endorsePI(?MAJVM, ?MA, ?PIJVM, ?PI, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postPIEndorsement(?MAJVM, ?MA, ?EndorseEnvs, ?PI),  
  updateSubjectSet(?PIJVM, ?PI, ?UpdateEnvs, ?Token),
  delegateObject(?MAJVM, ?MA, ?PIJVM, ?PI, ?UpdateEnvs, ?UpdateEnvs, "PIname?PI", "?PI:subject", "?MA:root").

defcall postUserEndorsement(?ServerJVM, ?ServerPrincipal, ?Envs, ?User) :-
  {
    postUserEndorsement($ServerJVM, $ServerPrincipal, $Envs, $User).
  }.

endorseUser(?MAJVM, ?MA, ?UserJVM, ?User, ?EndorseEnvs, ?UpdateEnvs) :-
  ?Token := postUserEndorsement(?MAJVM, ?MA, ?EndorseEnvs, ?User),  
  updateSubjectSet(?UserJVM, ?User, ?UpdateEnvs, ?Token),
  delegateObject(?MAJVM, ?MA, ?UserJVM, ?User, ?UpdateEnvs, ?UpdateEnvs, "Username?User", "?User:subject", "?MA:root").

defcall createProject(?ServerJVM, ?ServerPrincipal, ?Envs) :-
  {
    createProject($ServerJVM, $ServerPrincipal, $Envs).
  }.

defcall postProjectSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?SubjectId, ?ProjectId, ?ProjectMembershipRef) :-
  {
    postProjectSet($ServerJVM, $ServerPrincipal, $Envs, $SubjectId, $ProjectId, $ProjectMembershipRef).
  }.

defcall postUserGroupMemberSet(?ServerJVM, ?ServerPrincipal, ?Envs) :-
  {
    postUserGroupMemberSet($ServerJVM, $ServerPrincipal, $Envs).
  }.

defcall postProjectMemberSet(?ServerJVM, ?ServerPrincipal, ?Envs) :-
  {
    postProjectMemberSet($ServerJVM, $ServerPrincipal, $Envs).
  }.

queryThenCreateProject(?PAJVM, ?PA, ?SubjectJVM, ?SubjectId, ?QueryEnvs, ?PostEnvs, ?UpdateEnvs, ?ProjectId, ?ProjectMembershipRef) :-
  createProject(?PAJVM, ?PA, ?QueryEnvs),
  ?Token := postProjectSet(?PAJVM, ?PA, ?PostEnvs, ?SubjectId, ?ProjectId, ?ProjectMembershipRef),
  updateSubjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token),
  ?PName := objectFromScid(?ProjectId),
  delegateObject(?PAJVM, ?PA, ?PAJVM, ?PA, ?UpdateEnvs, ?UpdateEnvs, "Projectname?PName", ?ProjectId, "?PA:root").


defcall postProjectMembership(?PIJVM, ?PI, ?Envs, ?SubjectId, ?ProjectId, ?Delegatable) :-
  {
    postProjectMembershipDelegation($PIJVM, $PI, $Envs, $SubjectId, $ProjectId, $Delegatable). 
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

defcall postSliceSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?SubjectId, ?SliceId, ?ProjectId, ?SliceControlPrivRef) :-
  {
    postSliceSet($ServerJVM, $ServerPrincipal, $Envs, $SubjectId, $SliceId, $ProjectId, $SliceControlPrivRef). 
  }.

queryThenCreateSlice(?SAJVM, ?SA, ?SubjectJVM, ?SubjectId, ?QueryEnvs, ?PostEnvs, ?UpdateEnvs, ?ProjectId,  ?SliceId, ?SliceControlPrivRef) :- 
  createSlice(?SAJVM, ?SA, ?QueryEnvs, ?ProjectId), 
  ?Token := postSliceSet(?SAJVM, ?SA, ?PostEnvs, ?SubjectId, ?SliceId, ?ProjectId, ?SliceControlPrivRef),
  updateSubjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token),
  ?SName := objectFromScid(?SliceId),
  delegateObject(?SAJVM, ?SA, ?SAJVM, ?SA, ?UpdateEnvs, ?UpdateEnvs, "Slicename?SName", ?SliceId, "?SA:root").


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

defcall postSliverSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?SliverId, ?SliceId) :-
  {
    postSliverSet($ServerJVM, $ServerPrincipal, $Envs, $SliverId, $SliceId).
  }.

defcall postStitchportSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?SliverId, ?SliceId, ?ZoneId, ?VlanTag) :-
  {
    postStitchportSet($ServerJVM, $ServerPrincipal, $Envs, $SliverId, $SliceId, $ZoneId, $VlanTag).
  }.

defcall postSliverAcl(?ServerJVM, ?ServerPrincipal, ?Envs, ?SliverId, ?GroupId) :-
  {
    postSliverAcl($ServerJVM, $ServerPrincipal, $Envs, $SliverId, $GroupId).
  }.

queryThenCreateStitchport(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?SliceId, ?ZoneId, ?VlanTag) :- 
  createSliver(?CPJVM, ?CP, ?QueryEnvs, ?SliceId), 
  ?Token := postStitchportSet(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?SliceId, ?ZoneId, ?VlanTag),
  postSliverAcl(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?SliceId).

queryThenCreateNamedStitchport(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?SliceId, ?ZoneId, ?VlanTag, ?SliceId2) :- 
  createSliver(?CPJVM, ?CP, ?QueryEnvs, ?SliceId), 
  ?Token := postStitchportSet(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?SliceId, ?ZoneId, ?VlanTag),
  postSliverAcl(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?SliceId2),
  ?SliceName := objectFromScid(?SliceId),
  ?SliverName := objectFromScid(?SliverId),
  delegateObject(?CPJVM, ?CP, ?CPJVM, ?CP, ?QueryEnvs, ?QueryEnvs, "CPSlivername?SliverName", "?CP:?SliverName", "?CP:?SliceName").

queryThenCreateSliver(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?SliceId) :- 
  createSliver(?CPJVM, ?CP, ?QueryEnvs, ?SliceId), 
  ?Token := postSliverSet(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?SliceId),
  postSliverAcl(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?SliceId).

defcall addSliverAcl(?ServerJVM, ?ServerPrincipal, ?Envs, ?SliverId) :-
  {
    addSliverAcl($ServerJVM, $ServerPrincipal, $Envs, $SliverId).
  }.

queryThenInstallSliverAcl(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?GroupId) :- 
  addSliverAcl(?CPJVM, ?CP, ?QueryEnvs, ?SliverId), 
  postSliverAcl(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?GroupId).

defcall accessSliver(?ServerJVM, ?ServerPrincipal, ?Envs, ?SliverId) :-
  {
    accessSliver($ServerJVM, $ServerPrincipal, $Envs, $SliverId).
  }.

defcall stitchSlivers(?ServerJVM, ?ServerPrincipal, ?Envs, ?SrcSliverId, ?PeerSliverId) :-
  {
    stitchSlivers($ServerJVM, $ServerPrincipal, $Envs, $SrcSliverId, $PeerSliverId).
  }.

lookupThenStitch(?CPJVM, ?CP, ?Envs, ?SrcSliverId, ?PeerSliverId, ?PeerSliceId) :-
  ?SliverName := objectFromScid(?PeerSliverId),
  ?SliceName := objectFromScid(?PeerSliceId),
  queryName(?CPJVM, ?CP, ?Envs, "?CP:root", "CPSlicename?SliceName/CPSlivername?SliverName"),
  stitchSlivers(?CPJVM, ?CP, ?Envs, ?SrcSliverId, ?PeerSliverId).
  

stitchIntraSlice(?CPJVM, ?CP, ?QueryEnvs, ?SliverId1, ?SliverId2, ?SliceId, ?SliverId, ?ZoneId, ?VlanTag) :- 
  queryThenCreateStitchport(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?SliceId, ?ZoneId, ?VlanTag), 
  stitchSlivers(?CPJVM, ?CP, ?QueryEnvs, ?SliverId1, ?SliverId),
  stitchSlivers(?CPJVM, ?CP, ?QueryEnvs, ?SliverId, ?SliverId2).


defcall postZoneSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?ZoneId) :-
  {
    postZoneSet($ServerJVM, $ServerPrincipal, $Envs, $ZoneId).
  }.

defcall postAdjacentCP(?ServerJVM, ?ServerPrincipal, ?Envs, ?AdjacentCP) :-
  {
    updateAdjacentCPList($ServerJVM, $ServerPrincipal, $Envs, $AdjacentCP).
  }.
