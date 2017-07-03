defenv VMLevel :- "1".
defenv IaaS() :- 'hBZy2DLtG7qF7CxdZNWusPKdDz6LbA3lBDFIBDGbHTo'.

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defcon makeMySubjectSet() :-
  spec("Construct the subject set for self"),
  {
    link($Self).
    label("subject($Self)").
  }.

defpost postSubjectSet() :- [makeMySubjectSet()].

// A VM image is identified by a hash that runs over 
// ?Git, ?Revision, ?Checksum, ?Timestamp 
//
//defcon vmImgWhiteList(?Git, ?Revision, ?Checksum, ?Timestamp) :- 
//  {
//    acceptVMImage($Git, $Revision, $Checksum, $Timestamp). 
//    label("vm-image-whitelist").
//  }.
//
//defpost postVMImgWhiteList(?Git, ?Revision, ?Checksum, ?Timestamp) :- 
//  [vmImgWhiteList(?Git, ?Revision, ?Checksum, ?Timestamp)].

// Now Docker container images and vm images share the same ID space
// we might need a separate white list for container images
// defcon containerImgWhiteList()

//defcon sparkAppWhiteList(?AppHash) :-
//  {
//    trustedSparkApp($AppHash).
//    label("spark-app-whitelist").
//  }.
//
//defpost postSparkAppWhiteList(?AppHash) :- 
//  [sparkAppWhiteList(?AppHash)].

// We'll do third-party certification, instead of white list

defcon makeTapconVMImage(?ImageHash) :-
  spec("IaaS announces a Tapcon VM image"),
  {
    tapconImage($ImageHash).
    label("tapcon-image").
  }. 

defpost postTapconVMImage(?ImageID) :- 
  [makeTapconVMImage(?ImageID)].

defcon dataSharingPolicySet() :-
  spec('Local policies on data sharing'), 
  {
    approveAccess(?SparkAppID, ?ObjectID) :- 
      ?Owner := rootPrincipal(?ObjectID),
      approveAccessFromSparkApp(?Owner, ?SparkAppID). 

    // ObjectID is self-certifying ID
    approveAccessFromSparkApp(?AppID, ?ObjectID) :- 
      ?HostContainer: runSparkApp(?AppID, ?AppHash, ?StartTime),
      ?Owner := rootPrincipal(?ObjectID),
      ownerAcceptsSparkAppProperty(?Owner, ?AppHash, ?Property), 
      ?Owner: accessPrivilegeBySparkAppProperty(?Property, ?ObjectID),
      approveAccessFromDockerContainer(?HostContainer, ?ObjectID).

    approveAccessFromDockerContainer(?ContainerID, ?ObjectID) :-
      ?HostVM: runContainer(?ContainerID, ?ContainerImgHash, ?StartTime),
      ?Owner := rootPrincipal(?ObjectID),
      ownerAcceptsContainerImageProperty(?Owner, ?ContainerImgHash, ?Property), 
      ?Owner: accessPrivilegeByContainerImageProperty(?Property, ?ObjectID), 
      approveAccessFromVM(?HostVM, ?ObjectID).

    approveAccessFromVM(?VMInstanceID, ObjectID) :-
      $IaaS: runVMInstance(?VMInstanceID, ?VMImgHash, ?CCID, ?StartTime),
      ?Owner := rootPrincipal(?ObjectID),
      ownerAcceptsVMImageProperty(?Owner, ?VMImgHash, ?Property), 
      ?Owner: accessPrivilegeByVMImageProperty(?Property, ?ObjectID),
      approveAccessFromCloudContainer(?CCID, ?ObjectID).

    ownerAcceptsSparkAppProperty(?Owner, ?SparkAppHash, ?Property) :-
      ownerAcceptsProgramProperty(?Owner, ?SparkAppHash, ?Property).
  
    ownerAcceptsContainerImageProperty(?Owner, ?ContainerImgHash, ?Property) :-
      ownerAcceptsProgramProperty(?Owner, ?ContainerImgHash, ?Property).

    ownerAcceptsVMImageProperty(?Owner, ?VMImgHash, ?Property) :-
      ownerAcceptsProgramProperty(?Owner, ?VMImgHash, ?Property). 

    ownerAcceptsProgramProperty(?Owner, ?ProgramHash, ?Property) :-
      ?Certifier: hasProgramProperty(?ProgramHash, ?Property),
      ?Owner: trustCertifier(?Certifier).

    // Empty checks on cloud container conf for now
    approveAccessFromCloudContainer(?CloudContainerID, ObjectID) :- 
      $IaaS: cloudContainerConf(?CCID, ?CCConfPolicyHash, ?CCConfTemplate, ?CCConfTempArgs),




      ownerAcceptsVMImage(?ObjectOwner, ?VMImgHash),

    ownerAcceptsContainerImage(?ObjectOwner, ?ContainerImgHash) :-
      ownerAcceptsContainerImage(?ObjectOwner, ?ContainerImgHash),
      acceptImageBuildingChain(?ContainerImgHash).

    acceptVMImage(?VMImgHash) :-
      trustVMImage(?VMImgHash),
      trustImageBuildingChain(?VMImgHash).

    acceptContainerImage(?ContainerImgHash) :- acceptVMImage(?ContainerImgHash).


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

defcon launchSparkApp(?AppID, ?AppHash, ?StartTime, ?ContainerID) :-
  ?DockerContainerSet := label(?ContainerID, "container")
  {
    initSparkApp($AppID, $AppHash, $StartTime, $ContainerID).
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
