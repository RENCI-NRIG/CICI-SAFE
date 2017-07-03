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

defcon instanceHierarchySet() :- 
  spec('Instance level hierarchy'),
  {
    nextLevelBelow("CLOUD_CONTAINER", "BOTTOM")
    nextLevelAbove("CLOUD_CONTAINER", "VM").
    nextLevelBelow("VM", "CLOUD_CONTAINER").
    nextLevelAbove("VM", "DOCKER_CONTAINER").
    nextLevelBelow("DOCKER_CONTAINER", "VM").
    nextLevelAbove("DOCKER_CONTAINER", "SPARK_APP").
    nextLevelBelow("SPARK_APP", "DOCKER_CONTAINER").
    nextLevelAbove("SPARK_APP", "CEILING").
    label("tapcon-hierarchy").
  }.

definit instanceHierarchySet().

// A VM image is identified by a hash that runs over 
// ?Git, ?Revision, ?Checksum, ?Timestamp 
//
//  [vmImgWhiteList(?Git, ?Revision, ?Checksum, ?Timestamp)].

// Now Docker container images and vm images share the same ID space
// we might need a separate space for docker container images


defcon dataSharingPolicySet() :-
  spec('Local data-sharing policies'), 
  {

    // This is not needed for now
    //approveAccessFromInstance(?InstanceID, ?InstanceLevel, ?ObjectID) :- 
    //  ?InstanceLevel = "BOTTOM".  // hitting the bottom; done

    approveAccessFromInstance(?InstanceID, ?InstanceLevel, ?GuestNetAddr, ?ObjectID) :- 
      ?InstanceLevel = "BOTTOM".  // hitting the bottom; done

    // ObjectID is self-certifying ID
    approveAccessFromInstance(?InstanceID, ?InstanceLevel, ?ObjectID) :-   
      ?Host: runGuestInstance(?InstanceID, ?ProgramHash, ?InstanceLevel, ?StartTime),
      ?Host: nameGuestInstance(?InstanceID, ?Scid),
      ?Host: bindNetworkAddr(?Scid, ?NetAddr), 
      ?Owner := rootPrincipal(?ObjectID),
      ownerAcceptsImageBuildingChain(?Owner, ?ProgramHash), 
      ownerAcceptsPorgramProperty(?Owner, ?ProgramHash, ?Property), 
      ?Owner: accessPrivilegeByProgramProperty(?Property, ?ObjectID),
      nextLevelBelow(?InstanceLevel, ?HostLevel),
      approveAccessFromInstance(?Host, ?HostLevel, ?NetAddr, ?ObjectID).

    approveAccessFromInstance(?InstanceID, ?InstanceLevel, ?GuestNetAddr, ?ObjectID) :-   
      ?Host: runGuestInstance(?InstanceID, ?ProgramHash, ?InstanceLevel, ?StartTime),
      ?Host: nameGuestInstance(?InstanceID, ?Scid),
      ?Host: bindNetworkAddr(?Scid, ?NetAddr), 
      ?GuestNetAddr <: ?NetAddr,
      ?Owner := rootPrincipal(?ObjectID),
      ownerAcceptsImageBuildingChain(?Owner, ?ProgramHash), 
      ownerAcceptsAttester(?Owner, ?ProgramHash),
      ownerAcceptsPorgramProperty(?Owner, ?ProgramHash, ?Property), 
      ?Owner: accessPrivilegeByProgramProperty(?Property, ?ObjectID),
      nextLevelBelow(?InstanceLevel, ?HostLevel),
      approveAccessFromInstance(?Host, ?HostLevel, ?NetAddr, ?ObjectID).

    ownerAcceptsAttester(?Owner, ?ProgramHash) :-
      ownerAcceptsProgramProperty(?Owner, ?ProgramHash, "attester").

    ownerAcceptsProgramProperty(?Owner, ?ProgramHash, ?Property) :-
      ?Certifier: hasProgramProperty(?ProgramHash, ?Property),
      ?Owner: trustCertifierOn(?Certifier, ?Property).

    // IaaS attests to image building chains; Owner simply accepts what IaaS says
    ownerAcceptsImageBuildingChain(?Owner, ?ImageHash) :- 
      acceptImageBuildingChain(?ImageHash).

    acceptImageBuildingChain(?ImageID) :-
      $IaaS: tapconImage(?ImageID).

    acceptImageBuildingChain(?ImageID) :-
      $IaaS: buildVMImg(?BuilderImageID, ?ImageID),
      acceptImageBuildingChain(?BuilderImageID).

    label("tapcon-policy").
  }.

definit dataSharingPolicySet().


defcon makeTapconVMImage(?ImageHash) :-
  spec("IaaS announces a Tapcon VM image"),
  {
    tapconImage($ImageHash).
    label("tapcon-images/$ImageHash").
  }. 

defpost postTapconVMImage(?ImageHash) :- 
  [makeTapconVMImage(?ImageHash)].


