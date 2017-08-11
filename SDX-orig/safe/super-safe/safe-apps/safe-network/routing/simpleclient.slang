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

defcall postAdvertise(?ServerJVM,?ServerPrincipal,?Envs,?DstIP,?Path,?Target,?Src,?Len):-
  {
    postAdvertise($ServerJVM,$ServerPrincipal,$Envs,$DstIP,$Path,$Target,$Src,$Len).
  }.

defcall postInitRoute(?ServerJVM,?ServerPrincipal,?Envs,?DstIP,?Path,?Target,?Len) :- 
  {
    postInitRoute($ServerJVM,$ServerPrincipal,$Envs,$DstIP,$Path,$Target,$Len).
  }.

defcall postPathToken(?ServerJVM,?ServerPrincipal,?Envs,?Token,?DstIP,?Src,?Len):-
  {
    postPathToken($ServerJVM,$ServerPrincipal,$Envs,$Token,$DstIP,$Src,$Len).
  }.

advertisePath(?SpeakerJVM,?Speaker,?TargetJVM,?Target,?AdvertiseEnvs,?UpdateEnvs,?DstIP,?Path,?Src,?Len,?Len1):-
  ?Token := postAdvertise(?SpeakerJVM,?Speaker,?AdvertiseEnvs,?DstIP,?Path,?Target,?Src,?Len),
  postPathToken(?TargetJVM,?Target,?UpdateEnvs,?Token,?DstIP,?Speaker,?Len1).

initPath(?SpeakerJVM,?Speaker,?TargetJVM,?Target,?AdvertiseEnvs,?UpdateEnvs,?DstIP,?Path,?Len,?Len1):-
  ?Token := postInitRoute(?SpeakerJVM,?Speaker,?AdvertiseEnvs,?DstIP,?Path,?Target,?Len),
  postPathToken(?TargetJVM,?Target,?UpdateEnvs,?Token,?DstIP,?Speaker,?Len1).


defcall postIPAllocate(?ServerJVM,?ServerPrincipal,?Envs,?Prcpl,?Prefix,?DlgPrefix):-
  {
    postIPAllocate($ServerJVM,$ServerPrincipal,$Envs,$Prcpl,$Prefix,$DlgPrefix).
  }.

defcall postDlgToken(?ServerJVM,?ServerPrincipal,?Envs,?Token,?Prefix):-
  {
    postDlgToken($ServerJVM,$ServerPrincipal,$Envs,$Token,$Prefix).
  }.

allocatePrefix(?RpkiRootJVM,?RpkiRoot,?IRJVM,?IR,?AllocateEnvs,?UpdateEnvs,?Prefix,?DlgPrefix):-
  ?Token := postIPAllocate(?RpkiRootJVM,?RpkiRoot,?AllocateEnvs,?IR,?Prefix,?DlgPrefix),
  postDlgToken(?IRJVM,?IR,?UpdateEnvs,?Token,?Prefix).

defcall postMakeIPTokenSet(?ServerJVM,?ServerPrincipal,?Envs,?DlgPrefix):-
  {
    postMakeIPTokenSet($ServerJVM,$ServerPrincipal,$Envs,$DlgPrefix).
  }.

defcall verifyRoute(?ServerJVM,?ServerPrincipal,?Envs,?DstIP,?Path,?Src,?Len):-
  {
    verifyRoute($ServerJVM,$ServerPrincipal,$Envs,$DstIP,$Path,$Src,$Len).
  }.

defcall verifyOwnPrefix(?ServerJVM,?ServerPrincipal,?Envs,?AS,?Prefix):-
  {
    verifyOwnPrefix($ServerJVM,$ServerPrincipal,$Envs,$AS,$Prefix).
  }.

