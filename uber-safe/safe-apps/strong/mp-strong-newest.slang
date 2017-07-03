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

defcall postGroupSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?Group) :-
  {
    postGroupSet($ServerJVM, $ServerPrincipal, $Envs, $Group).
  }.

defcall updateGroupSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?Token, ?Group) :-
  {
    updateGroupSet($ServerJVM, $ServerPrincipal, $Envs, $Token, $Group).
  }.

defcall postGroupMember(?ServerJVM, ?ServerPrincipal, ?Envs, ?GroupId, ?SubjectId, ?Delegatable) :-
  {
    postGroupMember($ServerJVM, $ServerPrincipal, $Envs, $GroupId, $SubjectId, $Delegatable). 
  }. 

groupMember(?GAJVM, ?GA, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?GroupId, ?Delegatable) :- 
  ?Token := postGroupMember(?GAJVM, ?GA, ?PostEnvs, ?GroupId, ?SubjectId, ?Delegatable),
  updateSubjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token).

defcall postGroupDelegation(?ServerJVM, ?ServerPrincipal, ?Envs, ?GroupId, ?ToGroupId, ?Delegatable) :-
  {
    postGroupDelegation($ServerJVM, $ServerPrincipal, $Envs, $GroupId, $ToGroupId, $Delegatable).
  }.

// ?GA := rootPrincipal(?GroupId)
// ?SubjectId := rootPrincipal(?ToGroupId)
nestGroup(?GAJVM, ?GA, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?GroupId, ?ToGroupId, ?Delegatable) :- 
  ?Token := postGroupDelegation(?GAJVM, ?GA, ?PostEnvs, ?GroupId, ?ToGroupId, ?Delegatable),
  updateGroupSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token, ?ToGroupId).
  
defcall postMembershipDelegation(?ServerJVM, ?ServerPrincipal, ?Envs, ?SubjectId, ?GroupId, ?Delegatable) :-
  {
    postMembershipDelegation($ServerJVM, $ServerPrincipal, $Envs, $SubjectId, $GroupId, $Delegatable).
  }.

delegateMembership(?ServerJVM, ?ServerPrincipal, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?GroupId, ?Delegatable) :- 
  ?Token := postMembershipDelegation(?ServerJVM, ?ServerPrincipal, ?PostEnvs, ?SubjectId, ?GroupId, ?Delegatable),
  updateSubjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token).

defcall postObjectSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?ObjectId) :-
  {
    postObjectSet($ServerJVM, $ServerPrincipal, $Envs, $ObjectId).
  }.

defcall updateObjectSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?Token, ?ObjectId) :-
  {
    updateObjectSet($ServerJVM, $ServerPrincipal, $Envs, $Token, $ObjectId).
  }.

defcall postNameDelegation(?ServerJVM, ?ServerPrincipal, ?Envs, ?Name, ?ChildScid, ?ParentScid) :-
  {
    postNameDelegation($ServerJVM, $ServerPrincipal, $Envs, $Name, $ChildScid, $ParentScid).
  }.

delegateName(?ServerJVM, ?ServerPrincipal, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?Name, ?ChildScid, ?ParentScid) :- 
  ?Token := postNameDelegation(?ServerJVM, ?ServerPrincipal, ?PostEnvs, ?Name, ?ChildScid, ?ParentScid),
  updateObjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token, ?ChildScid).

defcall postDirectoryAccess(?ServerJVM, ?ServerPrincipal, ?Envs, ?GroupId, ?Scid) :-
  {
    postDirectoryAccess($ServerJVM, $ServerPrincipal, $Envs, $GroupId, $Scid).
  }.

defcall queryMembership(?ServerJVM, ?ServerPrincipal, ?Envs, ?GroupId, ?SubjectId) :-
  {
    queryMembership($ServerJVM, $ServerPrincipal, $Envs, $GroupId, $SubjectId).
  }.

defcall queryName(?ServerJVM, ?ServerPrincipal, ?Envs, ?Name) :-
  {
    queryName($ServerJVM, $ServerPrincipal, $Envs, $Name).
  }.

delegateNameThenQuery(?ServerJVM, ?ServerPrincipal, ?SubjectJVM, ?SubjectId, ?QueryJVM, ?QueryPrincipal, ?PostEnvs, ?UpdateEnvs, ?QueryEnvs, ?Name, ?ChildScid, ?ParentScid, ?QueryName) :- 
  delegateName(?ServerJVM, ?ServerPrincipal, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?Name, ?ChildScid, ?ParentScid),
  queryName(?QueryJVM, ?QueryPrincipal, ?QueryEnvs, ?QueryName).

defcall accessNamedObjectSingleContext(?ServerJVM, ?ServerPrincipal, ?Envs, ?SubjectId, ?Name) :-
  {
    accessNamedObjectSingleContext($ServerJVM, $ServerPrincipal, $Envs, $SubjectId, $Name).
  }.

defcall accessNamedObject(?ServerJVM, ?ServerPrincipal, ?Envs, ?SubjectId, ?Name) :-
  {
    accessNamedObject($ServerJVM, $ServerPrincipal, $Envs, $SubjectId, $Name).
  }.