// IaaS launches a cloud container
//
// A uniform guest attestation statement:
// runGuestInstance(GuestInstanceID, ?GuestImageHash, ?GuestLevel, ?StartTime)
//
defcon launchCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs, ?NetAddr, ?ScidUnderHost, ?StartTime) :-
  spec('Launch a cloud container'),
  {
    runGuestInstance($CCID, $PolicyHash, "CLOUD_CONTAINER", $StartTime).
    nameGuestInstance($CCID, $ScidUnderHost).
    bindNetworkAddr($ScidUnderHost, $NetAddr).
    guestConf($CCID, $PolicyHash, $TemplateID, $TemplateArgs).
    label("cloud-container/$CCID"). 
  }.

defpost postCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs, ?NetAddr, ?Scid, ?StartTime) :-
  [launchCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs, ?NetAddr, ?Scid, ?StartTime)].

// Cloud container launches a vm 
defcon launchVM(?InstanceID, ?ImgID, ?ConfigHash, ?NetAddr, ?ScidUnderHost, ?StartTime) :-
  ?ImgSet := label($IaaS, "vm-image/?ImgID"), 
  ?HostCloudContainerSet := label($Self, "subject($Self)"), 
  {
    link($ImgSet).
    link($HostCloudContainerSet).
    runGuestInstance($InstanceID, $ImgID, "VM", $StartTime). 
    nameGuestInstance($InstanceID, $ScidUnderHost).
    bindNetworkAddr($ScidUnderHost, $NetAddr).
    guestConf($InstanceID, $ConfigHash).
    label("vm-instance/$InstanceID"). // update this link to the vm's subject set
  }.

defpost postVM(?InstanceID, ?ImgID, ?ConfigHash, ?NetAddr, ?Scid, ?StartTime) :-
  [launchVM(?InstanceID, ?ImgID, ?ConfigHash, ?NetAddr, ?Scid, ?StartTime)].

// IaaS builds vm images
// ImageID is a hash over source and config
defcon buildVMImg(?BuilderImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp) :-
  ?BuilderImgSet := label($IaaS, "vm-image/?BuilderImgID"),
  {
    link($BuilderImgSet).
    buildVMImg($BuilderImgID, $ImgID). 
    vmImgConfig($ImgID, $GitHash, $RevHash, $Checksum, $Timestamp).
    label("vm-image/$ImgID").
  }.

defpost postVMImg(?BuilderImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp) :-
  [buildVMImg(?BuilderImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp)].


// VM launches a Docker container
defcon launchContainer(?ContainerID, ?VMImgID, ?ConfigHash, ?NetAddr, ?ScidUnderHost, ?StartTime) :- 
  ?VMImgSet := label($IaaS, "vm-image/?VMImgID"),
  ?HostVMSet := label($Self, "subject($Self)"),
  {
    link($VMImgSet).
    link($HostVMSet).
    runGuestInstance($ContainerID, $VMImgID, "DOCKER_CONTAINER", $StartTime). 
    nameGuestInstance($ContainerID, $ScidUnderHost).
    bindNetworkAddr($ScidUnderHost, $NetAddr).
    guestConf($ContainerID, $ConfigHash).
    label("container/$ContainerID").   // update this link to the container's subject set
  }.

defpost postContainer(?ContainerID, ?VMImgID, ?ConfigHash, ?NetAddr, ?ScidUnderHost, ?StartTime) :-
  [launchContainer(?ContainerID, ?VMImgID, ?ConfigHash, ?NetAddr, ?ScidUnderHost, ?StartTime)].

// Docker container launches a Spark app
defcon launchSparkApp(?AppID, ?AppHash, ?ConfigHash, ?NetAddr, ?ScidUnderHost, ?StartTime) :-
  ?DockerContainerSet := label($Self, "subject($Self)"),
  {
    link($DockerContainerSet).
    runGuestInstance($AppID, $AppHash, "SPARK_APP", $StartTime).
    nameGuestInstance($AppID, $ScidUnderHost).
    bindNetworkAddr($ScidUnderHost, $NetAddr).
    guestConf($AppID, $ConfigHash).
    label("spark-app/$AppID").         // update this link to the app's subject set
  }.

defpost postSparkApp(?AppID, ?AppHash, ?ConfigHash, ?StartTime) :-
  [launchSparkApp(?AppID, ?AppHash, ?ConfigHash, ?StartTime)].

defguard sparkAppAccessesObject(?AppID, ?ObjectID) :-
  ?DataOwner := rootPrincipal(?ObjectID),
  ?OwnerTrustWallet := label(?DataOwner, "trust wallet"),
  ?ACLSet := label(?DataOwner, "acl/?ObjectID"),
  ?AttestationSet := label(?AppID, "subject(?AppID)"),
  ?PolicySet := label("tapcon-policy"),
  ?HierarchySet := label("tapcon-hierarchy"),
  {
    link($OwnerTrustWallet).
    link($ACLSet).
    link($AttestationSet).
    link($PolicySet).
    approveAccessFromInstance($AppID, "SPARK_APP", $ObjectID)?
  }.
