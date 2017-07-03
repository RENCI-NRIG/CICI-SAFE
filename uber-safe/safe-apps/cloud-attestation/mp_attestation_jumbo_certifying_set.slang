defcall postIdSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?CN) :-
  {
    postIdSet($ServerJVM, $ServerPrincipal, $Envs, $CN).
  }.

defcall postInstanceSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?IP, ?ImgId, ?InstanceId) :-
  {
    postInstanceSet($ServerJVM, $ServerPrincipal, $Envs, $IP, $ImgId, $InstanceId).
  }.

defcall updateImageSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?Token, ?ImgId) :-
  {
    updateImageSet($ServerJVM, $ServerPrincipal, $Envs, $Token, $ImgId).
  }.

defcall postImageProperty(?ServerJVM, ?ServerPrincipal, ?Envs, ?ImgId, ?Property) :-
  {
    postImageProperty($ServerJVM, $ServerPrincipal, $Envs, $ImgId, $Property).
  }.

certifyImg(?CertifierJVM, ?CertifierId, ?ImgOwnerJVM, ?ImgOwnerId, ?CertifyingEnvs, ?UpdateEnvs, ?ImgId, ?Property) :-
  ?Token := postImageProperty(?CertifierJVM, ?CertifierId, ?CertifyingEnvs, ?ImgId, ?Property), 
  updateImageSet(?ImgOwnerJVM, ?ImgOwnerId, ?UpdateEnvs, ?Token, ?ImgId).

defcall postObjectAcl(?ServerJVM, ?ServerPrincipal, ?Envs, ?ObjectId, ?Property) :-
  {
    postObjectAcl($ServerJVM, $ServerPrincipal, $Envs, $ObjectId, $Property).
  }.

defcall postTrustedCertifier(?ServerJVM, ?ServerPrincipal, ?Envs, ?TrustedCertifierId) :-
  {
    postTrustedCertifier($ServerJVM, $ServerPrincipal, $Envs, $TrustedCertifierId).
  }.

defcall postTrustedCP(?ServerJVM, ?ServerPrincipal, ?Envs, ?TrustedCP) :-
  {
    postTrustedCP($ServerJVM, $ServerPrincipal, $Envs, $TrustedCP).
  }.

defcall accessObject(?ServerJVM, ?ServerPrincipal, ?Envs, ?IP, ?ObjectId) :-
  {
    accessObject($ServerJVM, $ServerPrincipal, $Envs, $IP, $ObjectId).
  }.

defcall imgInstanceAccessesObject(?ServerJVM, ?ServerPrincipal, ?Envs, ?ImgId, ?ObjectId) :-
  {
    imgInstanceAccessesObject($ServerJVM, $ServerPrincipal, $Envs, $ImgId, $ObjectId).
  }.
