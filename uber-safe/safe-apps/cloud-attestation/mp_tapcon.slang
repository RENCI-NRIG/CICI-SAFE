defcall postIdSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?CN) :-
  {
    postIdSet($ServerJVM, $ServerPrincipal, $Envs, $CN).
  }.

defcall postVMImgWhiteList(?ServerJVM, ?ServerPrincipal, ?Envs, ?Git, ?Revision, ?Checksum, ?Timestamp) :-
  {
    postVMImgWhiteList($ServerJVM, $ServerPrincipal, $Envs, $Git, $Revision, $Checksum, $Timestamp).
  }.

defcall postSparkAppWhiteList(?ServerJVM, ?ServerPrincipal, ?Envs, ?AppHash) :-
  {
    postSparkAppWhiteList($ServerJVM, $ServerPrincipal, $Envs, $AppHash).
  }.

defcall postTrustedVMImage(?ServerJVM, ?ServerPrincipal, ?Envs, ?ImageID) :-
  {
    postTrustedVMImage($ServerJVM, $ServerPrincipal, $Envs, $ImageID).
  }.

defcall postCloudContainer(?ServerJVM, ?ServerPrincipal, ?Envs, ?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs) :-
  {
    postCloudContainer($ServerJVM, $ServerPrincipal, $Envs, $CCID, $PolicyHash, $TemplateID, $TemplateArgs).
  }.

defcall postVM(?ServerJVM, ?ServerPrincipal, ?Envs, ?InstanceID, ?ImgID, ?CCID) :-
  {
    postVM($ServerJVM, $ServerPrincipal, $Envs, $InstanceID, $ImgID, $CCID).
  }.

defcall postVMImg(?ServerJVM, ?ServerPrincipal, ?Envs, ?MakerImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp, ?CCID) :-
  {
    postVMImg($ServerJVM, $ServerPrincipal, $Envs, $MakerImgID, $ImgID, $GitHash, $RevHash, $Checksum, $Timestamp, $CCID).
  }.

defcall postContainer(?ServerJVM, ?ServerPrincipal, ?Envs, ?ContainerID, ?VMImgID, ?HostVMID) :-
  {
    postContainer($ServerJVM, $ServerPrincipal, $Envs, $ContainerID, $VMImgID, $HostVMID).
  }.

defcall postSparkApp(?ServerJVM, ?ServerPrincipal, ?Envs, ?AppID, ?OwnerID, ?AppHash, ?StartTime, ?ContainerID) :-
  {
    postSparkApp($ServerJVM, $ServerPrincipal, $Envs, $AppID, $OwnerID, $AppHash, $StartTime, $ContainerID).
  }.

defcall checkSparkApp(?ServerJVM, ?ServerPrincipal, ?Envs, ?AppID) :-
  {
    postSparkApp($ServerJVM, $ServerPrincipal, $Envs, $AppID).
  }.
