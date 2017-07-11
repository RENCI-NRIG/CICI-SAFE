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

// We do third-party certification, instead of white list

defcon dataSharingPolicySet() :-
  spec('Local data-sharing policies'), 
  {
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

    // Empty checks on cloud container conf for now
    approveAccessFromCloudContainer(?CloudContainerID, ObjectID) :- 
      $IaaS: cloudContainerConf(?CloudContainerID, ?CCConfPolicyHash, ?CCConfTemplate, ?CCConfTempArgs),

    ownerAcceptsSparkAppProperty(?Owner, ?SparkAppHash, ?Property) :-
      ownerAcceptsProgramProperty(?Owner, ?SparkAppHash, ?Property).
  
    ownerAcceptsContainerImageProperty(?Owner, ?ContainerImgHash, ?Property) :-
      ownerAcceptsProgramProperty(?Owner, ?ContainerImgHash, ?Property),
      ownerAcceptsImageBuildingChain(?Owner, ?ContainerImgHash). 

    ownerAcceptsVMImageProperty(?Owner, ?VMImgHash, ?Property) :-
      ownerAcceptsProgramProperty(?Owner, ?VMImgHash, ?Property), 
      ownerAcceptsImageBuildingChain(?Owner, ?VMImgHash). 

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
defcon launchCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs) :-
  spec('Launch a cloud container'),
  {
    cloudContainerConf($CCID, $PolicyHash, $TemplateID, $TemplateArgs).
    label("cloud-container/$CCID"). 
  }.

defpost postCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs) :-
  [launchCloudContainer(?CCID, ?PolicyHash, ?TemplateID, ?TemplateArgs)].

// IaaS launches a vm 
defcon launchVM(?InstanceID, ?ImgHash, ?CCID, ?StartTime) :-
  ?ImgSet := label($IaaS, "vm-image/?ImgHash"), 
  ?CCSet := label($IaaS, "cloud-container/?CCID"), 
  {
    link($ImgSet).
    link($CCSet).
    runVMInstance($InstanceID, $ImgID, $CCID, $StartTime).
    label("vm-instance/$InstanceID"). // update this link to the vm's subject set
  }.

defpost postVM(?InstanceID, ?ImgID, ?CCID, ?StartTime) :-
  [launchVM(?InstanceID, ?ImgID, ?CCID, ?StartTime)].

// IaaS builds vm images
defcon buildVMImg(?BuilderImgHash, ?ImgHash, ?GitHash, ?RevHash, ?Checksum, ?Timestamp, ?CCID) :-
  ?BuilderImgSet := label($IaaS, "vm-image/?BuilderImgHash"),
  {
    link($BuilderImgSet).
    buildVMImg($BuilderImgHash, $ImgHash). 
    vmImgConfig($ImgHash, $GitHash, $RevHash, $Checksum, $Timestamp).
    label("vm-image/$ImgHash").
  }.

defpost postVMImg(?BuilderImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp, ?CCID) :-
  [buildVMImg(?BuilderImgID, ?ImgID, ?GitHash, ?RevHash, ?Checksum, ?Timestamp, ?CCID)].


// Issuer: VM instance
defcon launchContainer(?ContainerID, ?VMImgHash, ?StartTime) :- 
  ?VMImgSet := label($IaaS, "vm-image/?VMImgHash"),
  ?HostVMSet := label($Self, "subject($Self)"),
  {
    link($VMImgSet).
    link($HostVMSet).
    runContainer($ContainerID, $VMImgID, $StartTime). 
    label("container/$ContainerID").   // update this link to the container's subject set
  }.

defpost postContainer(?ContainerID, ?VMImgID, ?StartTime) :-
  [launchContainer(?ContainerID, ?VMImgID, ?StartTime)].

// Issuer: Docker container
defcon launchSparkApp(?AppID, ?AppHash, ?StartTime) :-
  ?DockerContainerSet := label($Self, "subject($Self)"),
  {
    link($DockerContainerSet).
    runSparkApp($AppID, $AppHash, $StartTime).
    label("spark-app/$AppID").         // update this link to the app's subject set
  }.

defpost postSparkApp(?AppID, ?AppHash, ?StartTime) :-
  [launchSparkApp(?AppID, ?AppHash, ?StartTime)].

defguard sparkAppAccessesObject(?AppID, ?ObjectID) :-
  ?DataOwner := rootPrincipal(?ObjectID),
  ?OwnerTrustWallet := label(?DataOwner, "trust wallet"),
  ?ACLSet := label(?DataOwner, "acl/?ObjectID"),
  ?AttestationSet := label(?AppID, "subject(?AppID)"),
  ?PolicySet := label("tapcon-policy"),
  {
    link($OwnerTrustWallet).
    link($ACLSet).
    link($AttestationSet).
    link($PolicySet).
    approveAccessFromSparkApp($AppID, $ObjectID)?
  }.
