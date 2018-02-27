defcall postIdSet(?ServerJVM,?ServerPrincipal,?Envs,?CN):-
  {
    postIdSet($ServerJVM,$ServerPrincipal,$Envs,$CN).
  }.

defcall postSubjectSet(?ServerJVM,?ServerPrincipal,?Envs):-
  {
    postSubjectSet($ServerJVM,$ServerPrincipal,$Envs).
  }.

defcall updateSubjectSet(?ServerJVM,?ServerPrincipal,?Envs,?Token):-
  {
    updateSubjectSet($ServerJVM,$ServerPrincipal,$Envs,$Token).
  }.

defcall postTagSet(?ServerJVM,?ServerPrincipal,?Envs,?Tag):-
  {
    postTagSet($ServerJVM,$ServerPrincipal,$Envs,$Tag).
  }.

defcall postTagAccessPolicy(?ServerJVM,?ServerPrincipal,?Envs):-
  {
    postTagAccessPolicy($ServerJVM,$ServerPrincipal,$Envs).
  }.


defcall updateTagSet(?ServerJVM,?ServerPrincipal,?Envs,?Token,?Tag):-
  {
    updateTagSet($ServerJVM,$ServerPrincipal,$Envs,$Token,$Tag).
  }.

defcall postTagAccess(?ServerJVM,?ServerPrincipal,?Envs,?Tag,?User,?Delegatable) :- 
  {
    postTagAccess($ServerJVM,$ServerPrincipal,$Envs,$Tag,$User,$Delegatable).
  }.

defcall postTagDelegation(?ServerJVM,?ServerPrincipal,?Envs,?Tag,?ToTag,?Delegatable):-
  {
    postTagDelegation($ServerJVM,$ServerPrincipal,$Envs,$Tag,$ToTag,$Delegatable).
  }.

defcall postNetworkTag(?ServerJVM,?ServerPrincipal,?Envs,?Network,?Tag):-
  {
    postNetworkTag($ServerJVM,$ServerPrincipal,$Envs,$Network,$Tag).
  }.

defcall postTagAccessDelegation(?ServerJVM,?ServerPrincipal,?Envs,?Tag,?User,?Delegatable):-
  {
    postTagAccessDelegation($ServerJVM,$ServerPrincipal,$Envs,$Tag,$User,$Delegatable).
  }.

grantTagAccess(?FromJVM,?FromPrincipal,?ToJVM,?ToPrincipal,?GrantEnvs,?UpdateEnvs,?Tag,?Delegatable):-
  ?Token := postTagAccess(?FromJVM,?FromPrincipal,?GrantEnvs,?Tag,?ToPrincipal,?Delegatable),
  updateTagSet(?ToJVM,?ToPrincipal,?UpdateEnvs,?Token,?Tag),
  updateSubjectSet(?ToJVM,?ToPrincipal,?UpdateEnvs,?Token).


tagAccessDelegation(?FromJVM,?FromPrincipal,?ToJVM,?ToPrincipal,?GrantEnvs,?UpdateEnvs,?Tag,?Delegatable):-
  ?Token := postTagAccessDelegation(?FromJVM,?FromPrincipal,?GrantEnvs,?Tag,?ToPrincipal,?Delegatable),
  updateTagSet(?ToJVM,?ToPrincipal,?UpdateEnvs,?Token,?Tag),
  updateSubjectSet(?ToJVM,?ToPrincipal,?UpdateEnvs,?Token).

tagDelegation(?FromJVM,?FromPrincipal,?ToJVM,?ToPrincipal,?GrantEnvs,?UpdateEnvs,?Tag,?ToTag,?Delegatable):-
  ?Token := postTagDelegation(?FromJVM,?FromPrincipal,?GrantEnvs,?Tag,?ToTag,?Delegatable),
  updateTagSet(?ToJVM,?ToPrincipal,?UpdateEnvs,?Token,?ToTag).

defcall checkTagAccess(?ServerJVM,?ServerPrincipal,?Envs,?User,?Tag):-
  {
    checkTagAccess($ServerJVM,$ServerPrincipal,$Envs,$User,$Tag).
  }.

defcall checkNetworkAccess(?ServerJVM,?ServerPrincipal,?Envs,?User,?Network):-
  {
    checkNetworkAccess($ServerJVM,$ServerPrincipal,$Envs,$User,$Network).
  }.


