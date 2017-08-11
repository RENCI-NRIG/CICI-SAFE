
defcall postIdSet(?ServerJVM,?ServerPrincipal,?Envs,?CN):-
  {
    postIdSet($ServerJVM,$ServerPrincipal,$Envs,$CN).
  }.

defcall postSubjectSet(?ServerJVM,?ServerPrincipal,?Envs):-
  {
    postSubjectSet($ServerJVM,$ServerPrincipal,$Envs).
  }.

defcall postSubjectSet(?ServerJVM,?ServerPrincipal,?Envs):-
  {
    postSubjectSet($ServerJVM,$ServerPrincipal,$Envs).
  }.

defcall updateSubjectSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?Token) :-
  {
    updateSubjectSet($ServerJVM, $ServerPrincipal, $Envs, $Token).
  }.

defcall postStitchPolicy(?ServerJVM,?ServerPrincipal,?Envs):-
  {
    postStitchPolicy($ServerJVM,$ServerPrincipal,$Envs).
  }.

defcall postEndorsePM(?ServerJVM,?ServerPrincipal,?Envs,?Member):-
  {
    postEndorsePM($ServerJVM,$ServerPrincipal,$Envs,$Member).
  }.

endorsePM(?SpeakerJVM,?Speaker,?TargetJVM,?Target,?AdvertiseEnvs,?UpdateEnvs):-
  ?Token := postEndorsePM(?SpeakerJVM,?Speaker,?AdvertiseEnvs,?Target),
  updateSubjectSet(?TargetJVM,?Target,?UpdateEnvs,?Token).

defcall postMyPA(?ServerJVM,?ServerPrincipal,?Envs,?PA):-
  {
    postMyPA($ServerJVM,$ServerPrincipal,$Envs,$PA).
  }.


endorsePA(?SpeakerJVM,?Speaker,?TargetJVM,?Target,?EndorseEnvs):-
  postEndorsePM(?SpeakerJVM,?Speaker,?EndorseEnvs,?Target).
