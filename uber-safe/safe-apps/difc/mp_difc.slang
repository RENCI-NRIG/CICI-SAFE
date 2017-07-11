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

defcall postTagSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?Tag) :-
  {
    postTagSet($ServerJVM, $ServerPrincipal, $Envs, $Tag).
  }.

defcall updateTagSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?Token, ?Tag) :-
  {
    updateTagSet($ServerJVM, $ServerPrincipal, $Envs, $Token, $Tag).
  }.

defcall postTagAccess(?ServerJVM, ?ServerPrincipal, ?Envs, ?TagId, ?SubjectId, ?Delegatable) :-
  {
    postTagAccess($ServerJVM, $ServerPrincipal, $Envs, $TagId, $SubjectId, $Delegatable). 
  }. 

grantTagAccess(?TAJVM, ?TA, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?TagId, ?Delegatable) :- 
  ?Token := postTagAccess(?TAJVM, ?TA, ?PostEnvs, ?TagId, ?SubjectId, ?Delegatable),
  updateSubjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token).

defcall postTagDelegation(?ServerJVM, ?ServerPrincipal, ?Envs, ?Tag, ?ToTag, ?Delegatable) :-
  {
    postTagDelegation($ServerJVM, $ServerPrincipal, $Envs, $Tag, $ToTag, $Delegatable).
  }.

// ?TA := rootPrincipal(?TagId)
// ?SubjectId := rootPrincipal(?ToTagId)
nestTag(?TAJVM, ?TA, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?TagId, ?ToTagId, ?Delegatable) :- 
  ?Token := postTagDelegation(?TAJVM, ?TA, ?PostEnvs, ?TagId, ?ToTagId, ?Delegatable),
  updateTagSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token, ?ToTagId).

defcall labelFile(?ServerJVM, ?ServerPrincipal, ?Envs, ?File, ?Tag) :-
  {
    postFileTag($ServerJVM, $ServerPrincipal, $Envs, $File, $Tag).
  }.

  
defcall postTagAccessDelegation(?ServerJVM, ?ServerPrincipal, ?Envs, ?Tag, ?User, ?Delegatable) :-
  {
    postTagAccessDelegation($ServerJVM, $ServerPrincipal, $Envs, $Tag, $User, $Delegatable).
  }.

delegateTagAccess(?ServerJVM, ?ServerPrincipal, ?SubjectJVM, ?SubjectId, ?PostEnvs, ?UpdateEnvs, ?TagId, ?Delegatable) :- 
  ?Token := postTagAccessDelegation(?ServerJVM, ?ServerPrincipal, ?PostEnvs, ?TagId, ?SubjectId, ?Delegatable),
  updateSubjectSet(?SubjectJVM, ?SubjectId, ?UpdateEnvs, ?Token).

defcall checkFileAccess(?ServerJVM, ?ServerPrincipal, ?Envs, ?User, ?File) :-
  {
    checkFileAccess($ServerJVM, $ServerPrincipal, $Envs, $User, $File).
  }.

defcall checkTagAccess(?ServerJVM, ?ServerPrincipal, ?Envs, ?User, ?Tag) :-
  {
    checkTagAccess($ServerJVM, $ServerPrincipal, $Envs, $User, $Tag).
  }.
