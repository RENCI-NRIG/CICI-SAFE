defenv VMLevel :- "1".
defenv IaaS() :- 'hBZy2DLtG7qF7CxdZNWusPKdDz6LbA3lBDFIBDGbHTo'.

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defcon vmImgWhiteList(?Git, ?Revision, ?Checksum, ?Timestamp) :- 
  {
    acceptVMImage($Git, $Revision, $Checksum, $Timestamp). 
    label("vm-image-whitelist").
  }.

defpost postVMImgWhiteList(?Git, ?Revision, ?Checksum, ?Timestamp) :- 
  [vmImgWhiteList(?Git, ?Revision, ?Checksum, ?Timestamp)].

// we might need a container image white list
// defcon containerImgWhiteList()

defcon sparkAppWhiteList(?AppHash) :-
  {
    trustedSparkApp($AppHash).
    label("spark-app-whitelist").
  }.

defpost postSparkAppWhiteList(?AppHash) :- 
  [sparkAppWhiteList(?AppHash)].

defcon makeTrustedVMImage(?ImageID) :-
  spec("IaaS announces a trusted VM image"),
  {
    trustedImage($ImageID).
    label("trusted-vm-image").
  }. 

defpost postTrustedVMImage(?ImageID) :- 
  [makeTrustedVMImage(?ImageID)].

defcon VMPolicySet() :-
  spec('Local policies on VMs and images'), 
  {

    acceptSparkApp(?AppID) :- 
      ?HostingContainer := rootPrincipal(?AppID),
      ?HostingContainer: sparkApp(?AppID, ?OwnerID, ?AppHash, ?StartTime, ?ContainerID),
      trustedSparkApp(?AppHash),
      ?HostVM := rootPrincipal(?ContainerID),
      ?HostVM: initContainer(?ContainerID, ?VMImgID, ?HostVMID),
      acceptImageProducingChain(?VMImgID),
      acceptVM(?HostVMID).       
            
    acceptVM(?VMInstanceID) :-
      $IaaS: initVMInstance(?VMInstanceID, ?VMImgID, ?CCID),
      $IaaS: cloudContainerConf(?CloudContainerConfID, ?CCConfPolicyHash, ?CCConfTemplate, ?CCConfTempArgs),
      acceptImageProducingChain(?VMImgID),
      acceptImageByProperty(?VMImgID).

    acceptImageProducingChain(?ImageID) :-
      $IaaS: trustedImage(?ImageID).

    acceptImageProducingChain(?ImageID) :-
      $IaaS: buildVMImg(?ProducerImageID, ?ImageID, ?CloudContainerConfID),
      acceptImageProducingChain(?ProducerImageID).

    acceptImageByProperty(?ImageID) :-
      ?Speaker: vmImgConfig(?ImageID, ?Git, ?Revision, ?Checksum, ?Timestamp),  // ?Speaker should be image builder
      acceptVMImage(?Git, ?Revision, ?Checksum, ?Timestamp).

    label("tapcon-policy").
  }.

definit VMPolicySet().

defcon launchCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs) :-
  spec('Launch a cloud container'),
  // CCID is scid
  {
    cloudContainerConf($CCID, $PolicyHash, $TemplateID, $TemplateArgs).
    label("cloud-container/$CCID"). 
  }.

defpost postCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs) :-
  [launchCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs)].

defcon launchVM(?InstanceID, ?ImgID, ?CCID) :-
  ?ImgOwner := rootPrincipal(?ImgID),
  ?ImgSet := label(?ImgOwner, "vm-image/?ImgID"), 
  ?CCOwner := rootPrincipal(?CCID),
  ?CCSet := label(?CCOwner, "cloud-container/?CCID"), 
  {
    link($ImgSet).
    link($CCSet).
    initVMInstance($InstanceID, $ImgID, $CCID).
    label("vm-instance/$InstanceID").
  }.

defpost postVM(?InstanceID, ?ImgID, ?CCID) :-
  [launchVM(?InstanceID, ?ImgID, ?CCID)].

defcon makeVMImg(?MakerImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp, ?CCID) :-
  ?MakerImgOwner := rootPrincipal(?MakerImgID),
  ?MakerImgSet := label(?MakerImgOwner, "vm-image/?MakerImgOwner"),
  {
    link($MakerImgSet).
    buildVMImg($MakerImgID, $ImgID, $CCID). 
    vmImgConfig($ImgID, $GitHash, $RevHash, $Checksum, $Timestamp).
    label("vm-image/$ImgID").
  }.

defpost postVMImg(?MakerImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp, ?CCID) :-
  [makeVMImg(?MakerImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp, ?CCID)].

defcon launchContainer(?ContainerID, ?VMImgID, ?HostVMID) :- 
  ?VMImgOwner := rootPrincipal(?VMImgID),
  ?VMImgSet := label(?VMImgOwner, "vm-image/?VMImgID"),
  ?HostVMOwner := rootPrincipal(?HostVMID),
  ?HostVMSet := label(?HostVMOwner, "vm-instance/?HostVMID"),
  {
    link($VMImgSet).
    link($HostVMSet).
    initContainer($ContainerID, $VMImgID, $HostVMID). 
    label("container/$ContainerID").
  }.

defpost postContainer(?ContainerID, ?VMImgID, ?HostVMID) :-
  [launchContainer(?ContainerID, ?VMImgID, ?HostVMID)].

defcon launchSparkApp(?AppID, ?OwnerID, ?AppHash, ?StartTime, ?ContainerID) :-
  {
    sparkApp($AppID, $OwnerID, $AppHash, $StartTime, $ContainerID).
    label("spark-app/$AppID").
  }.

defpost postSparkApp(?AppID, ?OwnerID, ?AppHash, ?StartTime, ?ContainerID) :-
  [launchSparkApp(?AppID, ?OwnerID, ?AppHash, ?StartTime, ?ContainerID)].

defguard checkSparkApp(?AppID) :-
  ?VMImgWhiteListSet := label("vm-image-whitelist"),
  ?SparkAppWhiteListSet := label("spark-app-whitelist"),
  ?PolicySet := label("tapcon-policy"),
  {
    link($VMImgWhiteListSet).
    link($SparkAppWhiteListSet).
    link($PolicySet).
    acceptSparkApp($AppID)?
  }.
