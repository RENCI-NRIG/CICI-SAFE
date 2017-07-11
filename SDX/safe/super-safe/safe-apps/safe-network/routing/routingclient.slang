defcall postIdSet(?ServerJVM,?ServerPrincipal,?Envs,?CN):-
  {
    postIdSet($ServerJVM,$ServerPrincipal,$Envs,$CN).
  }.

defcall postSubjectSet(?ServerJVM,?ServerPrincipal,?Envs):-
  {
    postSubjectSet($ServerJVM,$ServerPrincipal,$Envs).
  }.

defcall postRoutingPolicy(?ServerJVM,?ServerPrincipal,?Envs):-
  {
    postRoutingPolicy($ServerJVM,$ServerPrincipal,$Envs).
  }.

defcall postOwnPrefixPolicy(?ServerJVM,?ServerPrincipal,?Envs):-
  {
    postOwnPrefixPolicy($ServerJVM,$ServerPrincipal,$Envs).
  }.

defcall postAdvertise(?ServerJVM,?ServerPrincipal,?Envs,?DstIP,?Path,?Target):-
  {
    postAdvertise($ServerJVM,$ServerPrincipal,$Envs,$DstIP,$Path,$Target).
  }.

defcall postInitRoute(?ServerJVM,?ServerPrincipal,?Envs,?DstIP,?Path,?Target) :- 
  {
    postInitRoute($ServerJVM,$ServerPrincipal,$Envs,$DstIP,$Path,$Target).
  }.

defcall postPathToken(?ServerJVM,?ServerPrincipal,?Envs,?Token,?Path):-
  {
    postPathToken($ServerJVM,$ServerPrincipal,$Envs,$Token,$Path).
  }.

advertisePath(?SpeakerJVM,?Speaker,?TargetJVM,?Target,?AdvertiseEnvs,?UpdateEnvs,?DstIP,?Path):-
  ?Token := postAdvertise(?SpeakerJVM,?Speaker,?AdvertiseEnvs,?DstIP,?Path,?Target),
  postPathToken(?TargetJVM,?Target,?UpdateEnvs,?Token,?Path).

initPath(?SpeakerJVM,?Speaker,?TargetJVM,?Target,?AdvertiseEnvs,?UpdateEnvs,?DstIP,?Path):-
  ?Token := postInitRoute(?SpeakerJVM,?Speaker,?AdvertiseEnvs,?DstIP,?Path,?Target),
  postPathToken(?TargetJVM,?Target,?UpdateEnvs,?Token,?Path).


defcall postIPAllocate(?ServerJVM,?ServerPrincipal,?Envs,?Prcpl,?Prefix):-
  {
    postIPAllocate($ServerJVM,$ServerPrincipal,$Envs,$Prcpl,$Prefix).
  }.

defcall postDlgToken(?ServerJVM,?ServerPrincipal,?Envs,?Token):-
  {
    postDlgToken($ServerJVM,$ServerPrincipal,$Envs,$Token).
  }.

allocatePrefix(?RpkiRootJVM,?RpkiRoot,?IRJVM,?IR,?AllocateEnvs,?UpdateEnvs,?Prefix):-
  ?Token := postIPAllocate(?RpkiRootJVM,?RpkiRoot,?AllocateEnvs,?IR,?Prefix),
  postDlgToken(?IRJVM,?IR,?UpdateEnvs,?Token).

defcall postMakeIPTokenSet(?ServerJVM,?ServerPrincipal,?Envs):-
  {
    postMakeIPTokenSet($ServerJVM,$ServerPrincipal,$Envs).
  }.



