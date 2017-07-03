defcall updateSubjectSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?Token) :-
  {
    updateSubjectSet($ServerJVM, $ServerPrincipal, $Envs, $Token).
  }.

defcall postAttesterImage(?ServerJVM, ?ServerPrincipal, ?Envs, ?Image) :-
  {
    postAttesterImage($ServerJVM, $ServerPrincipal, $Envs, $Image).
  }.

defcall postImageProperty(?ServerJVM, ?ServerPrincipal, ?Envs, ?Image, ?Property) :-
  {
    postImageProperty($ServerJVM, $ServerPrincipal, $Envs, $Image, $Property).
  }.

defcall postObjectAcl(?ServerJVM, ?ServerPrincipal, ?Envs, ?ObjectId, ?Property) :-
  {
    postObjectAcl($ServerJVM, $ServerPrincipal, $Envs, $ObjectId, $Property).
  }.

defcall postInstanceSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?InstanceId, ?Image, ?ProgramType, ?GuestAuthNID) :-
  {
    postInstanceSet($ServerJVM, $ServerPrincipal, $Envs, $InstanceId, $Image, $ProgramType, $GuestAuthNID).
  }.

launchInstance(?HostJVM, ?HostId, ?GuestJVM, ?GuestAuthNID, ?AttestingEnvs, ?UpdateEnvs, ?InstanceId, ?Image, ?ProgramType) :-
  ?Token := postInstanceSet(?HostJVM, ?HostId, ?AttestingEnvs, ?InstanceId, ?Image, ?ProgramType, ?GuestAuthNID), 
  updateSubjectSet(?GuestJVM, ?GuestAuthNID, ?UpdateEnvs, ?Token).

defcall appAccessesObject(?ServerJVM, ?ServerPrincipal, ?Envs, ?AppID, ?ObjectID) :-
  {
    appAccessesObject($ServerJVM, $ServerPrincipal, $Envs, $AppID, $ObjectID).
  }.

defcall attestInstance(?ServerJVM, ?ServerPrincipal, ?Envs, ?AppID) :-
  {
    attestInstance($ServerJVM, $ServerPrincipal, $Envs, $AppID).
  }.

defcall attestAppProperty(?ServerJVM, ?ServerPrincipal, ?Envs, ?AppID, ?Property) :-
  {
    attestAppProperty($ServerJVM, $ServerPrincipal, $Envs, $AppID, $Property).
  }.
